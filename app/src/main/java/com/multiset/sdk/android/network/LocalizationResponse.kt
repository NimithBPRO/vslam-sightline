/*
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License. and you can’t re-distribute this file without a prior notice
For license details, visit www.multiset.ai.
Redistribution in source or binary forms must retain this notice.
*/
package com.multiset.sdk.android.network

data class LocalizationResponse(
    val poseFound: Boolean,
    val position: Position,
    val rotation: Rotation,
    val confidence: Float,
    val mapIds: List<String>
) {
    data class Position(
        val x: Float,
        val y: Float,
        val z: Float
    )

    data class Rotation(
        val x: Float,
        val y: Float,
        val z: Float,
        val w: Float
    )
}