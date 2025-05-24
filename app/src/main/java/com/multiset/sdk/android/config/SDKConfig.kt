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
    const val MAP_CODE = "" // Enter your mapCode here
    const val MAP_SET_CODE = "" // Enter your mapSetCode here

    // Credentials
    const val CLIENT_ID = ""
    const val CLIENT_SECRET = ""

    // To get ClientId and ClientSecret go to https://developer.multiset.ai/credentials

    enum class MapType {
        MAP, MAP_SET
    }

    fun getActiveMapType(): MapType {
        return if (MAP_CODE.isNotEmpty()) MapType.MAP else MapType.MAP_SET
    }
}