package com.okta.oidc.example.network

import com.okta.oidc.net.OktaHttpClient

/**
 * Simple connection factory.
 */
class MyConnectionFactory {
    var clientType = 0

    /**
     * Build okta http client.
     *
     * @return the okta http client
     */
    fun build(): OktaHttpClient? {
        return when (clientType) {
            USE_OK_HTTP -> OkHttp()
            USE_SYNC_OK_HTTP -> SyncOkHttp()
            else -> null //sdk will use default implementation if null is provided.
        }
    }

    companion object {
        const val USE_OK_HTTP = 1
        const val USE_SYNC_OK_HTTP = 2
    }
}
