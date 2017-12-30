package com.darkrockstudios.apps.pcvolumemixer

import android.app.Application
import android.view.Display
import com.google.android.things.device.ScreenManager
import java.util.concurrent.TimeUnit

/**
 * Created by adamw on 12/29/2017.
 */
class IotApp : Application()
{
	override fun onCreate()
	{
		super.onCreate()

		val screenManager = ScreenManager(Display.DEFAULT_DISPLAY)
		// Set brightness to a fixed value
		screenManager.setBrightnessMode(ScreenManager.BRIGHTNESS_MODE_MANUAL)
		//screenManager.setBrightness(255) //Max it out.
		screenManager.setScreenOffTimeout(1, TimeUnit.MINUTES)
	}
}