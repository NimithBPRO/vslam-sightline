/*
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License. and you can’t re-distribute this file without a prior notice
For license details, visit www.multiset.ai.
Redistribution in source or binary forms must retain this notice.
*/
package com.multiset.sdk.android.config

object SDKConfig {
    // API URLs
    const val SDK_AUTH_URL = "https://api.multiset.ai/v1/m2m/token"
    const val QUERY_URL = "https://api.multiset.ai/v1/vps/map/query-form"

    // Map Configuration
    const val MAP_CODE = "MAP_JWIVI82HW9HV" // Enter your mapCode here
    const val MAP_SET_CODE = "" // Enter your mapSetCode here

    // Credentials
    const val CLIENT_ID = "f67b6749-bf5b-42b5-b8a2-5ec3836503d4"
    const val CLIENT_SECRET = "3fd6c23ddcdcd6d58f3db3e3688c55a2fb587439a3f1d9f66ae90eb85a9df3a9"

    // To get ClientId and ClientSecret go to https://developer.multiset.ai/credentials

    enum class MapType {
        MAP, MAP_SET
    }

    fun getActiveMapType(): MapType {
        return if (MAP_CODE.isNotEmpty()) MapType.MAP else MapType.MAP_SET
    }
}