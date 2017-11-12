package com.darkrockstudios.apps.pcvolumemixer

import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), TcpClient.ServerListener, AudioSessionViewHolder.VolumeChangeListener
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

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

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

		autoConnect()
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean
	{
		val inflater = menuInflater
		inflater.inflate(R.menu.main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean
	{
		return when (item.itemId)
		{
			R.id.MENU_disconnect ->
			{
				m_client?.stopClient()
				true
			}
			else -> super.onOptionsItemSelected(item)
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
		Log.d("audio", "messageReceived")

		m_pcAudio = m_gson.fromJson<PcAudio>(message, PcAudio::class.java)

		runOnUiThread(this::populateUi)
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

		MIXER_scroll.visibility = View.VISIBLE
		MIXER_container.visibility = View.VISIBLE
	}

	private fun showConnect()
	{
		ip_address_input_container.visibility = View.VISIBLE
		port_input_container.visibility = View.VISIBLE
		connect_button.visibility = View.VISIBLE

		MIXER_scroll.visibility = View.GONE
		MIXER_container.visibility = View.GONE
	}

	override fun onVolumeChange(name: String, newVolume: Float)
	{
		Log.d("audio", "onVolumeChange")
		val updatedSession = AudioSession(name, newVolume)

		m_client?.sendMessageAsync(m_gson.toJson(updatedSession))
	}

	private fun populateUi()
	{
		m_pcAudio?.let {

			Log.d("audio", m_pcAudio.toString())

			MIXER_container.removeAllViews()

			if (it.devices.isNotEmpty())
			{
				supportActionBar?.title = it.devices[0].name

				for (session in it.devices[0].sessions.reversed())
				{
					val rootView = LayoutInflater.from(this).inflate(R.layout.audio_session, MIXER_container, false)
					val viewHolder = AudioSessionViewHolder(rootView, session, this)

					viewHolder.bind(session)

					MIXER_container.addView(rootView)
				}
			}
		}
	}
}
