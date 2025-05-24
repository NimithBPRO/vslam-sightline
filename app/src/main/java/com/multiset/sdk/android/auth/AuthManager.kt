/*
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License. and you can’t re-distribute this file without a prior notice
For license details, visit www.multiset.ai.
Redistribution in source or binary forms must retain this notice.
*/
package com.multiset.sdk.android.auth

import android.util.Base64
import android.util.Log
import com.multiset.sdk.android.config.SDKConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AuthManager {
    private var token: String? = null
    private val client = OkHttpClient()

    suspend fun authenticate(): Result<String> = withContext(Dispatchers.IO) {

        suspendCoroutine { continuation ->
            val authString = "${SDKConfig.CLIENT_ID}:${SDKConfig.CLIENT_SECRET}"
            val authData = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)

            val request = Request.Builder()
                .url(SDKConfig.SDK_AUTH_URL)
                .addHeader("Authorization", "Basic $authData")
                .post(RequestBody.create(null, ByteArray(0)))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            val json = JSONObject(responseBody ?: "{}")
                            token = json.getString("token")
                            continuation.resume(Result.success(token!!))
                        } else {
                            continuation.resume(
                                Result.failure(Exception("Please confirm your credentials!"))
                            )
                        }
                    } catch (e: Exception) {
                        continuation.resume(Result.failure(e))
                    }
                }
            })
        }
    }

    fun getToken(): String? = token
    fun isAuthenticated(): Boolean = token != null
}

