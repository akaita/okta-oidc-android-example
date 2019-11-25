/*
* Created by Mikel Pascual (mikel@4rtstudio.com) on 25/11/2019.
*/
package com.okta.oidc.example

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Preferences(context: Context) {

    private val sharedPreferences: SharedPreferences

    var biometricsEnabled
        get() = sharedPreferences.getBoolean(PREF_FINGERPRINT, false)
        set(value) {
            sharedPreferences.edit { putBoolean(PREF_FINGERPRINT, value) }
        }

    init {
        sharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val FILE_NAME = "okta_prefs"
        private const val PREF_FINGERPRINT = "fingerprint"
    }
}