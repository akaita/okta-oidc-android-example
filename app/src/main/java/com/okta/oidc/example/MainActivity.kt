package com.okta.oidc.example

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.okta.oidc.*
import com.okta.oidc.Okta.WebAuthBuilder
import com.okta.oidc.clients.BaseAuth
import com.okta.oidc.clients.sessions.SessionClient
import com.okta.oidc.clients.web.WebAuthClient
import com.okta.oidc.example.network.MyConnectionFactory
import com.okta.oidc.net.response.UserInfo
import com.okta.oidc.storage.security.DefaultEncryptionManager
import com.okta.oidc.storage.security.EncryptionManager
import com.okta.oidc.storage.security.GuardedEncryptionManager
import com.okta.oidc.util.AuthorizationException
import kotlinx.android.synthetic.main.main_activity.*

class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"

    /**
     * Authorization client using chrome custom tab as a user agent.
     */
    private lateinit var webAuth: WebAuthClient
    /**
     * The authorized client to interact with Okta's endpoints.
     */
    private lateinit var sessionClient: SessionClient
    /**
     * Okta OIDC configuration.
     */
    private val oidcConfig = OIDCConfig.Builder()
        .clientId(BuildConfig.CLIENT_ID)
        .redirectUri(BuildConfig.REDIRECT_URI)
        .endSessionRedirectUri(BuildConfig.END_SESSION_URI)
        .scopes(*BuildConfig.SCOPES)
        .discoveryUri(BuildConfig.DISCOVERY_URI)
        .create()

    private lateinit var preferences: Preferences
    private lateinit var defaultEncryptionManager: EncryptionManager
    private var currentEncryptionManager: EncryptionManager? = null
    private var keyguardEncryptionManager: GuardedEncryptionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        preferences = Preferences(this)
        biometric.isChecked = preferences.biometricsEnabled

        setupUiListeners()
        setupEncryptionManager()
        setupWebAuth(currentEncryptionManager!!)
        setupWebAuthCallback(webAuth)
    }

    private fun setupEncryptionManager() {
        try {
            keyguardEncryptionManager = GuardedEncryptionManager(this, Int.MAX_VALUE)
        } catch (exception: IllegalStateException) {
            Log.e(tag, "Error initializing keyguardEncryptionManager. Keyguard is probably disabled")
        }
        defaultEncryptionManager = DefaultEncryptionManager(this)
        currentEncryptionManager =
            if (preferences.biometricsEnabled) keyguardEncryptionManager else defaultEncryptionManager
    }

    private fun setupWebAuth(encryptionManager: EncryptionManager) {
        //use custom connection factory
        val factory = MyConnectionFactory().apply {
            clientType = MyConnectionFactory.USE_SYNC_OK_HTTP
        }
        webAuth = WebAuthBuilder()
            .withConfig(oidcConfig)
            .withContext(applicationContext)
            .withCallbackExecutor(null)
            .withEncryptionManager(encryptionManager)
            .setRequireHardwareBackedKeyStore(isEmulator.not())
            .withTabColor(0)
            .withOktaHttpClient(factory.build())
            .create()
        sessionClient = webAuth.sessionClient
    }

    private fun setupWebAuthCallback(webAuth: WebAuthClient) {
        val callback: ResultCallback<AuthorizationStatus, AuthorizationException?> =
            object : ResultCallback<AuthorizationStatus, AuthorizationException?> {
                override fun onSuccess(status: AuthorizationStatus) {
                    Log.d(tag, "AUTHORIZED")
                    if (status == AuthorizationStatus.AUTHORIZED) {
                        tvStatus.text = getString(R.string.authentication_authorized)
                        showAuthenticatedMode()
                        showNetworkProgress(false)
                        progressBar.visibility = View.GONE
                    } else if (status == AuthorizationStatus.SIGNED_OUT) { //this only clears the session.
                        tvStatus.text = getString(R.string.signed_out_of_okta)
                        showNetworkProgress(false)
                        if (webAuth.sessionClient.isAuthenticated.not()) {
                            showSignedOutMode()
                        }
                    }
                }

                override fun onCancel() {
                    progressBar.visibility = View.GONE
                    Log.d(tag, "CANCELED!")
                    tvStatus.text = getString(R.string.canceled)
                }

                override fun onError(msg: String?, error: AuthorizationException?) {
                    progressBar.visibility = View.GONE
                    Log.d(tag, "${error?.error} onError", error)
                    tvStatus.text = msg
                }
            }
        webAuth.registerCallback(callback, this)
    }

    private fun setupUiListeners() {
        signIn.setOnClickListener {
            showNetworkProgress(true)
            val payload = AuthenticationPayload.Builder()
                .build()
            webAuth.signIn(this, payload)
        }
        signInSocial.setOnClickListener {
            showNetworkProgress(true)
            val payload = AuthenticationPayload.Builder()
                .setIdp(BuildConfig.IDP)
                .setIdpScope(*BuildConfig.IDP_SCOPE)
                .build()
            webAuth.signIn(this, payload)
        }
        biometric.setOnCheckedChangeListener { button: CompoundButton, isChecked: Boolean ->
            preferences.biometricsEnabled = isChecked
            when {
                isChecked && isKeyguardSecure().not() -> {
                    button.isChecked = false
                    tvStatus.text = getString(R.string.keyguard_unsafe)
                    preferences.biometricsEnabled = false
                }
                isChecked -> {
                    if (keyguardEncryptionManager == null) {
                        keyguardEncryptionManager = GuardedEncryptionManager(this, Int.MAX_VALUE)
                    }
                    try {
                        if (keyguardEncryptionManager?.isValidKeys?.not() == true) {
                            keyguardEncryptionManager?.recreateKeys(this)
                        }
                        keyguardEncryptionManager?.recreateCipher()
                        sessionClient.migrateTo(keyguardEncryptionManager)
                        currentEncryptionManager = keyguardEncryptionManager
                    } catch (e: AuthorizationException) {
                        tvStatus.text = getString(R.string.error_data_migration)
                        Log.d(tag, "Error migrateTo", e)
                    }
                }
                else -> {
                    currentEncryptionManager?.removeKeys()
                    sessionClient.clear()
                    currentEncryptionManager = defaultEncryptionManager
                    try { //set the encryption manager back to default.
                        sessionClient.migrateTo(currentEncryptionManager)
                    } catch (e: AuthorizationException) { //NO-OP
                    }
                    showSignedOutMode()
                }
            }
        }
        getProfile.setOnClickListener {
            downloadProfile()
        }
        refreshToken.setOnClickListener {
            showNetworkProgress(true)
            sessionClient.refreshToken(object : RequestCallback<Tokens, AuthorizationException?> {
                override fun onSuccess(result: Tokens) {
                    tvStatus.text = getString(R.string.token_refreshed)
                    showNetworkProgress(false)
                }

                override fun onError(error: String?, exception: AuthorizationException?) {
                    tvStatus.text = exception?.errorDescription
                    showNetworkProgress(false)
                }
            })
        }
        signOutOfOkta.setOnClickListener {
            webAuth.signOutOfOkta(this)
        }
        signOutAll.setOnClickListener {
            showNetworkProgress(true)
            webAuth.signOut(this, object : RequestCallback<Int, AuthorizationException?> {
                override fun onSuccess(result: Int) {
                    showNetworkProgress(false)
                    tvStatus.text = ""
                    if (result == BaseAuth.SUCCESS) {
                        tvStatus.text = getString(R.string.signed_out_all)
                        showSignedOutMode()
                    }
                    if (result and BaseAuth.FAILED_CLEAR_SESSION == BaseAuth.FAILED_CLEAR_SESSION) {
                        tvStatus.append("FAILED_CLEAR_SESSION\n")
                    }
                    if (result and BaseAuth.FAILED_REVOKE_ACCESS_TOKEN == BaseAuth.FAILED_REVOKE_ACCESS_TOKEN) {
                        tvStatus.append("FAILED_REVOKE_ACCESS_TOKEN\n")
                    }
                    if (result and BaseAuth.FAILED_REVOKE_REFRESH_TOKEN == BaseAuth.FAILED_REVOKE_REFRESH_TOKEN) {
                        tvStatus.append("FAILED_REVOKE_REFRESH_TOKEN\n")
                    }
                    if (result and BaseAuth.FAILED_CLEAR_DATA == BaseAuth.FAILED_CLEAR_DATA) {
                        tvStatus.append("FAILED_CLEAR_DATA\n")
                    }
                }

                override fun onError(msg: String?, exception: AuthorizationException?) {
                    // Do nothing
                }
            })
        }
        clearData.setOnClickListener {
            sessionClient.clear()
            tvStatus.text = getString(R.string.clear_data)
            showSignedOutMode()
        }
        cancel.setOnClickListener {
            webAuth.cancel()
            sessionClient.cancel()
            showNetworkProgress(false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (sessionClient.isAuthenticated) {
            showAuthenticatedMode()
        }
        if (webAuth.isInProgress) {
            showNetworkProgress(true)
        }
        if (biometric.isChecked && currentEncryptionManager?.isUserAuthenticatedOnDevice?.not() == true) {
            showKeyguard()
        }
    }

    override fun onStop() {
        super.onStop()
        showNetworkProgress(false)
    }

    private fun showNetworkProgress(visible: Boolean) {
        progressBar.visibility = if (visible) View.VISIBLE else View.GONE
        cancel.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showAuthenticatedMode() {
        getProfile.visibility = View.VISIBLE
        signOutOfOkta.visibility = View.VISIBLE
        signOutAll.visibility = View.VISIBLE
        clearData.visibility = View.VISIBLE
        refreshToken.visibility = View.VISIBLE
        signIn.visibility = View.GONE
        signInSocial.visibility = View.GONE
    }

    private fun showSignedOutMode() {
        signIn.visibility = View.VISIBLE
        signInSocial.visibility = View.VISIBLE
        getProfile.visibility = View.GONE
        signOutOfOkta.visibility = View.GONE
        signOutAll.visibility = View.GONE
        refreshToken.visibility = View.GONE
        clearData.visibility = View.GONE
        tvStatus.text = ""
    }

    private fun downloadProfile() {
        showNetworkProgress(true)
        sessionClient.getUserProfile(object : RequestCallback<UserInfo, AuthorizationException?> {
            override fun onSuccess(result: UserInfo) {
                tvStatus.text = result.toString()
                showNetworkProgress(false)
            }

            override fun onError(error: String?, exception: AuthorizationException?) {
                Log.d(tag, error, exception?.cause)
                tvStatus.text = getString(R.string.error_template).format(exception?.errorDescription)
                showNetworkProgress(false)
            }
        })
    }

    private fun showKeyguard() {
        Biometric(
            fragmentActivity = this,
            onSuccessListener = {
                if (currentEncryptionManager?.cipher == null) {
                    currentEncryptionManager?.recreateCipher()
                }
            },
            onCancelListener = {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.biometric_cancel), Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            onErrorListener = { code, message ->
                runOnUiThread {
                    Toast.makeText(
                        this, getString(R.string.error_template).format("[$code] $message"), Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }).show()
    }
}