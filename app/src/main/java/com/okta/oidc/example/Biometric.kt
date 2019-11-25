/*
* Created by Mikel Pascual (mikel@4rtstudio.com) on 25/11/2019.
*/
package com.okta.oidc.example

import androidx.biometric.BiometricConstants.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executors


class Biometric(
    fragmentActivity: FragmentActivity,
    onSuccessListener: () -> Unit,
    onCancelListener: () -> Unit,
    onErrorListener: (Int, String) -> Unit
) {
    private val mCallback: BiometricPrompt.AuthenticationCallback =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == ERROR_NEGATIVE_BUTTON) {
                    onCancelListener()
                } else {
                    onErrorListener(errorCode, errString.toString())
                }
                prompt.cancelAuthentication()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccessListener()
            }
        }

    private val prompt: BiometricPrompt
    private val promptInfo: PromptInfo

    init {
        prompt = BiometricPrompt(fragmentActivity, Executors.newSingleThreadExecutor(), mCallback)
        promptInfo = PromptInfo.Builder()
            .setTitle(fragmentActivity.getString(R.string.confirm_credentials))
            .setDeviceCredentialAllowed(true)
            .setConfirmationRequired(true)
            .build()
    }

    fun show() {
        prompt.authenticate(promptInfo)
    }
}