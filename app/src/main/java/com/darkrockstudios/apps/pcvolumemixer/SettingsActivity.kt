package com.darkrockstudios.apps.pcvolumemixer

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v4.app.NavUtils
import android.view.MenuItem


/**
 * A [PreferenceActivity] that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 *
 * See [Android Design: Settings](http://developer.android.com/design/patterns/settings.html)
 * for design guidelines and the [Settings API Guide](http://developer.android.com/guide/topics/ui/settings.html)
 * for more information on developing a Settings UI.
 */
class SettingsActivity : AppCompatPreferenceActivity()
{
	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setupActionBar()
	}

	/**
	 * Set up the [android.app.ActionBar], if the API is available.
	 */
	private fun setupActionBar()
	{
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
	}

	override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean
	{
		val id = item.itemId
		if (id == android.R.id.home)
		{
			if (!super.onMenuItemSelected(featureId, item))
			{
				NavUtils.navigateUpFromSameTask(this)
			}
			return true
		}
		return super.onMenuItemSelected(featureId, item)
	}

	/**
	 * {@inheritDoc}
	 */
	override fun onIsMultiPane(): Boolean
	{
		return isXLargeTablet(this)
	}

	/**
	 * {@inheritDoc}
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	override fun onBuildHeaders(target: List<PreferenceActivity.Header>)
	{
		loadHeadersFromResource(R.xml.pref_headers, target)
	}

	/**
	 * This method stops fragment injection in malicious applications.
	 * Make sure to deny any unknown fragments here.
	 */
	override fun isValidFragment(fragmentName: String): Boolean
	{
		return PreferenceFragment::class.java.name == fragmentName
			   || GeneralPreferenceFragment::class.java.name == fragmentName
	}

	/**
	 * This fragment shows general preferences only. It is used when the
	 * activity is showing a two-pane settings UI.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	class GeneralPreferenceFragment : PreferenceFragment()
	{
		override fun onCreate(savedInstanceState: Bundle?)
		{
			super.onCreate(savedInstanceState)
			addPreferencesFromResource(R.xml.pref_general)
			setHasOptionsMenu(true)
		}

		override fun onOptionsItemSelected(item: MenuItem): Boolean
		{
			val id = item.itemId
			if (id == android.R.id.home)
			{
				startActivity(Intent(activity, SettingsActivity::class.java))
				return true
			}
			return super.onOptionsItemSelected(item)
		}
	}

	companion object
	{
		/**
		 * Helper method to determine if the device has an extra-large screen. For
		 * example, 10" tablets are extra-large.
		 */
		private fun isXLargeTablet(context: Context): Boolean
		{
			return context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_XLARGE
		}
	}
}
