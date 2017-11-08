package com.darkrockstudios.apps.pcvolumemixer

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), TcpClient.OnMessageReceived, AudioSessionViewHolder.VolumeChangeListener
{
	private val m_gson = Gson()

	private lateinit var m_client: TcpClient

	private var m_pcAudio: PcAudio? = null

	private val m_audioViewHolders: List<AudioSessionViewHolder> = mutableListOf()

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		m_client = TcpClient(this)
		Thread(m_client::run).start()
	}

	override fun onDestroy()
	{
		super.onDestroy()

		m_client.stopClient()
	}

	override fun messageReceived(message: String)
	{
		Log.d("audio", "messageReceived")

		m_pcAudio = m_gson.fromJson<PcAudio>(message, PcAudio::class.java)

		runOnUiThread(this::populateUi)
	}

	override fun onVolumeChange(name: String, newVolume: Float)
	{
		Log.d("audio", "onVolumeChange")
		val updatedSession = AudioSession(name,newVolume)

		m_client.sendMessageAsync(m_gson.toJson(updatedSession))
	}

	private fun populateUi()
	{
		m_pcAudio?.let {

			Log.d("audio", m_pcAudio.toString())

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
