package com.darkrockstudios.apps.pcvolumemixer

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import mehdi.sakout.aboutpage.AboutPage
import mehdi.sakout.aboutpage.Element


class AboutActivity : AppCompatActivity()
{
	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		val versionElement = Element()
		versionElement.title = getString(R.string.app_version_name, getVersionName())

		val protocolElement = Element()
		protocolElement.title = getString(R.string.protocol_version, PROTOCOL_VERSION)

		val windowsElement = Element()
		windowsElement.iconDrawable = R.drawable.ic_file_download
		windowsElement.title = getString(R.string.get_windows)
		val windowsIntent = Intent(Intent.ACTION_VIEW)
		windowsIntent.data = Uri.parse("https://github.com/PcVolumeControl/PcVolumeControlWindows/releases/latest")
		windowsElement.intent = windowsIntent

		val aboutPage = AboutPage(this)
				.setDescription(getString(R.string.about_description))
				.setImage(R.drawable.ic_about_icon)
				.addItem(windowsElement)
				.addItem(versionElement)
				.addItem(protocolElement)
				.addGroup(getString(R.string.about_connect_section))
				.addEmail("darkrockstudios@gmail.com")
				.addPlayStore("com.darkrockstudios.apps.pcvolumemixer")
				.addGitHub("PcVolumeControl")
				.create()

		setContentView(aboutPage)

		supportActionBar?.setDisplayHomeAsUpEnabled(true);
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean
	{
		when (item.itemId)
		{
			android.R.id.home -> // Respond to the action bar's Up/Home button
			{
				NavUtils.navigateUpFromSameTask(this)
				return true
			}
		}
		return super.onOptionsItemSelected(item)
	}

	private fun getVersionName(): String
	{
		var version = ""
		try
		{
			val pInfo = this.packageManager.getPackageInfo(packageName, 0)
			version = pInfo.versionName
		}
		catch (e: PackageManager.NameNotFoundException)
		{
			e.printStackTrace()
		}
		return version
	}
}
