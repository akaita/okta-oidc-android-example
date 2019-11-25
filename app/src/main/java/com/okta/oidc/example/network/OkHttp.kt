package com.okta.oidc.example.network

import android.net.Uri
import androidx.annotation.WorkerThread
import com.okta.oidc.net.ConnectionParameters
import com.okta.oidc.net.OktaHttpClient
import okhttp3.*
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A OktaHttpClient implementation using OkHttpClient.
 */
open class OkHttp : OktaHttpClient {
    /**
     * The Call.
     */
    @Volatile
    protected var mCall: Call? = null
    /**
     * The response.
     */
    protected var mResponse: Response? = null
    /**
     * The exception.
     */
    protected var mException: Exception? = null

    /**
     * Build request request.
     *
     * @param uri   the uri
     * @param param the param
     * @return the request
     */
    protected fun buildRequest(uri: Uri, param: ConnectionParameters): Request {
        if (sOkHttpClient == null) {
            sOkHttpClient = OkHttpClient.Builder()
                .readTimeout(param.readTimeOutMs().toLong(), TimeUnit.MILLISECONDS)
                .connectTimeout(param.connectionTimeoutMs().toLong(), TimeUnit.MILLISECONDS)
                .build()
        }
        var requestBuilder = Request.Builder().url(uri.toString())
        for ((key, value) in param.requestProperties()) {
            requestBuilder.addHeader(key, value)
        }
        if (param.requestMethod() == ConnectionParameters.RequestMethod.GET) {
            requestBuilder = requestBuilder.get()
        } else {
            val postParameters = param.postParameters()
            if (postParameters != null) {
                val formBuilder = FormBody.Builder()
                for ((key, value) in postParameters) {
                    formBuilder.add(key, value)
                }
                val formBody: RequestBody = formBuilder.build()
                requestBuilder.post(formBody)
            } else {
                requestBuilder.post(RequestBody.create(null, ""))
            }
        }
        return requestBuilder.build()
    }

    @WorkerThread
    @Throws(Exception::class)
    override fun connect(uri: Uri, param: ConnectionParameters): InputStream? {
        val request = buildRequest(uri, param)
        mCall = sOkHttpClient?.newCall(request)
        val latch = CountDownLatch(1)
        mCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mException = e
                latch.countDown()
            }

            override fun onResponse(call: Call, response: Response) {
                mResponse = response
                latch.countDown()
            }
        })
        latch.await()
        mException?.let { exception ->
            throw exception
        }
        return mResponse?.body()?.byteStream()
    }

    override fun cleanUp() {
        // Do nothing
    }

    override fun cancel() {
        mCall?.cancel()
    }

    override fun getHeaderFields(): Map<String, List<String>>? {
        return mResponse?.headers()?.toMultimap()
    }

    override fun getHeader(header: String): String? {
        return mResponse?.header(header)
    }

    @Throws(IOException::class)
    override fun getResponseCode(): Int {
        return mResponse?.code() ?: -1
    }

    override fun getContentLength(): Int {
        return mResponse?.body()?.contentLength()?.toInt() ?: -1
    }

    @Throws(IOException::class)
    override fun getResponseMessage(): String? {
        return mResponse?.message()
    }

    companion object {
        /**
         * The constant sOkHttpClient.
         */
        var sOkHttpClient: OkHttpClient? = null
    }
}