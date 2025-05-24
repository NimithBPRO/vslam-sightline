/*
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License. and you can’t re-distribute this file without a prior notice
For license details, visit www.multiset.ai.
Redistribution in source or binary forms must retain this notice.
*/

package com.multiset.sdk.android.ar

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.multiset.sdk.android.network.LocalizationResponse

class ARViewModel : ViewModel() {

    private val _trackingState = MutableLiveData<String>()
    val trackingState: LiveData<String> = _trackingState

    private val _localizationResult = MutableLiveData<ARActivity.LocalizationResult?>()
    val localizationResult: LiveData<ARActivity.LocalizationResult?> = _localizationResult

    fun updateTrackingState(state: String) {
        _trackingState.value = state
    }

    fun setLocalizationResult(
        response: LocalizationResponse,
        cameraPose: ARActivity.CameraPose
    ) {
        val resultPose = calculateResultPose(response, cameraPose)
        _localizationResult.value = ARActivity.LocalizationResult(
            response = response,
            cameraPose = cameraPose,
            resultPose = resultPose
        )
    }

    private fun calculateResultPose(
        response: LocalizationResponse,
        cameraPose: ARActivity.CameraPose
    ): ARActivity.ResultPose {
        // Convert response position and rotation
        val resPosition = Vector3(
            response.position.x,
            response.position.y,
            response.position.z
        )

        val resRotation = Quaternion(
            response.rotation.x,
            response.rotation.y,
            response.rotation.z,
            response.rotation.w
        )
        // This is a simplified calculation - adjust as needed
        val resultPosition = Vector3.add(cameraPose.position, resPosition)

        return ARActivity.ResultPose(
            position = resultPosition,
            rotation = resRotation
        )
    }
}