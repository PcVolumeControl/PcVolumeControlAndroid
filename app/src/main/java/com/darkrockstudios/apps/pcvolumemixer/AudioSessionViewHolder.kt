package com.darkrockstudios.apps.pcvolumemixer

import android.view.View
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ToggleButton
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.h6ah4i.android.widget.verticalseekbar.VerticalSeekBarWrapper


/**
 * Created by adamw on 9/16/2017.
 */
class AudioSessionViewHolder(rootView: View, session: AudioSession, listener: VolumeChangeListener, isMaster: Boolean = false)
	: SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener
{
	private val m_isMaster = isMaster

	private val m_listener: VolumeChangeListener = listener
	private val m_session: AudioSession = session

	private val m_container = rootView
	private val m_volumeBarContainer: VerticalSeekBarWrapper = rootView.findViewById(R.id.AUDIO_volume_container)
	private val m_volumeBar: SeekBar = rootView.findViewById(R.id.AUDIO_volume)
	private val m_sessionName: TextView = rootView.findViewById(R.id.AUDIO_name)
	private val muteButton: ToggleButton = rootView.findViewById(R.id.AUDIO_mute)

	fun bind(session: AudioSession)
	{
		m_sessionName.text = session.name

		m_volumeBar.min = 0
		m_volumeBar.max = 100

		m_volumeBar.progress = (session.volume * 100).toInt()

		m_volumeBar.setOnSeekBarChangeListener(this)

		muteButton.isChecked = session.muted

		muteButton.setOnCheckedChangeListener(this)
	}

	override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean)
	{

	}

	override fun onStartTrackingTouch(seekBar: SeekBar?)
	{

	}

	override fun onStopTrackingTouch(seekBar: SeekBar?)
	{
		val volume = (m_volumeBar.progress / 100f)
		val isMuted = muteButton.isChecked

		if (m_isMaster)
		{
			m_listener.onMasterVolumeChange(volume, isMuted)
		}
		else
		{
			m_listener.onVolumeChange(m_session.name, volume, isMuted)
		}
	}

	override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean)
	{
		val volume = (m_volumeBar.progress / 100f)
		val isMuted = muteButton.isChecked

		if (m_isMaster)
		{
			m_listener.onMasterVolumeChange(volume, isMuted)
		}
		else
		{
			m_listener.onVolumeChange(m_session.name, volume, isMuted)
		}
	}

	interface VolumeChangeListener
	{
		fun onVolumeChange(name: String, newVolume: Float, muted: Boolean)
		fun onMasterVolumeChange(newVolume: Float, muted: Boolean)
	}
}