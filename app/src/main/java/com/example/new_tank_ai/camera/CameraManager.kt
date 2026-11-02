package com.example.new_tank_ai.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class CameraManager(private val context: Context) : DefaultLifecycleObserver {

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onStart(owner: LifecycleOwner) {
        if (hasCameraPermission()) {
            bindCamera(owner)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        unbindCamera()
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageCapture
                )
                Log.d("TankAI", "Front camera bound successfully")
            } catch (e: Exception) {
                Log.e("TankAI", "Failed to bind front camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbindCamera() {
        try {
            cameraProvider?.unbindAll()
            imageCapture = null
            Log.d("TankAI", "Front camera unbound")
        } catch (e: Exception) {
            Log.e("TankAI", "Error unbinding camera", e)
        }
    }

    suspend fun captureFrame(): ByteArray? = withTimeoutOrNull(5000) {
        val capture = imageCapture ?: run {
            Log.e("TankAI", "ImageCapture use case not initialized")
            return@withTimeoutOrNull null
        }

        suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        try {
                            val bitmap = imageProxy.toBitmap()
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                            val matrix = Matrix().apply {
                                postRotate(rotationDegrees.toFloat())
                                postScale(-1f, 1f) // Mirror horizontally for selfie front camera
                            }

                            val rotatedBitmap = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )

                            val width = rotatedBitmap.width
                            val height = rotatedBitmap.height
                            val maxDimension = maxOf(width, height)
                            val scaledBitmap = if (maxDimension > 1024) {
                                val scale = 1024f / maxDimension
                                Bitmap.createScaledBitmap(
                                    rotatedBitmap,
                                    (width * scale).toInt(),
                                    (height * scale).toInt(),
                                    true
                                )
                            } else {
                                rotatedBitmap
                            }

                            val outputStream = ByteArrayOutputStream()
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                            val jpegBytes = outputStream.toByteArray()

                            Log.d("TankAI", "Front camera frame captured successfully (${jpegBytes.size / 1024} KB)")
                            continuation.resume(jpegBytes)
                        } catch (e: Exception) {
                            Log.e("TankAI", "Error processing captured camera frame", e)
                            continuation.resume(null)
                        } finally {
                            imageProxy.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("TankAI", "Image capture failed", exception)
                        continuation.resume(null)
                    }
                }
            )
        }
    }
}
