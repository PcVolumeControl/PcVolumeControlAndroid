package com.darkrockstudios.apps.pcvolumemixer

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.darkrockstudios.apps.pcvolumemixer.data.AudioDevice
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*


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

		autoConnect()
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
			                                     newAudioDevice.name,
			                                     null,
			                                     null,
			                                     listOf()))

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
		R.id.MENU_disconnect ->
		{
			m_client?.stopClient()
			true
		}
		R.id.MENU_pc_app ->
		{
			sendPcApp()
			true
		}
		else -> super.onOptionsItemSelected(item)
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
		catch (ex: android.content.ActivityNotFoundException)
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

	override fun messageReceived(message: String)
	{
		Log.d(TAG, "messageReceived: " + message)

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

	override fun onMasterVolumeChange(newVolume: Float, muted: Boolean)
	{
		val pcAudio = m_pcAudio
		pcAudio?.let {
			val newPcAudio = PcAudio(VERSION,
			                         null,
			                         AudioDevice(pcAudio.defaultDevice.deviceId,
			                                     pcAudio.defaultDevice.name,
			                                     newVolume,
			                                     muted,
			                                     listOf()))

			Log.d(TAG, "onMasterVolumeChange")

			m_client?.sendMessageAsync(m_gson.toJson(newPcAudio))
		}
	}

	override fun onVolumeChange(name: String, newVolume: Float, muted: Boolean)
	{
		val pcAudio = m_pcAudio
		pcAudio?.let {
			val newPcAudio = PcAudio(VERSION,
			                         null,
			                         AudioDevice(pcAudio.defaultDevice.deviceId,
			                                     pcAudio.defaultDevice.name,
			                                     null,
			                                     null,
			                                     listOf(AudioSession(name, newVolume, muted))))

			Log.d(TAG, "onVolumeChange")

			m_client?.sendMessageAsync(m_gson.toJson(newPcAudio))
		}
	}

	private fun populateUi()
	{
		val pcAudio = m_pcAudio
		pcAudio?.let {

			Log.d(TAG, m_pcAudio.toString())

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
			                          pcAudio.defaultDevice.masterVolume ?: 100f,
			                          pcAudio.defaultDevice.masterMuted ?: false)

			val masterRootView = LayoutInflater.from(this).inflate(R.layout.audio_session, MIXER_container, false)
			val masterViewHolder = AudioSessionViewHolder(masterRootView, master, this, true)

			masterViewHolder.bind(master)

			MIXER_container.addView(masterRootView)

			// Add each session control
			for (session in pcAudio.defaultDevice.sessions.reversed())
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

	private fun showMessage(message: String, length: Int = Snackbar.LENGTH_SHORT)
	{
		app_container?.let {
			val snackbar = Snackbar.make(app_container, message, length)
			snackbar.show()
		}
	}
}
