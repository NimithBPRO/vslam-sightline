/*
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License. and you can’t re-distribute this file without a prior notice
For license details, visit www.multiset.ai.
Redistribution in source or binary forms must retain this notice.
*/
package com.multiset.sdk.android.network

import com.google.gson.Gson
import com.multiset.sdk.android.config.SDKConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class NetworkManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun sendLocalizationRequest(
        token: String,
        parameters: Map<String, String>,
        imageData: ByteArray
    ): LocalizationResponse = withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            val requestBody = createMultipartBody(parameters, imageData)

            val request = Request.Builder()
                .url(SDKConfig.QUERY_URL)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            val localizationResponse = gson.fromJson(
                                responseBody,
                                LocalizationResponse::class.java
                            )
                            continuation.resume(localizationResponse)
                        } else {
                            continuation.resumeWithException(
                                Exception("Localization failed: ${response.code}")
                            )
                        }
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    private fun createMultipartBody(
        parameters: Map<String, String>,
        imageData: ByteArray
    ): RequestBody {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)

        // Add parameters
        parameters.forEach { (key, value) ->
            builder.addFormDataPart(key, value)
        }

        // Add image
        builder.addFormDataPart(
            "queryImage",
            "frame.jpg",
            imageData.toRequestBody("image/jpeg".toMediaType())
        )

        return builder.build()
    }
}
