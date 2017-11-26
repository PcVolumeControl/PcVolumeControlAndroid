package com.darkrockstudios.apps.pcvolumemixer

/**
 * Created by adamw on 11/26/2017.
 */

object OsUtil
{
	var sIsAtLeastL: Boolean = false
	var sIsAtLeastO: Boolean = false

	/**
	 * @return The Android API version of the OS that we're currently running on.
	 */
	val apiVersion: Int
		get() = android.os.Build.VERSION.SDK_INT

	init
	{
		val v = apiVersion
		sIsAtLeastL = v >= android.os.Build.VERSION_CODES.LOLLIPOP
		sIsAtLeastO = v >= android.os.Build.VERSION_CODES.O
	}
}
