/*
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License. and you can’t re-distribute this file without a prior notice
For license details, visit www.multiset.ai.
Redistribution in source or binary forms must retain this notice.
*/
package com.multiset.sdk.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.ArCoreApk
import com.multiset.sdk.android.ar.ARActivity
import com.multiset.sdk.android.auth.AuthManager
import com.multiset.sdk.android.config.SDKConfig
import com.multiset.sdk.android.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlin.jvm.java


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val authManager = AuthManager()
    private var isAuthenticated = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkARCoreAndProceed()
        } else {
            showToast("Camera permission is required for localization")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()

        binding.instructionsText.setOnClickListener {

            val url = "https://developer.multiset.ai/credentials"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse(url)
            startActivity(intent)

        }

    }


    private fun setupUI() {
        // Initially hide localize button
        binding.localizeButton.isEnabled = false

        binding.authButton.setOnClickListener {
            authenticateUser()
        }

        binding.localizeButton.setOnClickListener {
            if (checkCameraPermission()) {
                checkARCoreAndProceed()
            }
        }
    }

    private fun authenticateUser() {
        if (SDKConfig.CLIENT_ID.isEmpty() || SDKConfig.CLIENT_SECRET.isEmpty()) {
            binding.statusText.text = "Please enter ClientId and ClientSecret in SDKConfig file"
            return
        }

        binding.statusText.text = "Authenticating..."
        binding.authButton.isEnabled = false

        lifecycleScope.launch {
            authManager.authenticate().fold(
                onSuccess = { token ->
                    isAuthenticated = true
                    binding.statusText.text = "Authenticated"
                    binding.authButton.text = "Authenticated ✓"
                    binding.authButton.isEnabled = false
                    binding.localizeButton.isEnabled = true
                    showToast("Authentication successful")
                },
                onFailure = { error ->
                    binding.statusText.text = "Authentication Failed!"
                    binding.authButton.isEnabled = true
                    showToast("Authentication failed: ${error.message}")
                }
            )
        }
    }

    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            false
        } else {
            true
        }
    }

    private fun checkARCoreAndProceed() {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                startARActivity()
            }

            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                try {
                    val installStatus = ArCoreApk.getInstance().requestInstall(this, true)
                    if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                        showToast("Please install ARCore and restart the app")
                    }
                } catch (e: Exception) {
                    showToast("ARCore installation failed")
                }
            }

            else -> {
                showToast("ARCore is not supported on this device")
            }
        }
    }

    private fun startARActivity() {
        val mapType = SDKConfig.getActiveMapType()

        when (mapType) {
            SDKConfig.MapType.MAP -> {
                if (SDKConfig.MAP_CODE.isEmpty()) {
                    showToast("Please enter mapCode in SDKConfig file")
                    return
                }
            }

            SDKConfig.MapType.MAP_SET -> {
                if (SDKConfig.MAP_SET_CODE.isEmpty()) {
                    showToast("Please enter mapSetCode in SDKConfig file")
                    return
                }
            }
        }

        val intent = Intent(this, ARActivity::class.java).apply {
            putExtra("AUTH_TOKEN", authManager.getToken())
        }
        startActivity(intent)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
