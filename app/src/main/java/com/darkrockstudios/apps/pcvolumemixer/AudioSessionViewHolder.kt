package com.darkrockstudios.apps.pcvolumemixer

import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession

/**
 * Created by adamw on 9/16/2017.
 */
class AudioSessionViewHolder(rootView: View, session: AudioSession, listener: VolumeChangeListener)
	: SeekBar.OnSeekBarChangeListener
{
	private val m_listener: VolumeChangeListener = listener
	private val m_session: AudioSession = session

	private val m_volumeBar: SeekBar = rootView.findViewById(R.id.AUDIO_volume)
	private val m_sessionName: TextView = rootView.findViewById(R.id.AUDIO_name)

	fun bind(session: AudioSession)
	{
		m_sessionName.text = session.name
		Log.d("debug", "session: " + session.name);
		m_volumeBar.min = 0
		m_volumeBar.max = 100

		m_volumeBar.progress = (session.volume * 100).toInt()

		m_volumeBar.setOnSeekBarChangeListener(this)
	}

	override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean)
	{

	}

	override fun onStartTrackingTouch(seekBar: SeekBar?)
	{

	}

	override fun onStopTrackingTouch(seekBar: SeekBar?)
	{
		seekBar?.apply {
			m_listener.onVolumeChange(m_session.name, (progress / 100f))
		}
	}

	interface VolumeChangeListener
	{
		fun onVolumeChange(name: String, newVolume: Float)
	}
}