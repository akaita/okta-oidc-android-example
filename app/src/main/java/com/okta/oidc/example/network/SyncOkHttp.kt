package com.okta.oidc.example.network

import android.net.Uri
import com.okta.oidc.net.ConnectionParameters
import java.io.InputStream

/**
 * An OktaHttpClient implementation using OkHttpClient. This will use synchronous call.
 */
class SyncOkHttp : OkHttp() {
    @Throws(Exception::class)
    override fun connect(uri: Uri, param: ConnectionParameters): InputStream? {
        val request = buildRequest(uri, param)
        mCall = sOkHttpClient?.newCall(request)
        mResponse = mCall?.execute()
        return mResponse?.body()?.byteStream()
    }
}
