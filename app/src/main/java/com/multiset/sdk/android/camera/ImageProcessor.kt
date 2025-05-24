/*
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License. and you can't re-distribute this file without a prior notice
For license details, visit www.multiset.ai.
Redistribution in source or binary forms must retain this notice.
*/
package com.multiset.sdk.android.camera

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import com.google.ar.core.Frame

class ImageProcessor {

    data class ProcessedImageData(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val px: Float,
        val py: Float,
        val fx: Float,
        val fy: Float
    )

    fun processImageForLocalization(bitmap: Bitmap, frame: Frame, context: Context): ProcessedImageData {
        // Get camera intrinsics from the original frame
        val intrinsics = frame.camera.imageIntrinsics
        val fx = intrinsics.focalLength[0]
        val fy = intrinsics.focalLength[1]
        val cx = intrinsics.principalPoint[0]
        val cy = intrinsics.principalPoint[1]

        // Get the original camera image dimensions (before any rotation)
        val cameraImage = frame.acquireCameraImage()
        val origWidth = cameraImage.width.toFloat()
        val origHeight = cameraImage.height.toFloat()
        cameraImage.close()

        // Check device orientation (not bitmap orientation)
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        // Target dimensions based on orientation
        val targetWidth = if (isPortrait) 720 else 960
        val targetHeight = if (isPortrait) 960 else 720

        // Scale factors - these should be calculated based on how the original camera image
        // dimensions map to the target dimensions
        val scaleX: Float
        val scaleY: Float

        if (isPortrait) {
            // In portrait, the camera image is rotated 90 degrees
            // So original width becomes height and vice versa
            scaleX = targetWidth.toFloat() / origHeight
            scaleY = targetHeight.toFloat() / origWidth
        } else {
            // In landscape, dimensions map directly
            scaleX = targetWidth.toFloat() / origWidth
            scaleY = targetHeight.toFloat() / origHeight
        }

        // Resize bitmap to target dimensions
        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            targetWidth,
            targetHeight,
            true
        )

        // Adjust intrinsics based on orientation
        val newFx: Float
        val newFy: Float
        val newPx: Float
        val newPy: Float

        if (isPortrait) {
            // Portrait adjustments - matching iOS implementation
            // Swap focal lengths due to 90-degree rotation
            newFx = fy * scaleX
            newFy = fx * scaleY
            // Transform principal points accounting for rotation
            newPx = (origHeight - cy) * scaleX
            newPy = cx * scaleY
        } else {
            // Landscape adjustments - no rotation needed
            newFx = fx * scaleX
            newFy = fy * scaleY
            newPx = cx * scaleX
            newPy = cy * scaleY
        }

        return ProcessedImageData(
            bitmap = resizedBitmap,
            width = targetWidth,
            height = targetHeight,
            px = newPx,
            py = newPy,
            fx = newFx,
            fy = newFy
        )
    }

    fun imageToRgbBitmap(image: Image): Bitmap {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val width = image.width
        val height = image.height

        val ySize = yPlane.buffer.remaining()
        val uSize = uPlane.buffer.remaining()
        val vSize = vPlane.buffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yPlane.buffer.get(nv21, 0, ySize)
        vPlane.buffer.get(nv21, ySize, vSize)
        uPlane.buffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)

        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}