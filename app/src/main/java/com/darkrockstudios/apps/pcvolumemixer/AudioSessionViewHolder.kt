package com.darkrockstudios.apps.pcvolumemixer

import android.annotation.TargetApi
import android.os.Build
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSessionOptions


/**
 * Created by adamw on 9/16/2017.
 */
class AudioSessionViewHolder(rootView: View, session: AudioSession, listener: VolumeChangeListener, isMaster: Boolean = false)
	: SeekBar.OnSeekBarChangeListener
{
	private val m_isMaster = isMaster

	private val m_listener: VolumeChangeListener = listener
	private val m_session: AudioSession = session

	private val m_container = rootView
	private val m_volumeBarContainer: View = rootView.findViewById(R.id.AUDIO_volume_container)
	private val m_volumeBar: SeekBar = rootView.findViewById(R.id.AUDIO_volume)
	private val m_sessionName: TextView = rootView.findViewById(R.id.AUDIO_name)
	private val muteButton: ImageButton = rootView.findViewById(R.id.AUDIO_mute)

	private val menuButton: ImageButton? = rootView.findViewById(R.id.AUDIO_menu)

	@TargetApi(Build.VERSION_CODES.O)
	fun bind(session: AudioSession)
	{
		m_sessionName.text = session.name

		if (AudioSessionOptions.isFavorite(session.id, m_sessionName.context))
		{
			m_sessionName.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite, 0, 0, 0)
		}

		if (OsUtil.sIsAtLeastO)
		{
			m_volumeBar.min = 0
		}
		m_volumeBar.max = 100

		m_volumeBar.progress = session.volume.toInt()

		m_volumeBar.setOnSeekBarChangeListener(this)

		muteButton.isSelected = session.muted
		muteButton.setOnClickListener { onCheckedChanged( it ) }

		menuButton?.tag = session
	}

	override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean)
	{

	}

	override fun onStartTrackingTouch(seekBar: SeekBar?)
	{
		m_listener.onVolumeChangeStarted()
	}

	override fun onStopTrackingTouch(seekBar: SeekBar?)
	{
		val volume = m_volumeBar.progress.toFloat()
		val isMuted = muteButton.isSelected

		if (m_isMaster)
		{
			m_listener.onMasterVolumeChange(volume, isMuted)
		}
		else
		{
			m_listener.onVolumeChange(m_session.id, volume, isMuted)
		}

		m_listener.onVolumeChangeStopped()
	}

	private fun onCheckedChanged(buttonView: View?)
	{
		val volume = m_volumeBar.progress.toFloat()
		val isMuted = !muteButton.isSelected

		muteButton.isSelected = isMuted

		if (m_isMaster)
		{
			m_listener.onMasterVolumeChange(volume, isMuted)
		}
		else
		{
			m_listener.onVolumeChange(m_session.id, volume, isMuted)
		}
	}

	interface VolumeChangeListener
	{
		fun onVolumeChangeStarted()
		fun onVolumeChangeStopped()

		fun onVolumeChange(id: String, newVolume: Float, muted: Boolean)
		fun onMasterVolumeChange(newVolume: Float, muted: Boolean)
	}
}