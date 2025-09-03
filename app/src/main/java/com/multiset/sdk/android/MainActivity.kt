/* 
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License. and you can’t re-distribute this file without a prior notice
For license details, visit www.multiset.ai.
Redistribution in source or binary forms must retain this notice.
*/
package com.multiset.sdk.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.ArCoreApk
import com.multiset.sdk.android.ar.ARActivity
import com.multiset.sdk.android.auth.AuthManager
import com.multiset.sdk.android.config.SDKConfig
import com.multiset.sdk.android.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val authManager = AuthManager()
    private var isAuthenticated = false

    // Bluetooth
    private val BT_DEVICE_NAME = "SMART-GLASSES"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private var btOut: OutputStream? = null
    private var btIn: InputStream? = null
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Wi-Fi target (as requested)
    private val ESP_SSID = "BTIA_SIO1KOR"
    private val ESP_PASSWORD = "Y7QNGXlivndw"
    private val ESP_STATIC_IP = "http://192.168.4.1/"

    // State
    @Volatile private var isBtConnected = false
    @Volatile private var isWifiBound = false
    @Volatile private var isStreaming = false

    // Networking
    private var connectivityManager: ConnectivityManager? = null
    private var boundNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Permissions
    private val requiredPermissions = mutableListOf<String>().apply {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION) // for BT/Wi-Fi discovery on older devices
        }
        // WiFi perms are normal; no runtime request for ACCESS_WIFI_STATE/CHANGE_WIFI_STATE
    }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            // check granted
            val denied = result.entries.firstOrNull { !it.value }
            if (denied != null) {
                showToast(getString(R.string.permission_denied, denied.key))
            } else {
                showToast(getString(R.string.permissions_granted))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Use BluetoothManager for adapter (not deprecated)
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Example: Use string resource for statusText
        binding.statusText.text = getString(R.string.ready_to_authenticate)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // ask for permissions
        permissionLauncher.launch(requiredPermissions)

        setupUI()
    }

    private fun setupUI() {
        binding.localizeButton.isEnabled = false
        binding.authButton.setOnClickListener { authenticateUser() }
        binding.localizeButton.setOnClickListener {
            val ipAddress = binding.ipAddressEditText.text.toString().trim()
            if (isBtConnected && ipAddress.isNotEmpty()) {
                // Launch ARActivity in ESP32 mode, pass the stream URL
                val streamUrl = if (ipAddress.startsWith("http://") || ipAddress.startsWith("https://")) ipAddress else "http://$ipAddress/"
                val intent = Intent(this, com.multiset.sdk.android.ar.ARActivity::class.java)
                intent.putExtra("AUTH_TOKEN", authManager.getToken())
                intent.putExtra("ESP32_STREAM_URL", streamUrl)
                startActivity(intent)
            } else {
                // Show phone camera feed (default AR/localization flow)
                val intent = Intent(this, com.multiset.sdk.android.ar.ARActivity::class.java)
                intent.putExtra("AUTH_TOKEN", authManager.getToken())
                startActivity(intent)
            }
        }
        // Enable Stop Stream button when IP is entered
        binding.ipAddressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.stopStreamButton.isEnabled = !s.isNullOrEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.connectBtButton.setOnClickListener {
            lifecycleScope.launch { connectToSmartGlasses() }
        }
        binding.stopStreamButton.setOnClickListener {
            val ipAddress = binding.ipAddressEditText.text.toString().trim()
            if (ipAddress.isNotEmpty()) {
                lifecycleScope.launch { requestStreamStop(ipAddress) }
            }
        }
        updateBtUi(false)
        updateStreamUi(false)
    }

    private fun authenticateUser() {
        if (SDKConfig.CLIENT_ID.isEmpty() || SDKConfig.CLIENT_SECRET.isEmpty()) {
            binding.statusText.text = getString(R.string.please_enter_client_id_secret)
            return
        }

        binding.statusText.text = getString(R.string.authenticating)
        binding.authButton.isEnabled = false

        lifecycleScope.launch {
            authManager.authenticate().fold(
                onSuccess = { token ->
                    isAuthenticated = true
                    binding.statusText.text = getString(R.string.authenticated)
                    binding.authButton.text = getString(R.string.authenticated_check)
                    binding.authButton.isEnabled = false
                    binding.localizeButton.isEnabled = true
                    showToast(getString(R.string.show_toast_auth_success))
                },
                onFailure = { error ->
                    binding.statusText.text = getString(R.string.authentication_failed)
                    binding.authButton.isEnabled = true
                    showToast(getString(R.string.show_toast_auth_failed, error.message ?: ""))
                }
            )
        }
    }

    // ------------------------------
    // Bluetooth: find & connect
    // ------------------------------
    private suspend fun connectToSmartGlasses() = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runOnUiThread { showToast(getString(R.string.missing_bt_permission)) }
            return@withContext
        }
        runOnUiThread { binding.btStatusText.text = getString(R.string.scanning_connecting) }
        if (bluetoothAdapter == null) {
            runOnUiThread { showToast(getString(R.string.no_bt_adapter)) }
            return@withContext
        }
        if (!bluetoothAdapter!!.isEnabled) {
            runOnUiThread { showToast(getString(R.string.enable_bt_retry)) }
            return@withContext
        }

        // 1) look among bonded devices first
        val paired = bluetoothAdapter!!.bondedDevices
        val target = paired.firstOrNull { it.name == BT_DEVICE_NAME }
        val device = target ?: run {
            // 2) fallback: do a discovery and pick first matching device
            val discovered = discoverDeviceName(BT_DEVICE_NAME, 8000L)
            discovered
        }

        if (device == null) {
            runOnUiThread {
                binding.btStatusText.text = getString(R.string.device_not_found)
                showToast(getString(R.string.smart_glasses_not_found))
            }
            return@withContext
        }

        runOnUiThread { binding.btStatusText.text = getString(R.string.connecting_to_device, device.name) }
        try {
            // classic SPP socket
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter!!.cancelDiscovery()
            socket.connect()
            btSocket = socket
            btOut = socket.outputStream
            btIn = socket.inputStream
            isBtConnected = true

            runOnUiThread {
                updateBtUi(true)
                binding.btStatusText.text = getString(R.string.connected_to_device, device.name)
                btOut!!.write("STREAM\n".toByteArray(Charsets.UTF_8))
                showToast(getString(R.string.bluetooth_connected))
            }
        } catch (e: IOException) {
            isBtConnected = false
            btSocket?.close()
            btSocket = null
            runOnUiThread {
                updateBtUi(false)
                binding.btStatusText.text = getString(R.string.connection_failed, e.message ?: "")
                showToast(getString(R.string.bluetooth_connect_failed, e.message ?: ""))
            }
        }
    }

    /**
     * Simple discovery helper: performs discovery for specified duration and returns first device
     * that matches the provided name. Runs on IO dispatcher.
     */
    private suspend fun discoverDeviceName(name: String, timeoutMs: Long): BluetoothDevice? =
        withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { showToast(getString(R.string.missing_bt_permission)) }
                return@withContext null
            }
            val found = arrayListOf<BluetoothDevice>()
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            val receiver = object : BroadcastReceiver() {
                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action
                    if (action == BluetoothDevice.ACTION_FOUND) {
                        val dev: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (dev != null && dev.name == name) {
                            found.add(dev)
                        }
                    }
                }
            }
            registerReceiver(receiver, filter)
            bluetoothAdapter?.startDiscovery()
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs && found.isEmpty()) {
                Thread.sleep(200)
            }
            bluetoothAdapter?.cancelDiscovery()
            try { unregisterReceiver(receiver) } catch (_: Exception) {}
            return@withContext found.firstOrNull()
        }

    private fun updateBtUi(connected: Boolean) {
        runOnUiThread {
            binding.connectBtButton.isEnabled = !connected
            binding.stopStreamButton.isEnabled = connected && isStreaming
            binding.btStatusText.text = if (connected) getString(R.string.bluetooth_connected) else getString(R.string.bluetooth_disconnected)
        }
    }

    // ------------------------------
    // Stop streaming: tries HTTP /stop endpoint and unbind
    // ------------------------------
    private suspend fun requestStreamStop(ipAddress: String) = withContext(Dispatchers.IO) {
        try {
            val url = URL("$ipAddress/stopstream")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val responseCode = conn.responseCode
            runOnUiThread {
                if (responseCode == 200) {
                    showToast("Stream stopped on ESP32")
                    binding.webViewStream.visibility = View.GONE
                } else {
                    showToast("Failed to stop stream: $responseCode")
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            showToast(getString(R.string.stream_stopped))
        }
    }

    // ------------------------------
    // Wi-Fi connection helpers
    // ------------------------------
    private suspend fun connectAndBindToEspWifi(): Boolean = withContext(Dispatchers.Main) {
        // For API >= 29 use WifiNetworkSpecifier + requestNetwork
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return@withContext connectWithNetworkSpecifier()
        } else {
            // Pre-Android 10: try enabling network through WifiManager (deprecated API)
            return@withContext connectLegacyWifi()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private suspend fun connectWithNetworkSpecifier(): Boolean = withContext(Dispatchers.Main) {
        try {
            val specBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(ESP_SSID)
                .setWpa2Passphrase(ESP_PASSWORD)
            val spec = specBuilder.build()

            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // it's local esp network
                .setNetworkSpecifier(spec)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    // bind process to this network for outgoing traffic
                    connectivityManager?.bindProcessToNetwork(network)
                    boundNetwork = network
                    isWifiBound = true
                    runOnUiThread { binding.streamStatusText.text = getString(R.string.bound_to_wifi, ESP_SSID) }
                }
                override fun onUnavailable() {
                    runOnUiThread { binding.streamStatusText.text = getString(R.string.wifi_unavailable) }
                }
            }
            connectivityManager?.requestNetwork(req, networkCallback!!)

            // Wait a short period for network to come up
            var waited = 0
            while (!isWifiBound && waited < 7000) {
                Thread.sleep(300)
                waited += 300
            }
            return@withContext isWifiBound
        } catch (e: Exception) {
            Log.e("MainActivity", "connectWithNetworkSpecifier error", e)
            return@withContext false
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun connectLegacyWifi(): Boolean = withContext(Dispatchers.IO) {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_WIFI_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runOnUiThread { showToast(getString(R.string.permission_denied, Manifest.permission.ACCESS_WIFI_STATE)) }
            return@withContext false
        }
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
            Thread.sleep(1000)
        }

        // create WifiConfiguration
        val conf = WifiConfiguration().apply {
            SSID = "\"$ESP_SSID\""
            preSharedKey = "\"$ESP_PASSWORD\""
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        }

        val netId = wifiManager.addNetwork(conf)
        if (netId == -1) {
            // maybe existing network, try to find
            val configured = wifiManager.configuredNetworks.firstOrNull { it.SSID == "\"$ESP_SSID\"" }
            if (configured != null) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(configured.networkId, true)
                wifiManager.reconnect()
                // assume success after short wait
                Thread.sleep(2000)
                boundNetwork = null
                isWifiBound = true
                return@withContext true
            } else {
                return@withContext false
            }
        } else {
            wifiManager.disconnect()
            wifiManager.enableNetwork(netId, true)
            wifiManager.reconnect()
            Thread.sleep(2000)
            isWifiBound = true
            return@withContext true
        }
    }

    private fun unbindEspWifi() {
        // unbind process from the bound network and unregister callback
        try {
            if (networkCallback != null) {
                connectivityManager?.unregisterNetworkCallback(networkCallback!!)
                networkCallback = null
            }
        } catch (_: Exception) {}
        try {
            connectivityManager?.bindProcessToNetwork(null)
        } catch (_: Exception) {}
        isWifiBound = false
        boundNetwork = null
    }

    // ------------------------------
    // WebView helper for stream
    // ------------------------------
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val web: WebView = binding.webViewStream
        web.settings.javaScriptEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.streamStatusText.text = getString(R.string.loaded_url, url ?: "")
            }
        }
    }

    private fun updateStreamUi(streamingNow: Boolean) {
        runOnUiThread {
            binding.stopStreamButton.isEnabled = streamingNow
            binding.webViewStream.visibility = if (streamingNow) View.VISIBLE else View.GONE
        }
    }

    // ------------------------------
    // Misc helpers and AR flow (kept from your original)
    // ------------------------------
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
                        showToast(getString(R.string.please_install_arcore_restart))
                    }
                } catch (e: Exception) {
                    showToast(getString(R.string.arcore_install_failed))
                }
            }
            else -> {
                showToast(getString(R.string.arcore_not_supported))
            }
        }
    }

    private fun startARActivity() {
        val mapType = SDKConfig.getActiveMapType()

        when (mapType) {
            SDKConfig.MapType.MAP -> {
                if (SDKConfig.MAP_CODE.isEmpty()) {
                    showToast(getString(R.string.please_enter_map_code))
                    return
                }
            }
            SDKConfig.MapType.MAP_SET -> {
                if (SDKConfig.MAP_SET_CODE.isEmpty()) {
                    showToast(getString(R.string.please_enter_mapset_code))
                    return
                }
            }
        }

        val intent = Intent(this, ARActivity::class.java).apply {
            putExtra("AUTH_TOKEN", authManager.getToken())
        }
        startActivity(intent)
    }

    private fun showToast(msg: String) {
        runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
    }

    // ------------------------------
    // ESP32 frame fetching and localization
    // ------------------------------
    private suspend fun fetchAndLocalizeEsp32Frame(ipAddress: String, attempt: Int = 0) {
        val captureUrl = if (ipAddress.endsWith("/")) ipAddress + "capture" else "$ipAddress/capture"
        try {
            val url = URL(captureUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val inputStream = conn.inputStream
                val imageBytes = inputStream.readBytes()
                inputStream.close()
                conn.disconnect()
                // Show image in WebView (optional)
                val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)
                val html = "<img src='data:image/jpeg;base64,$base64' style='width:100%;height:auto;'/>"
                runOnUiThread {
                    binding.webViewStream.visibility = View.VISIBLE
                    binding.webViewStream.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    binding.streamStatusText.text = getString(R.string.streaming_from, ipAddress)
                }
                // Feed imageBytes to MultiSet SDK for localization
                localizeWithEsp32Frame(imageBytes)
            } else {
                conn.disconnect()
                // Retry after delay if not available
                if (attempt < 10) {
                    runOnUiThread { binding.streamStatusText.text = "Retrying ESP32 feed..." }
                    kotlinx.coroutines.delay(1000)
                    fetchAndLocalizeEsp32Frame(ipAddress, attempt + 1)
                } else {
                    runOnUiThread { binding.streamStatusText.text = "Failed to get ESP32 feed." }
                }
            }
        } catch (e: Exception) {
            // Retry after delay if not available
            if (attempt < 10) {
                runOnUiThread { binding.streamStatusText.text = "Retrying ESP32 feed..." }
                kotlinx.coroutines.delay(1000)
                fetchAndLocalizeEsp32Frame(ipAddress, attempt + 1)
            } else {
                runOnUiThread { binding.streamStatusText.text = "Failed to get ESP32 feed." }
            }
        }
    }

    private fun localizeWithEsp32Frame(imageBytes: ByteArray) {
        // TODO: Pass imageBytes to MultiSet SDK for localization
        // Example: authManager.localizeWithImage(imageBytes)
        // You may need to convert imageBytes to Bitmap or required format
    }

    override fun onDestroy() {
        super.onDestroy()
        // cleanup: close BT socket & unbind wifi
        try { btOut?.close() } catch (_: Exception) {}
        try { btIn?.close() } catch (_: Exception) {}
        try { btSocket?.close() } catch (_: Exception) {}
        unbindEspWifi()
    }
}
