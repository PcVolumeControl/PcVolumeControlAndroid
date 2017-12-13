package com.darkrockstudios.apps.pcvolumemixer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.PopupMenu
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.darkrockstudios.apps.pcvolumemixer.data.AudioDevice
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSessionOptions
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.google.gson.Gson
import hu.akarnokd.rxjava2.operators.FlowableTransformers
import io.reactivex.disposables.Disposable
import io.reactivex.processors.PublishProcessor
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.startActivity


class MainActivity : AppCompatActivity(), TcpClient.ServerListener, AudioSessionViewHolder.VolumeChangeListener, AdapterView.OnItemSelectedListener
{
	companion object
	{
		val TAG = MainActivity::class.java.simpleName
		val KEY_SERVER_IP = "server_ip"
		val KEY_PORT = "server_port"
	}

	private val m_gson = Gson()
	private var m_client: TcpClient? = null
	private var m_pcAudio: PcAudio? = null
	private var m_disconnectMenuItem: MenuItem? = null

	private lateinit var m_deviceAdapter: ArrayAdapter<AudioDeviceItem>

	private val m_serverMessageValve = PublishProcessor.create<Boolean>()
	private val m_serverMessage = PublishProcessor.create<String>()
	private var m_serverSubscription: Disposable? = null

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		version_view.text = getString(R.string.app_version, VERSION)

		setSupportActionBar(app_toolbar)

		connect_button.setOnClickListener { connectToServer(ip_address_input.text.toString(), port_input.text.toString()) }

		val prefs = PreferenceManager.getDefaultSharedPreferences(this)

		val defaultIp = prefs.getString(KEY_SERVER_IP, null)
		if (defaultIp != null)
		{
			ip_address_input.setText(defaultIp)
		}

		val defaultPort = prefs.getString(KEY_PORT, null)
		if (defaultPort != null)
		{
			port_input.setText(defaultPort)
		}

