package com.hoangbinhan.snowscreen.billing

import android.content.Context
import android.content.SharedPreferences

class ProPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "snow_screen_pro_prefs"
        private const val KEY_PRO_UNLOCKED = "key_pro_unlocked"
    }

    var isProUnlocked: Boolean
        get() = prefs.getBoolean(KEY_PRO_UNLOCKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PRO_UNLOCKED, value).apply()
        }
}
