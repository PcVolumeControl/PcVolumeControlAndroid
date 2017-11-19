package com.darkrockstudios.apps.pcvolumemixer

/**
 * Created by adamw on 11/18/2017.
 */
data class AudioDeviceItem(val name: String, val id: String)
{
	override fun toString(): String = name
}