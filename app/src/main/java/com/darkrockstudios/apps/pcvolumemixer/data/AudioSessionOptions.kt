package com.darkrockstudios.apps.pcvolumemixer.data

import android.content.Context
import android.preference.PreferenceManager


/**
 * Created by adamw on 11/26/2017.
 */
class AudioSessionOptions
{
	companion object
	{
		private val KEY_FAVORITES = "favorite_audio_sessions"
		private val KEY_HIDDEN = "hidden_audio_sessions"

		fun addFavorite(processName: String, context: Context)
		{
			val favorites = getFavorites(context)

			if (!favorites.contains(processName))
			{
				favorites.add(processName)
				val prefs = PreferenceManager.getDefaultSharedPreferences(context)
				prefs.edit().putStringSet(KEY_FAVORITES,favorites).commit()
			}
		}

		fun removeFavorite(processName: String, context: Context)
		{
			val favorites = getFavorites(context)
			favorites.remove(processName)

			val prefs = PreferenceManager.getDefaultSharedPreferences(context)
			prefs.edit().putStringSet(KEY_FAVORITES,favorites).commit()
		}

		fun getFavorites(context: Context): MutableSet<String>
		{
			val prefs = PreferenceManager.getDefaultSharedPreferences(context)
			return prefs.getStringSet(KEY_FAVORITES, mutableSetOf())
		}

		fun isFavorite(processName: String, context: Context): Boolean
		{
			return getFavorites(context).contains(processName)
		}

		fun addHidden(processName: String, context: Context)
		{
			val hidden = getHidden(context)

			if (!hidden.contains(processName))
			{
				hidden.add(processName)
				val prefs = PreferenceManager.getDefaultSharedPreferences(context)
				prefs.edit().putStringSet(KEY_HIDDEN,hidden).commit()
			}
		}

		fun clearHidden(context: Context)
		{
			val prefs = PreferenceManager.getDefaultSharedPreferences(context)
			prefs.edit().putStringSet(KEY_HIDDEN, mutableSetOf()).commit()
		}

		fun removeHidden(processName: String, context: Context)
		{
			val hidden = getHidden(context)
			hidden.remove(processName)

			val prefs = PreferenceManager.getDefaultSharedPreferences(context)
			prefs.edit().putStringSet(KEY_HIDDEN,hidden).commit()
		}

		fun getHidden(context: Context): MutableSet<String>
		{
			val prefs = PreferenceManager.getDefaultSharedPreferences(context)
			return prefs.getStringSet(KEY_HIDDEN, mutableSetOf())
		}

		fun isHidden(processName: String, context: Context): Boolean
		{
			return getHidden(context).contains(processName)
		}
	}
}