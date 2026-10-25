package com.ozyern.brinacam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlin.math.roundToInt

/**
 * Camera2 still pipeline for HI-RES. CameraX ImageCapture cannot keep a live
 * preview and a 50 / 200 MP JPEG at the same time, so it silently falls back
 * to a binned ~9 MP 16:9 size. This controller prefers the ultra-high-
 * resolution sensor mode and the largest JPEG the HAL actually lists.
 */
class HiResCameraController(
    context: Context,
    private val lensFacing: Int,
    private var targetRotation: Int,
    private val onReady: (minZoom: Float, maxZoom: Float, evMin: Int, evMax: Int, evStep: Float) -> Unit,
    private val onCaptured: (jpeg: ByteArray, width: Int, height: Int) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = java.util.concurrent.Executor { command -> handler.post(command) }

    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var jpegReader: ImageReader? = null
    private var characteristics: CameraCharacteristics? = null
    private var jpegSize: Size = Size(0, 0)
    private var usingMaxRes = false
    private var combinedSession = false
    private var capturing = false
    private var pendingStill = false
    private var restorePreviewAfterStill = false

    private var zoom = 1f
    private var minZoom = 1f
    private var maxZoom = 1f
    private var evIndex = 0
    private var flash = FlashSetting.OFF
    private var afRegion: MeteringRectangle? = null

    fun start(surface: Surface) {
        close()
        previewSurface = surface
        val cameraId = cameraId() ?: run {
            onError("HI-RES camera unavailable")
            return
        }
        characteristics = manager.getCameraCharacteristics(cameraId)
        @SuppressLint("MissingPermission")
        runCatching { manager.openCamera(cameraId, cameraCallback, handler) }
            .onFailure { onError("HI-RES camera unavailable") }
    }

    fun setZoom(ratio: Float) {
        zoom = ratio.coerceIn(minZoom, maxZoom)
        updatePreview()
    }

    fun setExposure(index: Int) {
        evIndex = index
        updatePreview()
    }

    fun setFlash(setting: FlashSetting) {
        flash = setting
        updatePreview()
    }

    fun setTargetRotation(rotation: Int) {
        targetRotation = rotation
    }

    fun focusAt(nx: Float, ny: Float) {
        val sensor = activeArray() ?: return
        val w = (sensor.width() * 0.12f).toInt().coerceAtLeast(50)
        val h = (sensor.height() * 0.12f).toInt().coerceAtLeast(50)
        val cx = (sensor.left + nx.coerceIn(0f, 1f) * sensor.width()).roundToInt()
        val cy = (sensor.top + ny.coerceIn(0f, 1f) * sensor.height()).roundToInt()
        val rect = Rect(
            (cx - w / 2).coerceIn(sensor.left, sensor.right - w),
            (cy - h / 2).coerceIn(sensor.top, sensor.bottom - h),
            0,
            0,
        )
        rect.right = (rect.left + w).coerceAtMost(sensor.right)
        rect.bottom = (rect.top + h).coerceAtMost(sensor.bottom)
        afRegion = MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX)
        updatePreview(triggerAf = true)
        handler.removeCallbacks(clearAf)
        handler.postDelayed(clearAf, 3500)
    }

    fun capture() {
        if (capturing) return
        val device = camera ?: return
        val preview = previewSurface ?: return
        if (session != null && combinedSession && usingMaxRes) {
            capturing = true
            fireStill(session ?: return)
            return
        }
        pendingStill = true
        restorePreviewAfterStill = true
        capturing = true
        createSession(device, preview, stillOnly = true, preferMaxRes = true)
    }

    fun close() {
        handler.removeCallbacks(clearAf)
        pendingStill = false
        restorePreviewAfterStill = false
        capturing = false
        runCatching { session?.close() }
        session = null
        runCatching { camera?.close() }
        camera = null
        jpegReader?.close()
        jpegReader = null
        previewSurface = null
        characteristics = null
        afRegion = null
    }

    private val clearAf = Runnable {
        afRegion = null
        updatePreview()
    }

    private fun cameraId(): String? {
        val ids = runCatching { manager.cameraIdList }.getOrDefault(emptyArray())
        // Devices with multiple rear cameras often enumerate a binned auxiliary sensor
        // before the full-resolution main sensor. Pick the ID with the largest JPEG
        // output instead of assuming the first rear-facing ID is the main camera.
        return ids.mapNotNull { id ->
            runCatching {
                val chars = manager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) != lensFacing) return@runCatching null
                val standard = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG).orEmpty()
                val maximum = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                        ?.getOutputSizes(ImageFormat.JPEG).orEmpty()
                } else emptyArray()
                val area = (standard.asSequence() + maximum.asSequence())
                    .maxOfOrNull { it.width.toLong() * it.height.toLong() } ?: 0L
                id to area
            }.getOrNull()
        }.maxByOrNull { it.second }?.first
    }

    private val cameraCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            camera = device
            val preview = previewSurface ?: return
            publishRanges()
            createSession(device, preview, stillOnly = false, preferMaxRes = true)
        }

        override fun onDisconnected(device: CameraDevice) {
            device.close()
            camera = null
        }

        override fun onError(device: CameraDevice, error: Int) {
            device.close()
            camera = null
            onError("HI-RES camera error")
        }
    }

    private fun publishRanges() {
        val chars = characteristics ?: return
        maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.coerceAtLeast(1f) ?: 1f
        minZoom = 1f
        zoom = 1f
        val range = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f
        onReady(minZoom, maxZoom, range?.lower ?: 0, range?.upper ?: 0, step)
    }

    private fun createSession(device: CameraDevice, preview: Surface, stillOnly: Boolean, preferMaxRes: Boolean) {
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        session = null
        val chars = characteristics ?: return
        val standard = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val maximum = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
        } else {
            null
        }
        val ultra = (
            chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR) == true
            )

        val maxJpegs = maximum?.getOutputSizes(ImageFormat.JPEG).orEmpty()
        val standardJpegs = standard?.getOutputSizes(ImageFormat.JPEG).orEmpty()
        val largestMax = maxJpegs.maxByOrNull { it.width.toLong() * it.height.toLong() }
        val largestStd = standardJpegs.maxByOrNull { it.width.toLong() * it.height.toLong() }
        val maxArea = largestMax?.let { it.width.toLong() * it.height.toLong() } ?: 0L
        val stdArea = largestStd?.let { it.width.toLong() * it.height.toLong() } ?: 0L
        val useMax = preferMaxRes && largestMax != null && (ultra || maxArea > stdArea)
        val chosen = if (useMax) largestMax!! else (largestStd ?: largestMax ?: Size(4000, 3000))

        usingMaxRes = useMax
        jpegSize = chosen
        jpegReader?.close()
        jpegReader = ImageReader.newInstance(chosen.width, chosen.height, ImageFormat.JPEG, 2).also { reader ->
            reader.setOnImageAvailableListener({ available ->
                val image = available.acquireLatestImage() ?: return@setOnImageAvailableListener
                image.use {
                    val buffer = it.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also(buffer::get)
                    Log.i(TAG, "HI-RES JPEG ${it.width}x${it.height} (${mp(it.width, it.height)} MP)")
                    onCaptured(bytes, it.width, it.height)
                }
                capturing = false
                if (restorePreviewAfterStill) {
                    restorePreviewAfterStill = false
                    previewSurface?.let { createSession(device, it, stillOnly = false, preferMaxRes = usingMaxRes) }
                }
            }, handler)
        }

        val jpegSurface = jpegReader!!.surface
        val outputs = buildList {
            if (!stillOnly) add(preview)
            add(jpegSurface)
        }
        combinedSession = !stillOnly
        Log.i(
            TAG,
            "HI-RES session jpeg=${chosen.width}x${chosen.height} maxRes=$usingMaxRes stillOnly=$stillOnly",
        )

        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(newSession: CameraCaptureSession) {
                session = newSession
                if (stillOnly && pendingStill) {
                    pendingStill = false
                    fireStill(newSession)
                } else {
                    updatePreview()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.w(TAG, "HI-RES session failed maxRes=$usingMaxRes stillOnly=$stillOnly")
                onSessionFailed(device, preview, stillOnly, preferMaxRes)
            }
        }

        val sessionParams = runCatching {
            device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).also { applyCommon(it) }.build()
        }.getOrNull()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val config = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs.map { OutputConfiguration(it) },
                executor,
                callback,
            )
            if (sessionParams != null) config.sessionParameters = sessionParams
            val supported = runCatching { device.isSessionConfigurationSupported(config) }.getOrDefault(true)
            if (supported) {
                runCatching { device.createCaptureSession(config) }
                    .onFailure { onSessionFailed(device, preview, stillOnly, preferMaxRes) }
                return
            }
        }
        @Suppress("DEPRECATION")
        runCatching { device.createCaptureSession(outputs, callback, handler) }
            .onFailure { onSessionFailed(device, preview, stillOnly, preferMaxRes) }
    }

    private fun onSessionFailed(
        device: CameraDevice,
        preview: Surface,
        stillOnly: Boolean,
        preferMaxRes: Boolean,
    ) {
        when {
            !stillOnly && preferMaxRes -> {
                createSession(device, preview, stillOnly = false, preferMaxRes = false)
            }
            !stillOnly -> {
                combinedSession = false
                createPreviewOnly(device, preview)
            }
            preferMaxRes -> {
                // Do not silently downgrade a requested Hi-Res shot to the normal
                // binned stream. That looks successful but commonly produces 9 MP.
                capturing = false
                pendingStill = false
                onError("Maximum-resolution capture failed. Try another camera mode or device.")
            }
            else -> {
                capturing = false
                pendingStill = false
                onError("HI-RES isn't supported here")
            }
        }
    }

    private fun createPreviewOnly(device: CameraDevice, preview: Surface) {
        combinedSession = false
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(newSession: CameraCaptureSession) {
                session = newSession
                updatePreview()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                onError("HI-RES preview failed")
            }
        }
        @Suppress("DEPRECATION")
        device.createCaptureSession(listOf(preview), callback, handler)
    }

    private fun fireStill(activeSession: CameraCaptureSession) {
        val device = camera ?: run {
            capturing = false
            return
        }
        val jpegSurface = jpegReader?.surface ?: run {
            capturing = false
            return
        }
        runCatching {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(jpegSurface)
                if (combinedSession) previewSurface?.let(::addTarget)
                applyCommon(this)
                set(CaptureRequest.JPEG_QUALITY, 100)
                set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
                set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)
                if (flash == FlashSetting.ON) {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE)
                }
            }.build()
            activeSession.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    val size = result.get(CaptureResult.SCALER_CROP_REGION)
                    Log.i(TAG, "HI-RES capture completed crop=$size jpeg=${jpegSize.width}x${jpegSize.height}")
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: android.hardware.camera2.CaptureFailure,
                ) {
                    capturing = false
                    onError("Couldn't save photo")
                }
            }, handler)
        }.onFailure {
            capturing = false
            onError("Couldn't save photo")
        }
    }

    private fun updatePreview(triggerAf: Boolean = false) {
        val device = camera ?: return
        val activeSession = session ?: return
        val surface = previewSurface ?: return
        if (!combinedSession && restorePreviewAfterStill) return
        runCatching {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                applyCommon(this)
                if (triggerAf) {
                    set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                }
            }.build()
            activeSession.setRepeatingRequest(request, null, handler)
        }
    }

    private fun applyCommon(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evIndex)
        when (flash) {
            FlashSetting.ON -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
            }
            FlashSetting.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }
            FlashSetting.OFF -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
            }
        }
        afRegion?.let { region ->
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
        }
        cropRegion()?.let { builder.set(CaptureRequest.SCALER_CROP_REGION, it) }
        if (usingMaxRes && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.set(CaptureRequest.SENSOR_PIXEL_MODE, CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION)
        }
        enableVendorHighRes(builder)
    }

    private fun enableVendorHighRes(builder: CaptureRequest.Builder) {
        val chars = characteristics ?: return
        for (key in chars.availableCaptureRequestKeys) {
            val name = key.name.lowercase()
            if (!name.contains("remosaic") &&
                !name.contains("high.resolution") &&
                !name.contains("high_resolution") &&
                !name.contains("ultra.high") &&
                !name.contains("sensor.pixel.mode")
            ) {
                continue
            }
            @Suppress("UNCHECKED_CAST")
            runCatching { builder.set(key as CaptureRequest.Key<Int>, 1) }
                .recoverCatching { builder.set(key as CaptureRequest.Key<Byte>, 1.toByte()) }
                .recoverCatching { builder.set(key as CaptureRequest.Key<Boolean>, true) }
        }
    }

    private fun cropRegion(): Rect? {
        val sensor = activeArray() ?: return null
        if (zoom <= 1.01f) return sensor
        val zoom = this.zoom.coerceAtLeast(1f)
        val w = (sensor.width() / zoom).roundToInt().coerceAtLeast(2)
        val h = (sensor.height() / zoom).roundToInt().coerceAtLeast(2)
        val x = sensor.left + (sensor.width() - w) / 2
        val y = sensor.top + (sensor.height() - h) / 2
        return Rect(x, y, x + w, y + h)
    }

    private fun activeArray(): Rect? {
        val chars = characteristics ?: return null
        if (usingMaxRes && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION)?.let { return it }
        }
        return chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
    }

    private fun jpegOrientation(): Int {
        val sensor = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val device = when (targetRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensor + device) % 360
        } else {
            (sensor - device + 360) % 360
        }
    }

    private fun mp(width: Int, height: Int): String =
        "%.1f".format(width.toLong() * height.toLong() / 1_000_000.0)

    companion object {
        private const val TAG = "BrinaCamHiRes"
    }
}
