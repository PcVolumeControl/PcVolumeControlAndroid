package com.darkrockstudios.apps.pcvolumemixer

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), TcpClient.ServerListener, AudioSessionViewHolder.VolumeChangeListener
{
	private val m_gson = Gson()

	private var m_client: TcpClient? = null

	private var m_pcAudio: PcAudio? = null

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		connect_button.setOnClickListener { connectToServer(ip_address_input.text.toString()) }

		val serverIp = ip_address_input.text.toString()
		if (!TextUtils.isEmpty(serverIp))
		{
			connectToServer(serverIp)
		}
	}

	private fun connectToServer(serverIp: String)
	{
		m_client?.stopClient()

		m_client = TcpClient(this, serverIp)
		m_client?.let {
			Thread(it::run).start()
		}
	}

	override fun onDestroy()
	{
		super.onDestroy()

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
		runOnUiThread { showMixer() }
	}

	override fun onDisconnect()
	{
		runOnUiThread { showConnect() }
	}

	private fun showMixer()
	{
		ip_address_input_container.visibility = View.GONE
		connect_button.visibility = View.GONE

		MIXER_scroll.visibility = View.VISIBLE
		MIXER_container.visibility = View.VISIBLE
	}

	private fun showConnect()
	{
		ip_address_input_container.visibility = View.VISIBLE
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
				for (session in it.devices[0].sessions)
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
