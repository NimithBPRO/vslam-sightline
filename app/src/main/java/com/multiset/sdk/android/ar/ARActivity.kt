/*
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License.
You may not re-distribute this file without prior notice.
For license details, visit www.multiset.ai.
*/
package com.multiset.sdk.android.ar

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ARActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArBinding
    private lateinit var arFragment: ArFragment
    private val viewModel: ARViewModel by viewModels()
    private var authToken: String? = null
    private var gizmoNode: GizmoNode? = null
    private var isLocalizing = false
    private var esp32StreamUrl: String? = null
    private val networkManager by lazy { NetworkManager() }
    private val imageProcessor by lazy { ImageProcessor() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authToken = intent.getStringExtra("AUTH_TOKEN")
        esp32StreamUrl = intent.getStringExtra("ESP32_STREAM_URL")

        if (authToken == null || esp32StreamUrl.isNullOrEmpty()) {
            showToast("Auth token and ESP32 stream URL required")
            finish()
            return
        }

        // Initialize AR & observers
        setupAR()
        observeViewModel()
        updateGizmoPosition(Vector3.zero(), Quaternion.identity())

        // Setup ESP32 streaming UI and localization flow
        setupESP32Mode()
    }

    /** ---------------- ARCore Setup ---------------- **/
    private fun setupAR() {
        val fragment = supportFragmentManager.findFragmentById(binding.arFragment.id)
        if (fragment is ArFragment) {
            arFragment = fragment

            // When fragment's view lifecycle owner is ready, attach update listener
            arFragment.viewLifecycleOwnerLiveData.observe(this) { owner ->
                if (owner != null && arFragment.arSceneView != null) {
                    arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
                        onSceneUpdate(frameTime)
                    }
                    // When session is created, configure and add the gizmo
                    arFragment.setOnSessionInitializationListener { session ->
                        configureSession(session)
                        addGizmoToScene()
                    }
                }
            }
        } else {
            Log.e("ARActivity", "AR fragment not found or wrong ID in layout")
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

    /** ---------------- ESP32 Streaming + Localization ---------------- **/
    private fun setupESP32Mode() {
        binding.esp32WebView.settings.javaScriptEnabled = true
        binding.esp32WebView.settings.useWideViewPort = true
        binding.esp32WebView.settings.loadWithOverviewMode = true
        binding.esp32WebView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE

        startStream()

        binding.localizeButton.setOnClickListener {
            if (!isLocalizing) {
                isLocalizing = true
                binding.progressBar.visibility = View.VISIBLE
                binding.localizationStatus.text = "Capturing image..."

                lifecycleScope.launch {
                    try {
                        // stop live stream rendering in WebView
                        stopStream()

                        // fetch single snapshot from ESP32 /capture
                        val bitmap = fetchEsp32FrameBitmapFromCapture(esp32StreamUrl!!)
                        if (bitmap != null) {
                            // display the captured image in the webview while processing
                            showImageInWebView(bitmap)

                            // get a pose (IMU/ARCore)
                            val pose = getCurrentCameraPoseFromIMU()

                            // send to localization
                            sendLocalizationRequest(bitmap, pose)
                        } else {
                            showToast("Failed to fetch image from device")
                            resumeStream()
                            binding.progressBar.visibility = View.GONE
                            isLocalizing = false
                        }
                    } catch (e: Exception) {
                        Log.e("ARActivity", "Localization flow failed: ${e.message}")
                        showToast("Localization error: ${e.message ?: "unknown"}")
                        resumeStream()
                        binding.progressBar.visibility = View.GONE
                        isLocalizing = false
                    }
                }
            }
        }

        binding.resetButton.setOnClickListener { resetWorldOrigin() }
        binding.closeButton.setOnClickListener { finish() }
    }

    private fun startStream() {
        esp32StreamUrl?.let {
            binding.esp32WebView.visibility = View.VISIBLE
            // ensure URL includes protocol (http://)
            val finalUrl = if (it.startsWith("http")) it else "http://$it"
            binding.esp32WebView.loadUrl(finalUrl)
        }
    }

    private fun stopStream() {
        // stop and hide webview to release HTTP connection
        try {
            binding.esp32WebView.stopLoading()
        } catch (_: Exception) { }
        binding.esp32WebView.visibility = View.INVISIBLE
    }

    private fun resumeStream() {
        binding.esp32WebView.visibility = View.VISIBLE
        startStream()
    }

    private fun showImageInWebView(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.Main) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val base64Image = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)
            val html = "<html><body style='margin:0;padding:0;background:black;'><img src='data:image/png;base64,$base64Image' " +
                    "style='width:100%;height:100%;object-fit:contain;'/></body></html>"
            binding.esp32WebView.loadData(html, "text/html", "utf-8")
        }
    }

    private suspend fun fetchEsp32FrameBitmapFromCapture(streamUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Build /capture URL robustly
            val raw = if (streamUrl.endsWith("/")) streamUrl else "$streamUrl/"
            val urlStr = if (raw.startsWith("http")) "$raw${"capture"}" else "http://$raw${"capture"}"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect()

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val input = conn.inputStream
                val bmp = BitmapFactory.decodeStream(input)
                input.close()
                conn.disconnect()
                bmp
            } else {
                Log.w("ESP32_FETCH", "Non-200 response: ${conn.responseCode}")
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.e("ESP32_FETCH", "Error fetching image: ${e.message}")
            null
        }
    }

    private suspend fun sendLocalizationRequest(bitmap: Bitmap, cameraPose: CameraPose) {
        if (authToken == null) {
            showToast("Auth token is required")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val processedData = imageProcessor.processImageForLocalization(
                    bitmap,
                    arFragment.arSceneView.arFrame!!,
                    this@ARActivity
                )

                val parameters = mutableMapOf(
                    "isRightHanded" to "true",
                    "fx" to processedData.fx.toString(),
                    "fy" to processedData.fy.toString(),
                    "px" to processedData.px.toString(),
                    "py" to processedData.py.toString(),
                    "width" to processedData.width.toString(),
                    "height" to processedData.height.toString()
                )

                when (SDKConfig.getActiveMapType()) {
                    SDKConfig.MapType.MAP -> parameters["mapCode"] = SDKConfig.MAP_CODE
                    SDKConfig.MapType.MAP_SET -> parameters["mapSetCode"] = SDKConfig.MAP_SET_CODE
                }

                val outputStream = ByteArrayOutputStream()
                processedData.bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                val imageData = outputStream.toByteArray()

                val response: LocalizationResponse = networkManager.sendLocalizationRequest(
                    authToken!!,
                    parameters,
                    imageData
                )

                // Build internal LocalizationResult and post to viewModel (or directly handle)
                val localResult = LocalizationResult(
                    response = response,
                    cameraPose = cameraPose,
                    resultPose = ResultPose(Vector3.zero(), Quaternion.identity()) // placeholder until poseHandler fills it
                )

                // Calculate processed pose on UI thread
                withContext(Dispatchers.Main) {
                    // compute and apply pose using poseHandler (same logic as Swift)
                    val processedPose = poseHandler(response, cameraPose)
                    // update the resultPose in localResult (we'll create a new one)
                    val finalResult = LocalizationResult(response, cameraPose, ResultPose(processedPose.position, processedPose.rotation))
                    viewModel.setLocalizationResult(response, cameraPose) // keep your ViewModel API call (assumed)
                    // handle the result in UI
                    handleLocalizationResult(finalResult)
                    binding.progressBar.visibility = View.GONE
                    isLocalizing = false
                    resumeStream()
                }

            } catch (e: Exception) {
                Log.e("LOCALIZE", "Send localization failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    showToast("Localization failed!")
                    binding.progressBar.visibility = View.GONE
                    isLocalizing = false
                    resumeStream()
                }
            }
        }
    }

    /** ---------------- Gizmo & AR Overlay ---------------- **/
    private fun addGizmoToScene() {
        val scene = arFragment.arSceneView.scene
        gizmoNode = GizmoNode(this)
        scene.addChild(gizmoNode)
        gizmoNode?.show()
    }

    private fun updateGizmoPosition(position: Vector3, rotation: Quaternion) {
        gizmoNode?.let { node ->
            node.localPosition = position
            node.localRotation = rotation
            node.show()
        } ?: Log.e("Multiset_LOG >>", "Gizmo node is null!")
    }

    /** Observe ViewModel (explicit typed observer for localization result) **/
    private fun observeViewModel() {
        viewModel.trackingState.observe(this) { state ->
            binding.statusText.text = state
        }

        // Explicit type so Kotlin doesn't try to infer from unknown ViewModel type
        viewModel.localizationResult.observe(this) { result: LocalizationResult? ->
            result?.let {
                handleLocalizationResult(it)
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun handleLocalizationResult(result: LocalizationResult) {
        if (result.response.poseFound) {
            binding.localizationStatus.text = "Localization Success"
            // Compute final pose (poseHandler) and update gizmo
            val processedPose = poseHandler(result.response, result.cameraPose)
            updateGizmoPosition(processedPose.position, processedPose.rotation)
            showToast("Localization successful!")
        } else {
            binding.localizationStatus.text = "Localization failed"
            showToast("Pose not found")
        }
    }

    private fun onSceneUpdate(frameTime: FrameTime) {
        val frame = arFragment.arSceneView.arFrame ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return

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

    private fun resetWorldOrigin() {
        val session = arFragment.arSceneView.session ?: return
        session.pause()
        val config = Config(session)
        session.configure(config)
        session.resume()

        gizmoNode?.localPosition = Vector3.zero()
        gizmoNode?.localRotation = Quaternion.identity()
        showToast("World origin reset")
    }

    /** ---------------- Matrix math & pose handler ---------------- **/
    data class CameraPose(val position: Vector3, val rotation: Quaternion)

    data class LocalizationResult(
        val response: LocalizationResponse,
        val cameraPose: CameraPose,
        val resultPose: ResultPose
    )

    data class ResultPose(val position: Vector3, val rotation: Quaternion)

    // Mirror of the Swift-style poseHandler: uses util helpers (assumed present in your util package)
    private fun poseHandler(
        localizationResponse: LocalizationResponse,
        cameraPose: CameraPose
    ): ResultPose {
        // Parse response rotation/position
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

        // Use your util functions (assumed available in project)
        val rotationMatrix = com.multiset.sdk.android.utils.Util.createMatrixFromQuaternion(resRotation)
        val negatedResponseMatrix = com.multiset.sdk.android.utils.Util.createTransformMatrix(rotationMatrix, resPosition)
        val invNegatedResponseMatrix = com.multiset.sdk.android.utils.Util.invertMatrix(negatedResponseMatrix)

        val trackerSpaceMatrix = com.multiset.sdk.android.utils.Util.createTransformMatrix(
            com.multiset.sdk.android.utils.Util.createMatrixFromQuaternion(cameraPose.rotation),
            cameraPose.position
        )

        val resultantMatrix = com.multiset.sdk.android.utils.Util.multiplyMatrices(trackerSpaceMatrix, invNegatedResponseMatrix)
        val resultPosition = com.multiset.sdk.android.utils.Util.extractPosition(resultantMatrix)
        val resultRotationRaw = com.multiset.sdk.android.utils.Util.extractRotation(resultantMatrix)

        return ResultPose(resultPosition, resultRotationRaw)
    }

    /** ---------------- Utility ---------------- **/
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getCurrentCameraPoseFromIMU(): CameraPose {
        val frame = arFragment.arSceneView.arFrame
        return if (frame != null) {
            val pose = frame.camera.pose
            CameraPose(Vector3(pose.tx(), pose.ty(), pose.tz()), Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw()))
        } else {
            CameraPose(Vector3.zero(), Quaternion.identity())
        }
    }
}
