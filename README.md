# MultiSet-Android-SDK

This SDK allows you to perform Visual Positioning using MultiSet's VPS (Visual Positioning System). It supports localization of either a single map or a mapSet with centimeter-level accuracy.

## 🚀 Getting Started

### 1. Configure SDK Credentials

Open the `SDKConfig.kt` file located at `com/multiset/sdk/android/config/SDKConfig.kt` and provide your **Client ID** and **Client Secret**:

```kotlin
object SDKConfig {
    // Enter your credentials here
    const val CLIENT_ID = "YOUR_CLIENT_ID"
    const val CLIENT_SECRET = "YOUR_CLIENT_SECRET"
    
    // API endpoints (do not modify)
    const val SDK_AUTH_URL = "https://api.multiset.ai/v1/m2m/token"
    const val QUERY_URL = "https://api.multiset.ai/v1/vps/map/query-form"
}
```

To get your credentials, visit:
🔗 https://developer.multiset.ai/credentials

These credentials are required to authenticate the user with the MultiSet platform.

### 2. Choose Map Type & Provide Map Code

Depending on whether you want to localize a single map or a map set, provide the appropriate code in `SDKConfig.kt`:

```kotlin
object SDKConfig {
    // For localizing a single map
    const val MAP_CODE = "YOUR_MAP_CODE"
    
    // For localizing a map set
    const val MAP_SET_CODE = ""  // Leave empty if using MAP_CODE
    
    // The SDK automatically determines the map type
    fun getActiveMapType(): MapType {
        return if (MAP_CODE.isNotEmpty()) MapType.MAP else MapType.MAP_SET
    }
    
    enum class MapType {
        MAP, MAP_SET
    }
}
```

**Important**: Only one should be active at a time — either `MAP_CODE` or `MAP_SET_CODE`.

### 3. Start Localization

After configuration:

1. **Launch the app** on an ARCore-supported device
2. **Grant camera permission** when prompted
3. **Tap Auth button** to authenticate with MultiSet
4. **Tap Localize button** to start visual positioning
5. **Point camera** at the mapped area

Upon successful localization, a **3D Gizmo** (coordinate axes) will appear at the Map Origin indicating that the pose has been correctly estimated.

## 📱 Requirements

- **Android API level**: 28 (Android 9.0) or higher
- **Target SDK**: 35 (Android 14)
- **Camera**: Required for visual positioning
- **ARCore**: Required for AR visualization
- **Internet**: Required for authentication and localization

## 🔧 Installation

### Step 1: Add Dependencies

Add the following to your app's `build.gradle.kts`:

```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    
    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // ARCore
    implementation("com.google.ar:core:1.41.0")
    
    // SceneView for modern AR
    implementation("io.github.sceneview:sceneview:1.2.2")
    
    // Camera
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### Step 2: Configure Permissions

Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />

<!-- AR Features -->
<uses-feature android:name="android.hardware.camera.ar" android:required="true" />
<uses-feature android:name="android.hardware.camera" android:required="true" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="true" />

<application>
    <!-- ARCore Meta Data -->
    <meta-data android:name="com.google.ar.core" android:value="required" />
    
    <!-- Your activities -->
</application>
```

### Step 3: Enable Jetifier

Add to your `gradle.properties`:

```properties
android.useAndroidX=true
android.enableJetifier=true
```

## 🏗️ Architecture

```
com.multiset.sdk.android/
├── ar/
│   ├── ARActivity.kt          # Main AR visualization
│   ├── ARViewModel.kt         # AR state management
│   └── GizmoNode.kt          # 3D coordinate axes
├── auth/
│   └── AuthManager.kt        # Authentication handling
├── camera/
│   └── ImageProcessor.kt     # Camera frame processing
├── config/
│   └── SDKConfig.kt         # SDK configuration
├── network/
│   ├── NetworkManager.kt    # API communication
│   └── LocalizationResponse.kt
└── utils/
    └── Util.kt             # Matrix operations
```

## 📌 Features

- ✅ **Visual Positioning System (VPS)** with centimeter-level accuracy
- ✅ **Single map and mapSet** localization support
- ✅ **Real-time pose tracking** with 6DOF
- ✅ **AR visualization** with 3D gizmo at map origin
- ✅ **Portrait and landscape** orientation support
- ✅ **Automatic token management** for authentication
- ✅ **Debug logging** for development

## 🔍 Troubleshooting

### Authentication Issues
- Verify your Client ID and Secret are correct
- Check internet connectivity
- Ensure credentials are active on developer portal

### Localization Failures
- Ensure you're in the mapped area
- Check lighting conditions (avoid too dark/bright)
- Move device slowly for better tracking
- Verify map code is correct

### Camera Issues
- Grant camera permissions in device settings
- Ensure no other app is using camera
- Restart app if camera preview is black

### ARCore Issues
- Install/update Google Play Services for AR
- Check device compatibility at https://developers.google.com/ar/devices


## 🧑‍💻 Support

For any questions or issues:
- 📚 Documentation: https://docs.multiset.ai
- 🐛 Issues: Raise an issue on this repository
- 📧 Email: support@multiset.ai

## 📄 License

Copyright © 2025 MultiSet AI. All rights reserved.

Licensed under the MultiSet License. You may not use this file except in compliance with the License. Redistribution in source or binary forms must retain this notice. For license details, visit www.multiset.ai.