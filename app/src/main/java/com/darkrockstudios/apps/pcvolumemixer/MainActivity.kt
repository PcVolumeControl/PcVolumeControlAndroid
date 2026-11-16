package com.darkrockstudios.apps.pcvolumemixer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSessionOptions
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.darkrockstudios.apps.pcvolumemixer.databinding.ActivityMainBinding
import com.darkrockstudios.apps.pcvolumemixer.icons.IconSession
import com.darkrockstudios.apps.pcvolumemixer.icons.IconStore
import com.darkrockstudios.apps.pcvolumemixer.protocol.IconProtocol
import com.darkrockstudios.apps.pcvolumemixer.protocol.OlderSide
import com.darkrockstudios.apps.pcvolumemixer.protocol.ServerLine
import com.darkrockstudios.apps.pcvolumemixer.protocol.ServerLineClassifier
import com.darkrockstudios.apps.pcvolumemixer.protocol.SessionDetails
import com.darkrockstudios.apps.pcvolumemixer.protocol.Updates
import com.darkrockstudios.apps.pcvolumemixer.protocol.Versions
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import hu.akarnokd.rxjava2.operators.FlowableTransformers
import io.reactivex.disposables.Disposable
import io.reactivex.processors.PublishProcessor
import java.util.concurrent.atomic.AtomicBoolean


