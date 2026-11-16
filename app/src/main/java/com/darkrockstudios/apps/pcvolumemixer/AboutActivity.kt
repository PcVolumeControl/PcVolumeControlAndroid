package com.darkrockstudios.apps.pcvolumemixer

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
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

		val serverElement = Element()
		serverElement.iconDrawable = R.drawable.ic_file_download
		serverElement.title = getString(R.string.get_server)
		val serverIntent = Intent(Intent.ACTION_VIEW)
		serverIntent.data = Uri.parse(SERVER_DOWNLOAD_URL)
		serverElement.intent = serverIntent

		val aboutPage = AboutPage(this)
				.setDescription(getString(R.string.about_description))
				.setImage(R.drawable.ic_about_icon)
				.addItem(serverElement)
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
			version = pInfo.versionName ?: ""
		}
		catch (e: PackageManager.NameNotFoundException)
		{
			e.printStackTrace()
		}
		return version
	}
}
