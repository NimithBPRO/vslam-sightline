/*
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License. and you can’t re-distribute this file without a prior notice
For license details, visit www.multiset.ai.
Redistribution in source or binary forms must retain this notice.
*/
package com.multiset.sdk.android.ar

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.ux.ArFragment
import com.multiset.sdk.android.camera.ImageProcessor
import com.multiset.sdk.android.config.SDKConfig
import com.multiset.sdk.android.databinding.ActivityArBinding
import com.multiset.sdk.android.network.LocalizationResponse
import com.multiset.sdk.android.network.NetworkManager
import com.multiset.sdk.android.utils.Util.Companion.createMatrixFromQuaternion
import com.multiset.sdk.android.utils.Util.Companion.createTransformMatrix
import com.multiset.sdk.android.utils.Util.Companion.extractPosition
import com.multiset.sdk.android.utils.Util.Companion.extractRotation
import com.multiset.sdk.android.utils.Util.Companion.invertMatrix
import com.multiset.sdk.android.utils.Util.Companion.multiplyMatrices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ARActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArBinding
    private lateinit var arFragment: ArFragment
    private val viewModel: ARViewModel by viewModels()
    private var authToken: String? = null
    private var gizmoNode: GizmoNode? = null
    private var isLocalizing = false

    private val networkManager by lazy { NetworkManager() }
    private val imageProcessor by lazy { ImageProcessor() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authToken = intent.getStringExtra("AUTH_TOKEN")

        if (authToken == null) {
            showToast("Auth token is required")
            finish()
            return
        }

        setupAR()
        setupUI()
        observeViewModel()

        // Update gizmo position at origin
        updateGizmoPosition(Vector3.zero(), Quaternion.identity())

    }

    private fun setupAR() {
        val fragment = supportFragmentManager.findFragmentById(binding.arFragment.id)
        if (fragment is ArFragment) {
            arFragment = fragment
            // Wait until the fragment's view is created
            arFragment.viewLifecycleOwnerLiveData.observe(this) { owner ->
                if (owner != null && arFragment.arSceneView != null) {
                    arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
                        onSceneUpdate(frameTime)
                    }
                    arFragment.setOnSessionInitializationListener { session ->
                        configureSession(session)
                        addGizmoToScene()
                    }
                }
            }
        }
    }


    private fun configureSession(session: Session) {
        val config = Config(session).apply {
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            focusMode = Config.FocusMode.AUTO
            lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY

            depthMode = Config.DepthMode.AUTOMATIC
        }
        session.configure(config)
    }

    private fun setupUI() {
        binding.localizeButton.setOnClickListener {
            if (!isLocalizing) {
                startLocalization()
            }
        }

        binding.resetButton.setOnClickListener {
            resetWorldOrigin()
        }

        binding.closeButton.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.trackingState.observe(this) { state ->
            binding.statusText.text = state
        }

        viewModel.localizationResult.observe(this) { result ->
            result?.let {
                handleLocalizationResult(it)
            }
        }
    }

    private fun onSceneUpdate(frameTime: FrameTime) {
        val frame = arFragment.arSceneView.arFrame ?: return

        if (frame.camera.trackingState != TrackingState.TRACKING) {
            return
        }

        // Update tracking state
        when (frame.camera.trackingState) {
            TrackingState.TRACKING -> {
                viewModel.updateTrackingState("Tracking Normal")
                binding.localizeButton.isEnabled = true
            }

            TrackingState.PAUSED -> {
                viewModel.updateTrackingState("Tracking Paused")
                binding.localizeButton.isEnabled = false
            }

            TrackingState.STOPPED -> {
                viewModel.updateTrackingState("Tracking Stopped")
                binding.localizeButton.isEnabled = false
            }
        }
    }

    private fun startLocalization() {
        val frame = arFragment.arSceneView.arFrame ?: return

        if (frame.camera.trackingState != TrackingState.TRACKING) {
            showToast("Camera tracking not ready")
            return
        }

        isLocalizing = true
        binding.progressBar.visibility = View.VISIBLE
        binding.localizationStatus.text = "Localizing..."

        lifecycleScope.launch {
            try {
                val cameraPose = getCurrentCameraPose(frame)
                val bitmap = captureFrameImage(frame)

                if (bitmap != null && authToken != null) {
                    sendLocalizationRequest(bitmap, cameraPose)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Localization error: ${e.message}")
                    binding.progressBar.visibility = View.GONE
                    isLocalizing = false
                }
            }
        }
    }

    private fun getCurrentCameraPose(frame: Frame): CameraPose {
        val pose = frame.camera.pose
        val position = Vector3(pose.tx(), pose.ty(), pose.tz())
        var rotation = Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw())

        // Apply orientation correction to match Unity ARFoundation behavior
        // Unity ARFoundation always reports camera rotation relative to landscape orientation
        if (!isLandscapeOrientation()) {
            // In portrait mode, we need to remove the extra 90-degree rotation
            // Create a quaternion for +90 degrees around Z axis (to counteract the -90)
            val orientationCorrection = Quaternion.axisAngle(Vector3(0f, 0f, 1f), 90f)

            // Apply the correction to make portrait rotation match landscape
            rotation = Quaternion.multiply(rotation, orientationCorrection)
        }

        // Validate position magnitude
        val positionMagnitude = position.length()
        if (positionMagnitude < 1e-6f) {
            Log.w(
                "Multiset_LOG >>",
                "Camera position is too close to origin, AR might not be initialized yet"
            )
        }

        return CameraPose(position, rotation)
    }

    private suspend fun captureFrameImage(frame: Frame): Bitmap? =
        withContext(Dispatchers.Default) {
            try {
                val image = frame.acquireCameraImage()
                val rawBitmap = imageProcessor.imageToRgbBitmap(image) // Convert Image to Bitmap
                image.close()

                // Rotate if in portrait mode
                val finalBitmap = if (!isLandscapeOrientation()) {
                    rotateBitmap(rawBitmap, 90f)
                } else {
                    rawBitmap
                }

                finalBitmap
            } catch (e: Exception) {
                null
            }
        }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private suspend fun sendLocalizationRequest(bitmap: Bitmap, cameraPose: CameraPose) {
        if (authToken == null) {
            showToast("Auth token is required")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                // Resize and process image - NOW PASSING CONTEXT
                val processedData = imageProcessor.processImageForLocalization(
                    bitmap,
                    arFragment.arSceneView.arFrame!!,
                    this@ARActivity  // Pass the context here
                )

                // Prepare parameters
                val parameters = mutableMapOf(
                    "isRightHanded" to "true",
                    "fx" to processedData.fx.toString(),
                    "fy" to processedData.fy.toString(),
                    "px" to processedData.px.toString(),
                    "py" to processedData.py.toString(),
                    "width" to processedData.width.toString(),
                    "height" to processedData.height.toString()
                )

                // Add map code based on type
                when (SDKConfig.getActiveMapType()) {
                    SDKConfig.MapType.MAP -> parameters["mapCode"] = SDKConfig.MAP_CODE
                    SDKConfig.MapType.MAP_SET -> parameters["mapSetCode"] = SDKConfig.MAP_SET_CODE
                }

                // Convert bitmap to JPEG
                val outputStream = ByteArrayOutputStream()
                processedData.bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                val imageData = outputStream.toByteArray()

                // Send request
                val response = networkManager.sendLocalizationRequest(
                    authToken!!,
                    parameters,
                    imageData
                )

                withContext(Dispatchers.Main) {
                    viewModel.setLocalizationResult(response, cameraPose)
                    binding.progressBar.visibility = View.GONE
                    isLocalizing = false
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Localization Failed!")
                    binding.progressBar.visibility = View.GONE
                    isLocalizing = false
                }
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun handleLocalizationResult(result: LocalizationResult) {

        if (result.response.poseFound) {
            binding.localizationStatus.text = "Localization Success"

            // Process the pose using the same logic as Swift
            val processedPose = poseHandler(result.response, result.cameraPose)

            // Update gizmo position with processed pose
            updateGizmoPosition(processedPose.position, processedPose.rotation)

            showToast("Localization successful!")
        } else {
            binding.localizationStatus.text = "Localization failed"
            showToast("Pose not found")
        }
    }

    private fun addGizmoToScene() {
        val scene = arFragment.arSceneView.scene
        gizmoNode = GizmoNode(this) // Pass context
        scene.addChild(gizmoNode)

        // Show gizmo at origin initially
        gizmoNode?.show()
    }

    private fun updateGizmoPosition(position: Vector3, rotation: Quaternion) {
        gizmoNode?.let { node ->
            node.localPosition = position
            node.localRotation = rotation
            node.show() // Make sure it's visible
        } ?: run {
            Log.e("Multiset_LOG >>", "Gizmo node is null!")
        }
    }

    private fun resetWorldOrigin() {
        val session = arFragment.arSceneView.session ?: return
        session.pause()

        val config = Config(session)
        session.configure(config)
        session.resume()

        gizmoNode?.let {
            it.localPosition = Vector3.zero()
            it.localRotation = Quaternion.identity()
        }

        showToast("World origin reset")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    data class CameraPose(
        val position: Vector3,
        val rotation: Quaternion
    )

    data class LocalizationResult(
        val response: LocalizationResponse,
        val cameraPose: CameraPose,
        val resultPose: ResultPose
    )

    data class ResultPose(
        val position: Vector3,
        val rotation: Quaternion
    )

    // Add this new method that mirrors your Swift poseHandler
    private fun poseHandler(
        localizationResponse: LocalizationResponse,
        cameraPose: CameraPose
    ): ResultPose {
        // Parse the response data for position and rotation
        val resPosition = Vector3(
            localizationResponse.position.x,
            localizationResponse.position.y,
            localizationResponse.position.z
        )

        val resRotation = Quaternion(
            localizationResponse.rotation.x,
            localizationResponse.rotation.y,
            localizationResponse.rotation.z,
            localizationResponse.rotation.w
        )

        // Create rotation matrix from quaternion
        val rotationMatrix = createMatrixFromQuaternion(resRotation)

        // Create negated response matrix (translation included)
        val negatedResponseMatrix = createTransformMatrix(rotationMatrix, resPosition)

        // Invert the negated response matrix
        val invNegatedResponseMatrix = invertMatrix(negatedResponseMatrix)

        // Create the tracker space matrix (from camera position and rotation)
        val trackerSpaceMatrix = createTransformMatrix(
            createMatrixFromQuaternion(cameraPose.rotation),
            cameraPose.position
        )

        // Calculate the resultant matrix
        val resultantMatrix = multiplyMatrices(trackerSpaceMatrix, invNegatedResponseMatrix)

        // Decompose the resultant matrix into position and rotation
        val resultPosition = extractPosition(resultantMatrix)
        val resultRotationRaw = extractRotation(resultantMatrix)

        return ResultPose(resultPosition, resultRotationRaw)
    }


    // Helper method to check device orientation
    private fun isLandscapeOrientation(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }


}