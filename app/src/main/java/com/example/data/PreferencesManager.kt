package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vote_counting_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_WARD_ID = "last_selected_ward_id"
    }

    fun getLastSelectedWardId(): Long {
        return prefs.getLong(KEY_LAST_WARD_ID, -1L)
    }

    fun setLastSelectedWardId(wardId: Long) {
        prefs.edit().putLong(KEY_LAST_WARD_ID, wardId).apply()
    }
}
