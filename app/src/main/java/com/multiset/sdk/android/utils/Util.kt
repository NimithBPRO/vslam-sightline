/*
Copyright (c) 2025 MultiSet AI. All rights reserved.
Licensed under the MultiSet License. You may not use this file except in compliance with the License. and you can’t re-distribute this file without a prior notice
For license details, visit www.multiset.ai.
Redistribution in source or binary forms must retain this notice.
*/
package com.multiset.sdk.android.utils

import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import kotlin.math.*

class Util {
    companion object {

        // Helper methods for matrix operations
        fun createMatrixFromQuaternion(quaternion: Quaternion): Array<FloatArray> {
            val x = quaternion.x
            val y = quaternion.y
            val z = quaternion.z
            val w = quaternion.w

            val xx = x * x
            val yy = y * y
            val zz = z * z
            val xy = x * y
            val xz = x * z
            val yz = y * z
            val wx = w * x
            val wy = w * y
            val wz = w * z

            return arrayOf(
                floatArrayOf(1f - 2f * (yy + zz), 2f * (xy - wz), 2f * (xz + wy), 0f),
                floatArrayOf(2f * (xy + wz), 1f - 2f * (xx + zz), 2f * (yz - wx), 0f),
                floatArrayOf(2f * (xz - wy), 2f * (yz + wx), 1f - 2f * (xx + yy), 0f),
                floatArrayOf(0f, 0f, 0f, 1f)
            )
        }

        fun createTransformMatrix(
            rotationMatrix: Array<FloatArray>,
            position: Vector3
        ): Array<FloatArray> {
            val result = Array(4) { i -> rotationMatrix[i].clone() }
            result[0][3] = position.x
            result[1][3] = position.y
            result[2][3] = position.z
            result[3][3] = 1f
            return result
        }

        fun invertMatrix(matrix: Array<FloatArray>): Array<FloatArray> {
            // For a 4x4 transformation matrix, we can use the simplified inverse
            // since it's a combination of rotation and translation
            val result = Array(4) { FloatArray(4) }

            // Transpose the rotation part (top-left 3x3)
            for (i in 0..2) {
                for (j in 0..2) {
                    result[i][j] = matrix[j][i]
                }
            }

            // Calculate the inverse translation
            val invTranslation = Vector3(
                -(result[0][0] * matrix[0][3] + result[0][1] * matrix[1][3] + result[0][2] * matrix[2][3]),
                -(result[1][0] * matrix[0][3] + result[1][1] * matrix[1][3] + result[1][2] * matrix[2][3]),
                -(result[2][0] * matrix[0][3] + result[2][1] * matrix[1][3] + result[2][2] * matrix[2][3])
            )

            result[0][3] = invTranslation.x
            result[1][3] = invTranslation.y
            result[2][3] = invTranslation.z
            result[3][0] = 0f
            result[3][1] = 0f
            result[3][2] = 0f
            result[3][3] = 1f

            return result
        }

        fun multiplyMatrices(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
            val result = Array(4) { FloatArray(4) }

            for (i in 0..3) {
                for (j in 0..3) {
                    result[i][j] = 0f
                    for (k in 0..3) {
                        result[i][j] += a[i][k] * b[k][j]
                    }
                }
            }

            return result
        }

        fun extractPosition(matrix: Array<FloatArray>): Vector3 {
            return Vector3(matrix[0][3], matrix[1][3], matrix[2][3])
        }

        fun extractRotation(matrix: Array<FloatArray>): Quaternion {
            // Extract quaternion from rotation matrix
            val trace = matrix[0][0] + matrix[1][1] + matrix[2][2]

            return if (trace > 0) {
                val s = sqrt(trace + 1.0f) * 2f // s = 4 * qw
                val qw = 0.25f * s
                val qx = (matrix[2][1] - matrix[1][2]) / s
                val qy = (matrix[0][2] - matrix[2][0]) / s
                val qz = (matrix[1][0] - matrix[0][1]) / s
                Quaternion(qx, qy, qz, qw)
            } else if (matrix[0][0] > matrix[1][1] && matrix[0][0] > matrix[2][2]) {
                val s = sqrt(1.0f + matrix[0][0] - matrix[1][1] - matrix[2][2]) * 2f // s = 4 * qx
                val qw = (matrix[2][1] - matrix[1][2]) / s
                val qx = 0.25f * s
                val qy = (matrix[0][1] + matrix[1][0]) / s
                val qz = (matrix[0][2] + matrix[2][0]) / s
                Quaternion(qx, qy, qz, qw)
            } else if (matrix[1][1] > matrix[2][2]) {
                val s = sqrt(1.0f + matrix[1][1] - matrix[0][0] - matrix[2][2]) * 2f // s = 4 * qy
                val qw = (matrix[0][2] - matrix[2][0]) / s
                val qx = (matrix[0][1] + matrix[1][0]) / s
                val qy = 0.25f * s
                val qz = (matrix[1][2] + matrix[2][1]) / s
                Quaternion(qx, qy, qz, qw)
            } else {
                val s = sqrt(1.0f + matrix[2][2] - matrix[0][0] - matrix[1][1]) * 2f // s = 4 * qz
                val qw = (matrix[1][0] - matrix[0][1]) / s
                val qx = (matrix[0][2] + matrix[2][0]) / s
                val qy = (matrix[1][2] + matrix[2][1]) / s
                val qz = 0.25f * s
                Quaternion(qx, qy, qz, qw)
            }
        }
    }
}