		m_deviceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item)
		device_selector.adapter = m_deviceAdapter

		device_selector.onItemSelectedListener = this

		about_button.setOnClickListener { goToAbout() }

		autoConnect()

		m_serverSubscription = m_serverMessage
				.serialize()
				.onBackpressureLatest()
				.compose(FlowableTransformers.valve(m_serverMessageValve, true))
				.subscribe(this::processMessage)
	}

	private fun goToAbout()
	{
		startActivity<AboutActivity>()
	}

	override fun onNothingSelected(parent: AdapterView<*>?)
	{

	}

	override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long)
	{
		val newAudioDevice = m_deviceAdapter.getItem(position)

		Log.d(TAG, "Changing default device to: " + newAudioDevice.toString())

		val pcAudio = m_pcAudio
		pcAudio?.let {
			val newPcAudio = PcAudio(VERSION,
									 null,
									 AudioDevice(newAudioDevice.id,
												 null,
												 null,
												 null,
												 null))

			m_client?.sendMessageAsync(m_gson.toJson(newPcAudio))
		}
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean
	{
		val inflater = menuInflater
		inflater.inflate(R.menu.main, menu)

		m_disconnectMenuItem = menu?.findItem(R.id.MENU_disconnect)

		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu?): Boolean
	{
		m_disconnectMenuItem?.isVisible = m_client?.isRunning() ?: false

		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean
			= when (item.itemId)
	{
		R.id.MENU_disconnect   ->
		{
			m_client?.stopClient()
			true
		}
		R.id.MENU_pc_app       ->
		{
			sendPcApp()
			true
		}
		R.id.MENU_clear_hidden ->
		{
			AudioSessionOptions.clearHidden(this)
			populateUi()
			showMessage(R.string.TOAST_hidden_cleared)
			true
		}
		else                   ->
			super.onOptionsItemSelected(item)
	}

	private fun sendPcApp()
	{
		val intent = Intent(Intent.ACTION_SEND)
		intent.type = "message/rfc822"
		intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.PCAPP_EMAIL_title))
		intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.PCAPP_EMAIL_body))
		try
		{
			startActivity(Intent.createChooser(intent, getString(R.string.PCAPP_EMAIL_title)))
		}
		catch (ex: ActivityNotFoundException)
		{
			showMessageLong(getString(R.string.PCAPP_EMAIL_failed))
		}
	}

	private fun connectToServer(serverIp: String, port: String)
	{
		m_client?.stopClient()

		m_client = TcpClient(this, serverIp, Integer.parseInt(port))
		m_client?.let {
			Thread(it::run).start()
		}
	}

	private fun autoConnect()
	{
		val serverIp = ip_address_input.text.toString()
		val port = port_input.text.toString()
		if (!TextUtils.isEmpty(serverIp) && !TextUtils.isEmpty(port))
		{
			Log.d(TAG, "Auto connecting to server")
			connectToServer(serverIp, port)
		}
	}

	override fun onStart()
	{
		super.onStart()

		// Want to make sure we are always receiving messages when we start
		m_serverMessageValve.onNext(true)

		if (m_client?.isRunning() == false)
		{
			autoConnect()
		}
	}

	override fun onStop()
	{
		super.onStop()

		m_client?.stopClient()
	}

	override fun onDestroy()
	{
		super.onDestroy()

		m_serverSubscription?.dispose()
	}

	override fun messageReceived(message: String)
	{
		m_serverMessage.onNext(message)
	}

	private fun processMessage(message: String)
	{
		m_pcAudio = m_gson.fromJson<PcAudio>(message, PcAudio::class.java)

		if (m_pcAudio?.version == VERSION)
		{
			runOnUiThread(this::populateUi)
		}
		else
		{
			Log.d(TAG, "PC versions is wrong. PC " + m_pcAudio?.version + " APP " + VERSION)
			showMessageLong(getString(R.string.TOAST_wrong_version, m_pcAudio?.version, VERSION))
			m_client?.stopClient()
		}
	}

	override fun onConnecting()
	{
		runOnUiThread { connecting_progress.visibility = View.VISIBLE }
	}

	override fun onConnect()
	{
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)

		val ipInput = ip_address_input.text.toString()
		if (ipInput != prefs.getString(KEY_SERVER_IP, null))
		{
			prefs.edit().putString(KEY_SERVER_IP, ipInput).apply()
		}

		val portInput = port_input.text.toString()
		if (portInput != prefs.getString(KEY_PORT, null))
		{
			prefs.edit().putString(KEY_PORT, portInput).apply()
		}

		runOnUiThread { showMixer() }
	}

	override fun onDisconnect()
	{
		runOnUiThread { showConnect() }
	}

	private fun showMixer()
	{
		connecting_progress.visibility = View.GONE

		about_button.visibility = View.GONE
		ip_address_input_container.visibility = View.GONE
		port_input_container.visibility = View.GONE
		connect_button.visibility = View.GONE
		big_icon_view.visibility = View.GONE
		version_view.visibility = View.GONE

		device_selector.visibility = View.VISIBLE
		MIXER_scroll.visibility = View.VISIBLE
		MIXER_container.visibility = View.VISIBLE

		m_disconnectMenuItem?.isVisible = true
	}

	private fun showConnect()
	{
		connecting_progress.visibility = View.GONE

		about_button.visibility = View.VISIBLE
		ip_address_input_container.visibility = View.VISIBLE
		port_input_container.visibility = View.VISIBLE
		connect_button.visibility = View.VISIBLE
		big_icon_view.visibility = View.VISIBLE
		version_view.visibility = View.VISIBLE

		device_selector.visibility = View.GONE
		MIXER_scroll.visibility = View.GONE
		MIXER_container.visibility = View.GONE

		m_disconnectMenuItem?.isVisible = false

		supportActionBar?.title = getString(R.string.app_name)
	}

	override fun onVolumeChangeStarted()
	{
		m_serverMessageValve.offer(false)
	}

	override fun onVolumeChangeStopped()
	{
		m_serverMessageValve.offer(true)
	}

	override fun onMasterVolumeChange(newVolume: Float, muted: Boolean)
	{
		val pcAudio = m_pcAudio
		pcAudio?.let {
			val newPcAudio = PcAudio(VERSION,
									 null,
									 AudioDevice(pcAudio.defaultDevice.deviceId,
												 null,
												 newVolume,
												 muted,
												 null))

			Log.d(TAG, "onMasterVolumeChange")

			m_client?.sendMessageAsync(m_gson.toJson(newPcAudio))
		}
	}

	override fun onVolumeChange(id: String, newVolume: Float, muted: Boolean)
	{
		val pcAudio = m_pcAudio
		pcAudio?.let {
			val newPcAudio = PcAudio(VERSION,
									 null,
									 AudioDevice(pcAudio.defaultDevice.deviceId,
												 null,
												 null,
												 null,
												 listOf(AudioSession(null, id, newVolume, muted))))

			Log.d(TAG, "onVolumeChange")

			m_client?.sendMessageAsync(m_gson.toJson(newPcAudio))
		}
	}

	private fun populateUi()
	{
		val pcAudio = m_pcAudio
		pcAudio?.let {

			//Log.d(TAG, m_pcAudio.toString())

			var selectedPos = -1
			m_deviceAdapter.clear()
			if (pcAudio.deviceIds != null)
			{
				for ((index, device) in pcAudio.deviceIds.entries.withIndex())
				{
					m_deviceAdapter.add(AudioDeviceItem(device.value, device.key))

					if (pcAudio.defaultDevice.deviceId == device.key)
					{
						selectedPos = index
					}
				}
			}
			m_deviceAdapter.notifyDataSetChanged()

			if (selectedPos > -1)
			{
				device_selector.setSelection(selectedPos, false)
			}

			MIXER_container.removeAllViews()

			supportActionBar?.title = pcAudio.defaultDevice.name

			// Add master control
			val master = AudioSession(getString(R.string.master_audio_session_name),
									  "master",
									  pcAudio.defaultDevice.masterVolume ?: 100.0f,
									  pcAudio.defaultDevice.masterMuted ?: false)

			val masterRootView = LayoutInflater.from(this).inflate(R.layout.audio_session_master, MIXER_container, false)
			val masterViewHolder = AudioSessionViewHolder(masterRootView, master, this, true)

			masterViewHolder.bind(master)

			MIXER_container.addView(masterRootView)

			val favorites = AudioSessionOptions.getFavorites(this)
			val hidden = AudioSessionOptions.getHidden(this)

			var sortedSessions = pcAudio.defaultDevice.sessions?.toList() ?: listOf()
			sortedSessions = sortedSessions.filter { !hidden.contains(it.name) }
			sortedSessions = sortedSessions.sortedBy { audioSession -> audioSession.name?.toUpperCase() }
			sortedSessions = sortedSessions.sortedByDescending { favorites.contains(it.name) }

			// Add each session control
			for (session in sortedSessions)
			{
				val rootView = LayoutInflater.from(this).inflate(R.layout.audio_session, MIXER_container, false)
				val viewHolder = AudioSessionViewHolder(rootView, session, this)

				viewHolder.bind(session)

				MIXER_container.addView(rootView)
			}
		}
	}

	private fun showMessageLong(message: String)
	{
		showMessage(message, Snackbar.LENGTH_LONG)
	}

	private fun showMessage(@StringRes messageId: Int, length: Int = Snackbar.LENGTH_SHORT)
	{
		showMessage(getString(messageId), length)
	}

	private fun showMessage(message: String, length: Int = Snackbar.LENGTH_SHORT)
	{
		app_container?.let {
			val snackbar = Snackbar.make(app_container, message, length)
			snackbar.show()
		}
	}

	fun showAudioSessionOptions(v: View)
	{
		val popup = PopupMenu(this, v)
		popup.menuInflater.inflate(R.menu.audio_session_options, popup.menu)

		val favorite = popup.menu.findItem(R.id.AUDIO_SESSION_favorite)
		val unfavorite = popup.menu.findItem(R.id.AUDIO_SESSION_unfavorite)
		val hide = popup.menu.findItem(R.id.AUDIO_SESSION_hide)

		val audioSession: AudioSession = v.tag as AudioSession

		if (AudioSessionOptions.isFavorite(audioSession.name, this))
		{
			favorite.isVisible = false
			unfavorite.isVisible = true
		}
		else
		{
			favorite.isVisible = true
			unfavorite.isVisible = false
		}

		popup.setOnMenuItemClickListener { item ->
			when (item.itemId)
			{
				R.id.AUDIO_SESSION_favorite   ->
				{
					audioSession.name?.let {
						AudioSessionOptions.addFavorite(audioSession.name, this)
						populateUi()
					}
				}
				R.id.AUDIO_SESSION_unfavorite ->
				{
					audioSession.name?.let {
						AudioSessionOptions.removeFavorite(audioSession.name, this)
						populateUi()
					}
				}
				R.id.AUDIO_SESSION_hide       ->
				{
					audioSession.name?.let {
						AudioSessionOptions.addHidden(audioSession.name, this)
						populateUi()
					}
				}
			}
			true
		}

		popup.show()
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean
	{
		val pcAudio = m_pcAudio
		if (m_client?.isRunning() == true && pcAudio != null)
		{
			return when (keyCode)
			{
				KeyEvent.KEYCODE_VOLUME_DOWN ->
				{
					if (pcAudio.defaultDevice.masterVolume != null)
					{
						val newVolume = Math.max(pcAudio.defaultDevice.masterVolume - 10.0f, 0.0f)
						onMasterVolumeChange(newVolume, pcAudio.defaultDevice.masterMuted ?: false)

						m_pcAudio = pcAudio.copy(defaultDevice = pcAudio.defaultDevice.copy(masterVolume = newVolume))
						populateUi()
					}
					true
				}
				KeyEvent.KEYCODE_VOLUME_UP   ->
				{
					if (pcAudio.defaultDevice.masterVolume != null)
					{
						val newVolume = Math.min(pcAudio.defaultDevice.masterVolume + 10.0f, 100.0f)
						onMasterVolumeChange(newVolume, pcAudio.defaultDevice.masterMuted ?: false)

						m_pcAudio = pcAudio.copy(defaultDevice = pcAudio.defaultDevice.copy(masterVolume = newVolume))
						populateUi()
					}
					true
				}
				else                         ->
					super.onKeyDown(keyCode, event)
			}
		}
		else
		{
			return super.onKeyDown(keyCode, event)
		}
	}
}
