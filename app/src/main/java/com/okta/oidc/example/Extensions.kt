/*
* Created by Mikel Pascual (mikel@4rtstudio.com) on 25/11/2019.
*/
package com.okta.oidc.example

import android.app.KeyguardManager
import android.content.Context
import android.os.Build

/**
 * Check if device have enabled keyguard.
 *
 * @return the boolean
 */
fun Context.isKeyguardSecure(): Boolean {
    return (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardSecure == true
}

/**
 * Check if the device is a emulator.
 *
 * @return true if it is emulator
 */
val isEmulator: Boolean
    get() = (Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Google")
            || Build.PRODUCT.contains("sdk_gphone")
            || Build.DEVICE.contains("generic"))