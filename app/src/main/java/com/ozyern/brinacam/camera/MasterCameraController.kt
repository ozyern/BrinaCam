package com.ozyern.brinacam.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.DngCreator
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

class MasterCameraController(
    private val context: Context,
    private val onReady: (Boolean) -> Unit,
    private val onSaved: (Uri, String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executor { command -> handler.post(command) }
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var characteristics: CameraCharacteristics? = null
    private var iso = 100
    private var exposureMs = 10L
    private var lastResult: TotalCaptureResult? = null
    private var capturing = false

    @SuppressLint("MissingPermission")
    fun start(surface: Surface, initialIso: Int, initialExposureMs: Long) {
        close()
        previewSurface = surface
        iso = initialIso
        exposureMs = initialExposureMs
        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
        if (cameraId == null) {
            onError("Master camera unavailable")
            return
        }
        characteristics = manager.getCameraCharacteristics(cameraId)
        runCatching { manager.openCamera(cameraId, cameraCallback, handler) }
            .onFailure { onError("Master camera unavailable") }
    }

    fun setManual(newIso: Int, newExposureMs: Long) {
        iso = newIso
        exposureMs = newExposureMs
        updatePreview()
    }

    fun capture(raw: Boolean) {
        val device = camera ?: return
        val activeSession = session ?: return
        if (capturing) return
        capturing = true
        runCatching {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                jpegReader?.surface?.let(::addTarget)
                if (raw) rawReader?.surface?.let(::addTarget)
                applyManual(this)
                set(CaptureRequest.JPEG_ORIENTATION, 90)
            }.build()
            activeSession.capture(request, captureCallback, handler)
        }.onFailure {
            capturing = false
            onError("Master capture failed")
        }
    }

    private val cameraCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            camera = device
            createReaders(device)
        }

        override fun onDisconnected(device: CameraDevice) {
            device.close()
            camera = null
        }

        override fun onError(device: CameraDevice, error: Int) {
            device.close()
            camera = null
            onError("Master camera error")
        }
    }

    private fun createReaders(device: CameraDevice) {
        val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSize = map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
            ?: Size(1920, 1080)
        val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR).orEmpty()
        val rawSize = rawSizes.maxByOrNull { it.width.toLong() * it.height }
        jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).also {
            it.setOnImageAvailableListener({ reader -> saveJpeg(reader.acquireLatestImage()) }, handler)
        }
        rawReader = rawSize?.let { size ->
            ImageReader.newInstance(size.width, size.height, ImageFormat.RAW_SENSOR, 2).also {
                it.setOnImageAvailableListener({ reader -> saveRaw(reader.acquireLatestImage()) }, handler)
            }
        }
        val outputs = buildList {
            previewSurface?.let(::add)
            jpegReader?.surface?.let(::add)
            rawReader?.surface?.let(::add)
        }
        device.createCaptureSession(outputs, sessionCallback, handler)
        onReady(rawReader != null)
    }

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(newSession: CameraCaptureSession) {
            session = newSession
            updatePreview()
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            onError("Master camera setup failed")
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            lastResult = result
            handler.postDelayed({ capturing = false }, 150)
        }
    }

    private fun updatePreview() {
        val device = camera ?: return
        val activeSession = session ?: return
        val surface = previewSurface ?: return
        runCatching {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                applyManual(this)
            }.build()
            activeSession.setRepeatingRequest(request, null, handler)
        }
    }

    private fun applyManual(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureMs * 1_000_000L)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
    }

    private fun saveJpeg(image: android.media.Image?) {
        image ?: return
        image.use {
            val buffer = it.planes[0].buffer
            val bytes = ByteArray(buffer.remaining()).also(buffer::get)
            val uri = insert("MASTER_", "image/jpeg", "jpg") ?: return
            context.contentResolver.openOutputStream(uri)?.use { output -> output.write(bytes) }
            onSaved(uri, "image/jpeg")
        }
    }

    private fun saveRaw(image: android.media.Image?) {
        val result = lastResult ?: run {
            image?.close()
            return
        }
        image ?: return
        image.use {
            val uri = insert("MASTER_", "image/x-adobe-dng", "dng") ?: return
            context.contentResolver.openOutputStream(uri)?.use { output ->
                DngCreator(characteristics!!, result).use { creator ->
                    creator.writeImage(output, it)
                }
            }
            onSaved(uri, "image/x-adobe-dng")
        }
    }

    private fun insert(prefix: String, mime: String, extension: String): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$prefix$stamp.$extension")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/BrinaCam")
            },
        )
    }

    fun close() {
        session?.close()
        session = null
        camera?.close()
        camera = null
        jpegReader?.close()
        jpegReader = null
        rawReader?.close()
        rawReader = null
        previewSurface = null
        capturing = false
    }
}