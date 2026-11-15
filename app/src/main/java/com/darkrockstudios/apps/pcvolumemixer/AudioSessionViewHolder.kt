package com.darkrockstudios.apps.pcvolumemixer

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSessionOptions
import com.darkrockstudios.apps.pcvolumemixer.protocol.SessionDetails


/**
 * Created by adamw on 9/16/2017.
 *
 * @param iconLookup returns the cached icon for an icon id, or null to show the placeholder.
 */
class AudioSessionViewHolder(rootView: View, session: AudioSession, listener: VolumeChangeListener, isMaster: Boolean = false,
							 private val iconLookup: ((String) -> Bitmap?)? = null)
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
	private val m_icon: ImageView? = rootView.findViewById(R.id.AUDIO_icon)
	private val m_detail: TextView? = rootView.findViewById(R.id.AUDIO_detail)
	private val m_nameWidth: TextView? = rootView.findViewById(R.id.AUDIO_name_width)
	private var m_shownIcon: Bitmap? = null

	@TargetApi(Build.VERSION_CODES.O)
	fun bind(session: AudioSession)
	{
		m_sessionName.text = session.name
		m_nameWidth?.text = session.name
		refreshIcon()
		showDetail(session.detail)

		if (AudioSessionOptions.isFavorite(session.id, m_sessionName.context))
		{
			m_sessionName.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite, 0, 0, 0)
			m_nameWidth?.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite, 0, 0, 0)
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

	val sessionId: String
		get() = m_session.id

	/**
	 * Shows [detail] under the name, or hides it. The title block keeps its height either way, and
	 * the detail is folded into what a screen reader says for the name.
	 */
	fun showDetail(detail: String?)
	{
		val detailView = m_detail ?: return
		if (detail.isNullOrEmpty())
		{
			detailView.visibility = View.GONE
			detailView.text = null
			m_sessionName.maxLines = 2
		}
		else
		{
			detailView.text = detail
			detailView.visibility = View.VISIBLE
			m_sessionName.maxLines = 1
		}
		m_sessionName.contentDescription = SessionDetails.spokenLabel(m_sessionName.text?.toString(), detail)
	}

	/** Shows the session's icon if it is cached. The view keeps its size either way. */
	fun refreshIcon()
	{
		val iconView = m_icon ?: return
		val bitmap = m_session.iconId?.let { iconLookup?.invoke(it) }
		if (bitmap === m_shownIcon) return

		m_shownIcon = bitmap
		if (bitmap != null)
		{
			iconView.setImageBitmap(bitmap)
		}
		else
		{
			iconView.setImageResource(R.drawable.ic_app_placeholder)
		}
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