open class MainActivity : AppCompatActivity(), TcpClient.ServerListener,
    AudioSessionViewHolder.VolumeChangeListener, AdapterView.OnItemSelectedListener {
    companion object {
        val TAG = MainActivity::class.java.simpleName
        val KEY_SERVER_IP = "server_ip"
        val KEY_PORT = "server_port"
    }

    private lateinit var binding: ActivityMainBinding
    private val m_gson = Gson()
    private val m_lineClassifier = ServerLineClassifier(m_gson)
    // Read on connection threads to ignore callbacks from clients that have been replaced.
    @Volatile
    private var m_client: TcpClient? = null
    @Volatile
    private var m_iconSession: IconSession? = null
    private lateinit var m_iconStore: IconStore
    private var m_iconSizePx = 0
    private var m_sessionViewHolders: List<AudioSessionViewHolder> = emptyList()
    private val m_iconRefreshPosted = AtomicBoolean(false)
    private val m_iconListener: () -> Unit = { onIconsChanged() }
    private var m_pcAudio: PcAudio? = null
    private var m_disconnectMenuItem: MenuItem? = null
    private var m_discovery: ServerDiscovery? = null
    private var m_discoveredServers: List<DiscoveredServer> = emptyList()

    private lateinit var m_deviceAdapter: ArrayAdapter<AudioDeviceItem>

    private val m_serverMessageValve = PublishProcessor.create<Boolean>()
    private val m_serverMessage = PublishProcessor.create<PcAudio>()
    private var m_serverSubscription: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Targeting SDK 35 forces edge-to-edge, so keep content out of the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        binding.versionView.text = getString(R.string.app_version, PROTOCOL_VERSION)

        m_iconStore = IconStore.get(this)
        m_iconStore.addListener(m_iconListener)
        m_iconSizePx = IconProtocol.requestSize(resources.getDimension(R.dimen.session_icon_size))

        setSupportActionBar(binding.appToolbar)

        binding.connectButton.setOnClickListener {
            connectToServer(
                binding.ipAddressInput.text.toString(),
                binding.portInput.text.toString()
            )
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val defaultIp = prefs.getString(KEY_SERVER_IP, null)
        if (defaultIp != null) {
            binding.ipAddressInput.setText(defaultIp)
        }

        val defaultPort = prefs.getString(KEY_PORT, null)
        if (defaultPort != null) {
            binding.portInput.setText(defaultPort)
        }

        m_deviceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item)
        binding.deviceSelector.adapter = m_deviceAdapter

        binding.deviceSelector.onItemSelectedListener = this

        binding.aboutButton.setOnClickListener { goToAbout() }

        m_discovery = ServerDiscovery(this) { servers ->
            m_discoveredServers = servers
            updateDiscoveredServers()
        }

        autoConnect()

        m_serverSubscription = m_serverMessage
            .serialize()
            .onBackpressureLatest()
            .compose(FlowableTransformers.valve(m_serverMessageValve, true))
            .subscribe(this::processMessage)
    }

    private fun updateThumbArea() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val showThumbArea = prefs.getBoolean(getString(R.string.key_thumb_area), true)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            var bottomPadding = 0

            if (showThumbArea) {
                bottomPadding = resources.getDimensionPixelSize(R.dimen.min_touch)
            }

            val scrollView = findViewById<View>(R.id.MIXER_scroll)
            scrollView.setPadding(
                scrollView.paddingStart,
                scrollView.paddingTop,
                scrollView.paddingEnd,
                bottomPadding
            )
        }
    }

    // Because we have scrollview & horizontal scrollview with the same ID on orentation change
    // we can't restore state or things break lol
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        // Don't call super.onRestoreInstanceState()
    }

    private fun goToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun goToAbout() {
        startActivity(Intent(this, AboutActivity::class.java))
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {

    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val newAudioDevice = m_deviceAdapter.getItem(position)
            ?: error("Failed to get items for position: $position")

        Log.d(TAG, "Changing default device to: $newAudioDevice")

        if (m_pcAudio != null) {
            m_client?.sendMessageAsync(m_gson.toJson(Updates.device(newAudioDevice.id)))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main, menu)

        m_disconnectMenuItem = menu?.findItem(R.id.MENU_disconnect)

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        m_disconnectMenuItem?.isVisible = m_client?.isRunning() ?: false

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.MENU_disconnect -> {
            m_client?.stopClient()
            true
        }
        R.id.MENU_pc_app -> {
            sendPcApp()
            true
        }
        R.id.MENU_clear_hidden -> {
            AudioSessionOptions.clearHidden(this)
            populateUi()
            showMessage(R.string.TOAST_hidden_cleared)
            true
        }
        R.id.MENU_settings -> {
            goToSettings()
            true
        }
        R.id.MENU_about -> {
            goToAbout()
            true
        }
        else ->
            super.onOptionsItemSelected(item)
    }

    private fun sendPcApp() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "message/rfc822"
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.PCAPP_EMAIL_title))
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.PCAPP_EMAIL_body))
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.PCAPP_EMAIL_title)))
        } catch (ex: ActivityNotFoundException) {
            showMessageLong(getString(R.string.PCAPP_EMAIL_failed))
        }
    }

    private fun connectToServer(serverIp: String, port: String) {
        m_client?.stopClient()

        val client = TcpClient(this, serverIp, Integer.parseInt(port))
        m_iconSession?.close()
        m_iconSession = IconSession(client, m_iconStore, m_iconSizePx, m_gson)
        m_client = client
        Thread(client::run).start()
    }

    private fun updateDiscoveredServers() {
        binding.discoveredServers.removeAllViews()
        m_discoveredServers.forEach { server ->
            val button = Button(this, null, androidx.appcompat.R.attr.borderlessButtonStyle)
            button.text = getString(R.string.discovered_server_label, server.name, server.host)
            button.isAllCaps = false
            button.setOnClickListener {
                binding.ipAddressInput.setText(server.host)
                binding.portInput.setText(server.port.toString())
                connectToServer(server.host, server.port.toString())
            }
            binding.discoveredServers.addView(button)
        }

        val onConnectScreen = binding.connectButton.visibility == View.VISIBLE
        binding.discoveredServersContainer.visibility =
            if (onConnectScreen && m_discoveredServers.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun autoConnect() {
        val serverIp = binding.ipAddressInput.text.toString()
        val port = binding.portInput.text.toString()
        if (!TextUtils.isEmpty(serverIp) && !TextUtils.isEmpty(port)) {
            Log.d(TAG, "Auto connecting to server")
            connectToServer(serverIp, port)
        }
    }

    override fun onStart() {
        super.onStart()

        updateThumbArea()

        m_discovery?.start()

        // Want to make sure we are always receiving messages when we start
        m_serverMessageValve.onNext(true)

        if (m_client?.isRunning() == false) {
            autoConnect()
        }
    }

    override fun onStop() {
        super.onStop()

        m_discovery?.stop()
        m_client?.stopClient()
    }

    override fun onDestroy() {
        super.onDestroy()

        m_serverSubscription?.dispose()
        m_iconStore.removeListener(m_iconListener)
        m_iconSession?.close()
    }

    override fun messageReceived(client: TcpClient, message: String) {
        if (client !== m_client) return
        val iconSession = m_iconSession?.takeIf { it.client === client }
        when (val line = m_lineClassifier.classify(message)) {
            is ServerLine.State -> {
                iconSession?.onState(line.state)
                m_serverMessage.onNext(line.state)
            }
            is ServerLine.Icon -> iconSession?.onReply(line.reply)
            is ServerLine.VersionMismatch -> runOnUiThread { onVersionMismatch(line.version) }
            is ServerLine.Unknown -> Log.d(TAG, "Ignoring unknown server line")
            is ServerLine.Malformed -> Log.w(TAG, "Ignoring malformed server line: ${line.reason}")
        }
    }

    private fun processMessage(state: PcAudio) {
        runOnUiThread {
            val previous = m_pcAudio
            m_pcAudio = state
            if (SessionDetails.onlyDetailsChanged(previous, state)) {
                showDetails(state)
            } else {
                populateUi()
            }
        }
    }

    /** Updates details in the existing rows, so a changing tab title does not rebuild the list. */
    private fun showDetails(state: PcAudio) {
        val details = state.defaultDevice.sessions.orEmpty().associate { it.id to it.detail }
        m_sessionViewHolders.forEach { it.showDetail(details[it.sessionId]) }
    }

    private fun onVersionMismatch(serverVersion: Int?) {
        Log.d(TAG, "PC versions is wrong. PC $serverVersion APP $PROTOCOL_VERSION")
        val message = when (Versions.olderSide(serverVersion)) {
            OlderSide.SERVER -> R.string.TOAST_server_too_old
            OlderSide.APP -> R.string.TOAST_app_too_old
        }
        showMessageLong(getString(message, serverVersion ?: 0, PROTOCOL_VERSION))
        m_client?.stopClient()
    }

    override fun onConnecting() {
        runOnUiThread { binding.connectingProgress.visibility = View.VISIBLE }
    }

    override fun onConnect() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val ipInput = binding.ipAddressInput.text.toString()
        if (ipInput != prefs.getString(KEY_SERVER_IP, null)) {
            prefs.edit().putString(KEY_SERVER_IP, ipInput).apply()
        }

        val portInput = binding.portInput.text.toString()
        if (portInput != prefs.getString(KEY_PORT, null)) {
            prefs.edit().putString(KEY_PORT, portInput).apply()
        }

        runOnUiThread { showMixer() }
    }

    override fun onDisconnect(client: TcpClient) {
        if (client !== m_client) return
        m_iconSession?.takeIf { it.client === client }?.close()
        runOnUiThread { showConnect() }
    }

    private fun showMixer() {
        binding.connectingProgress.visibility = View.GONE

        binding.aboutButton.visibility = View.GONE
        binding.ipAddressInputContainer.visibility = View.GONE
        binding.portInputContainer.visibility = View.GONE
        binding.connectButton.visibility = View.GONE
        binding.discoveredServersContainer.visibility = View.GONE
        binding.bigIconView?.visibility = View.GONE
        binding.versionView.visibility = View.GONE

        binding.deviceSelector.visibility = View.VISIBLE
        binding.MIXERScroll.visibility = View.VISIBLE
        binding.MIXERContainer.visibility = View.VISIBLE

        m_disconnectMenuItem?.isVisible = true
    }

    private fun showConnect() {
        binding.connectingProgress.visibility = View.GONE

        binding.aboutButton.visibility = View.VISIBLE
        binding.ipAddressInputContainer.visibility = View.VISIBLE
        binding.portInputContainer.visibility = View.VISIBLE
        binding.connectButton.visibility = View.VISIBLE
        binding.bigIconView?.visibility = View.VISIBLE
        binding.versionView.visibility = View.VISIBLE
        updateDiscoveredServers()

        binding.deviceSelector.visibility = View.GONE
        binding.MIXERScroll.visibility = View.GONE
        binding.MIXERContainer.visibility = View.GONE

        m_disconnectMenuItem?.isVisible = false

        supportActionBar?.title = getString(R.string.app_name)
    }

    override fun onVolumeChangeStarted() {
        m_serverMessageValve.offer(false)
    }

    override fun onVolumeChangeStopped() {
        m_serverMessageValve.offer(true)
    }

    override fun onMasterVolumeChange(newVolume: Float, muted: Boolean) {
        val pcAudio = m_pcAudio
        pcAudio?.let {
            val newPcAudio = Updates.master(pcAudio.defaultDevice.deviceId, newVolume, muted)

            Log.d(TAG, "onMasterVolumeChange")

            m_client?.sendMessageAsync(m_gson.toJson(newPcAudio))
        }
    }

    override fun onVolumeChange(id: String, newVolume: Float, muted: Boolean) {
        val pcAudio = m_pcAudio
        pcAudio?.let {
            val newPcAudio = Updates.session(pcAudio.defaultDevice.deviceId, id, newVolume, muted)

            Log.d(TAG, "onVolumeChange")

            m_client?.sendMessageAsync(m_gson.toJson(newPcAudio))
        }
    }

    private fun populateUi() {
        val pcAudio = m_pcAudio
        pcAudio?.let {

            //Log.d(TAG, m_pcAudio.toString())

            var selectedPos = -1
            m_deviceAdapter.clear()
            if (pcAudio.deviceIds != null) {
                for ((index, device) in pcAudio.deviceIds.entries.withIndex()) {
                    m_deviceAdapter.add(AudioDeviceItem(device.value, device.key))

                    if (pcAudio.defaultDevice.deviceId == device.key) {
                        selectedPos = index
                    }
                }
            }
            m_deviceAdapter.notifyDataSetChanged()

            if (selectedPos > -1) {
                binding.deviceSelector.setSelection(selectedPos, false)
            }

            binding.MIXERContainer.removeAllViews()

            supportActionBar?.title = pcAudio.defaultDevice.name

            // Add master control
            val master = AudioSession(
                getString(R.string.master_audio_session_name),
                "master",
                pcAudio.defaultDevice.masterVolume ?: 100.0f,
                pcAudio.defaultDevice.masterMuted ?: false
            )

            val masterRootView = LayoutInflater.from(this)
                .inflate(R.layout.audio_session_master, binding.MIXERContainer, false)
            val masterViewHolder = AudioSessionViewHolder(masterRootView, master, this, true)

            masterViewHolder.bind(master)

            binding.MIXERContainer.addView(masterRootView)

            val favorites = AudioSessionOptions.getFavorites(this)
            val hidden = AudioSessionOptions.getHidden(this)

            var sortedSessions = pcAudio.defaultDevice.sessions?.toList() ?: listOf()
            sortedSessions = sortedSessions.filter { !hidden.contains(it.id) }
            sortedSessions =
                sortedSessions.sortedBy { audioSession -> audioSession.name?.toUpperCase() }
            sortedSessions = sortedSessions.sortedByDescending { favorites.contains(it.id) }

            // Add each session control
            val viewHolders = ArrayList<AudioSessionViewHolder>(sortedSessions.size)
            for (session in sortedSessions) {
                val rootView = LayoutInflater.from(this)
                    .inflate(R.layout.audio_session, binding.MIXERContainer, false)
                val viewHolder = AudioSessionViewHolder(rootView, session, this, iconLookup = ::cachedIcon)

                viewHolder.bind(session)

                binding.MIXERContainer.addView(rootView)
                viewHolders += viewHolder
            }
            m_sessionViewHolders = viewHolders
        }
    }

    private fun cachedIcon(iconId: String): Bitmap? = m_iconSession?.cachedIcon(iconId)

    /** Called on the icon thread; several icons arriving together cause one refresh. */
    private fun onIconsChanged() {
        if (!m_iconRefreshPosted.compareAndSet(false, true)) return
        runOnUiThread {
            m_iconRefreshPosted.set(false)
            if (!isDestroyed) m_sessionViewHolders.forEach { it.refreshIcon() }
        }
    }

    private fun showMessageLong(message: String) {
        showMessage(message, Snackbar.LENGTH_LONG)
    }

    private fun showMessage(@StringRes messageId: Int, length: Int = Snackbar.LENGTH_SHORT) {
        showMessage(getString(messageId), length)
    }

    private fun showMessage(message: String, length: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(binding.root, message, length).show()
    }

    fun showAudioSessionOptions(v: View) {
        val popup = PopupMenu(this, v)
        popup.menuInflater.inflate(R.menu.audio_session_options, popup.menu)

        val favorite = popup.menu.findItem(R.id.AUDIO_SESSION_favorite)
        val unfavorite = popup.menu.findItem(R.id.AUDIO_SESSION_unfavorite)
        val hide = popup.menu.findItem(R.id.AUDIO_SESSION_hide)

        val audioSession: AudioSession = v.tag as AudioSession

        if (AudioSessionOptions.isFavorite(audioSession.id, this)) {
            favorite.isVisible = false
            unfavorite.isVisible = true
        } else {
            favorite.isVisible = true
            unfavorite.isVisible = false
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.AUDIO_SESSION_favorite -> {
                    audioSession.name?.let {
                        AudioSessionOptions.addFavorite(audioSession.id, this)
                        populateUi()
                    }
                }
                R.id.AUDIO_SESSION_unfavorite -> {
                    audioSession.name?.let {
                        AudioSessionOptions.removeFavorite(audioSession.id, this)
                        populateUi()
                    }
                }
                R.id.AUDIO_SESSION_hide -> {
                    audioSession.name?.let {
                        AudioSessionOptions.addHidden(audioSession.id, this)
                        populateUi()
                    }
                }
            }
            true
        }

        popup.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val pcAudio = m_pcAudio
        if (m_client?.isRunning() == true && pcAudio != null) {
            return when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (pcAudio.defaultDevice.masterVolume != null) {
                        val newVolume = Math.max(pcAudio.defaultDevice.masterVolume - 10.0f, 0.0f)
                        onMasterVolumeChange(newVolume, pcAudio.defaultDevice.masterMuted ?: false)

                        m_pcAudio =
                            pcAudio.copy(defaultDevice = pcAudio.defaultDevice.copy(masterVolume = newVolume))
                        populateUi()
                    }
                    true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (pcAudio.defaultDevice.masterVolume != null) {
                        val newVolume = Math.min(pcAudio.defaultDevice.masterVolume + 10.0f, 100.0f)
                        onMasterVolumeChange(newVolume, pcAudio.defaultDevice.masterMuted ?: false)

                        m_pcAudio =
                            pcAudio.copy(defaultDevice = pcAudio.defaultDevice.copy(masterVolume = newVolume))
                        populateUi()
                    }
                    true
                }
                else ->
                    super.onKeyDown(keyCode, event)
            }
        } else {
            return super.onKeyDown(keyCode, event)
        }
    }
}
