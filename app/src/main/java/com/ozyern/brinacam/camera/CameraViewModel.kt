package com.ozyern.brinacam.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.AudioAttributes
import android.media.MediaActionSound
import android.media.SoundPool
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2CameraInfo
import com.ozyern.brinacam.R
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "BrinaCam"
private const val SAVE_DIR = "DCIM/BrinaCam"

/** Owns the CameraX session and all state shown by the capture screen. */
class CameraViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("brinacam", Context.MODE_PRIVATE)
    private val mainExecutor = ContextCompat.getMainExecutor(app)
    private val sound = MediaActionSound()
    private val shutterPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val shutterSoundId = shutterPool.load(app, R.raw.shutter, 1)

    // ---- UI state -------------------------------------------------------------------------

    var mode by mutableStateOf(CaptureMode.PHOTO)
        private set
    var availableModes by mutableStateOf(listOf(CaptureMode.VIDEO, CaptureMode.PHOTO, CaptureMode.HIRES))
        private set
    var lensFacing by mutableIntStateOf(CameraSelector.LENS_FACING_BACK)
        private set

    var flash by mutableStateOf(enumPref("flash", FlashSetting.OFF))
        private set
    var timerSeconds by mutableIntStateOf(prefs.getInt("timer", 0))
        private set
    var gridOn by mutableStateOf(prefs.getBoolean("grid", true))
        private set
    var aspect by mutableStateOf(enumPref("aspect", AspectSetting.R4_3))
        private set
    var hdrAvailable by mutableStateOf(false)
        private set
    var hdrOn by mutableStateOf(prefs.getBoolean("hdr", false))
        private set
    var mirrorFront by mutableStateOf(prefs.getBoolean("mirror", true))
        private set
    var shutterSound by mutableStateOf(prefs.getBoolean("sound", true))
        private set

    var zoomRatio by mutableFloatStateOf(1f)
        private set
    var minZoom by mutableFloatStateOf(1f)
        private set
    var maxZoom by mutableFloatStateOf(1f)
        private set

    /** 35 mm-equivalent focal length of the 1x lens, for the zoom dial. */
    var focalLength by mutableFloatStateOf(24f)
        private set
    var focusLocked by mutableStateOf(false)
        private set

    var exposureSupported by mutableStateOf(false)
        private set
    var exposureIndex by mutableIntStateOf(0)
        private set
    var exposureMin by mutableIntStateOf(0)
        private set
    var exposureMax by mutableIntStateOf(0)
        private set
    var exposureStep by mutableFloatStateOf(0f)
        private set
    val exposureValue: Float get() = exposureIndex * exposureStep

    var effects by mutableStateOf(emptyList<ColorEffect>())
        private set
    var effect by mutableIntStateOf(CameraMetadata.CONTROL_EFFECT_MODE_OFF)
        private set

    var isRecording by mutableStateOf(false)
        private set
    var recordingSeconds by mutableIntStateOf(0)
        private set
    var countdown by mutableIntStateOf(0)
        private set
    /** Incremented on every capture so the UI can play the shutter animation. */
    var captureTick by mutableIntStateOf(0)
        private set

    var thumbnail by mutableStateOf<Bitmap?>(null)
        private set
    var lastMedia by mutableStateOf<Pair<Uri, String>?>(null)
        private set

    // ---- CameraX objects ------------------------------------------------------------------

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var owner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var targetRotation = Surface.ROTATION_0
    private var timerJob: Job? = null

    init {
        loadLastMedia()
    }

    fun attach(owner: LifecycleOwner, previewView: PreviewView) {
        this.owner = owner
        this.previewView = previewView
        viewModelScope.launch {
            try {
                if (provider == null) provider = ProcessCameraProvider.getInstance(context).await()
                if (extensions == null) {
                    extensions = runCatching {
                        ExtensionsManager.getInstanceAsync(context, provider!!).await()
                    }.getOrNull()
                }
                refreshModes()
                bind()
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
                toast("Camera unavailable")
            }
        }
    }

    // ---- Settings -------------------------------------------------------------------------

    fun selectMode(newMode: CaptureMode) {
        if (newMode == mode || isRecording || newMode !in availableModes) return
        cancelTimer()
        mode = newMode
        bind()
    }

    fun switchLens() {
        if (isRecording) return
        val next = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        val selector = CameraSelector.Builder().requireLensFacing(next).build()
        if (provider?.hasCamera(selector) != true) return
        lensFacing = next
        refreshModes()
        bind()
    }

    fun cycleFlash() {
        flash = FlashSetting.entries[(flash.ordinal + 1) % FlashSetting.entries.size]
        prefs.edit { putString("flash", flash.name) }
        imageCapture?.flashMode = imageFlashMode()
        if (mode == CaptureMode.VIDEO) camera?.cameraControl?.enableTorch(flash == FlashSetting.ON)
    }

    fun cycleTimer() {
        timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
        prefs.edit { putInt("timer", timerSeconds) }
    }

    fun toggleGrid() {
        gridOn = !gridOn
        prefs.edit { putBoolean("grid", gridOn) }
    }

    fun cycleAspect() {
        aspect = AspectSetting.entries[(aspect.ordinal + 1) % AspectSetting.entries.size]
        prefs.edit { putString("aspect", aspect.name) }
        bind()
    }

    fun toggleHdr() {
        if (!hdrAvailable) return
        hdrOn = !hdrOn
        prefs.edit { putBoolean("hdr", hdrOn) }
        if (mode == CaptureMode.PHOTO) bind()
    }

    fun toggleMirror() {
        mirrorFront = !mirrorFront
        prefs.edit { putBoolean("mirror", mirrorFront) }
    }

    fun toggleSound() {
        shutterSound = !shutterSound
        prefs.edit { putBoolean("sound", shutterSound) }
    }

    fun selectEffect(newEffect: Int) {
        if (newEffect == effect) return
        effect = newEffect
        bind()
    }

    fun setZoom(ratio: Float) {
        val cam = camera ?: return
        val clamped = ratio.coerceIn(minZoom, maxZoom)
        zoomRatio = clamped
        cam.cameraControl.setZoomRatio(clamped)
    }

    fun setExposure(index: Int) {
        val cam = camera ?: return
        if (!exposureSupported) return
        val clamped = index.coerceIn(exposureMin, exposureMax)
        if (clamped == exposureIndex) return
        exposureIndex = clamped
        cam.cameraControl.setExposureCompensationIndex(clamped)
    }

    /** Locks focus and exposure on the centre of the frame until tapped again. */
    fun toggleFocusLock() {
        val cam = camera ?: return
        val view = previewView ?: return
        if (focusLocked) {
            cam.cameraControl.cancelFocusAndMetering()
            focusLocked = false
            return
        }
        val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .disableAutoCancel()
            .build()
        cam.cameraControl.startFocusAndMetering(action)
        focusLocked = true
    }

    fun focusAt(x: Float, y: Float) {
        focusLocked = false
        val cam = camera ?: return
        val view = previewView ?: return
        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun setDeviceRotation(rotation: Int) {
        if (rotation == targetRotation) return
        targetRotation = rotation
        imageCapture?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    // ---- Capture --------------------------------------------------------------------------

    fun onShutter() {
        when {
            mode == CaptureMode.VIDEO -> toggleRecording()
            timerJob != null -> cancelTimer()
            timerSeconds > 0 -> {
                timerJob = viewModelScope.launch {
                    for (i in timerSeconds downTo 1) {
                        countdown = i
                        delay(1000)
                    }
                    countdown = 0
                    timerJob = null
                    takePhoto()
                }
            }
            else -> takePhoto()
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        countdown = 0
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        captureTick++
        if (shutterSound) shutterPool.play(shutterSoundId, 1f, 1f, 1, 0, 1f)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + timestamp())
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, SAVE_DIR)
        }
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT && mirrorFront
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).setMetadata(metadata).build()

        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let { setLastMedia(it, "image/jpeg") }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed", exception)
                toast("Couldn't save photo")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun toggleRecording() {
        recording?.let {
            it.stop()
            recording = null
            return
        }
        val capture = videoCapture ?: return

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_" + timestamp())
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, SAVE_DIR)
        }
        val output = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()

        var pending = capture.output.prepareRecording(context, output)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }
        if (shutterSound) sound.play(MediaActionSound.START_VIDEO_RECORDING)
        recordingSeconds = 0
        isRecording = true
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Status -> {
                    recordingSeconds = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos).toInt()
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    recording = null
                    if (shutterSound) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    if (event.hasError()) {
                        Log.e(TAG, "Recording failed: ${event.error}", event.cause)
                        if (event.outputResults.outputUri == Uri.EMPTY) toast("Couldn't save video")
                    }
                    if (event.outputResults.outputUri != Uri.EMPTY) {
                        setLastMedia(event.outputResults.outputUri, "video/mp4")
                    }
                }
                else -> Unit
            }
        }
    }

    // ---- Binding --------------------------------------------------------------------------

    private fun refreshModes() {
        val base = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        fun has(extension: Int) = extensions?.let {
            runCatching { it.isExtensionAvailable(base, extension) }.getOrDefault(false)
        } ?: false

        availableModes = buildList {
            if (has(ExtensionMode.NIGHT)) add(CaptureMode.NIGHT)
            add(CaptureMode.VIDEO)
            add(CaptureMode.PHOTO)
            if (has(ExtensionMode.BOKEH)) add(CaptureMode.PORTRAIT)
            add(CaptureMode.HIRES)
        }
        hdrAvailable = has(ExtensionMode.HDR)
        if (mode !in availableModes) mode = CaptureMode.PHOTO
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bind() {
        val provider = provider ?: return
        val owner = owner ?: return
        val view = previewView ?: return

        val base = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val extensionMode = when (mode) {
            CaptureMode.NIGHT -> ExtensionMode.NIGHT
            CaptureMode.PORTRAIT -> ExtensionMode.BOKEH
            CaptureMode.PHOTO -> if (hdrOn && hdrAvailable) ExtensionMode.HDR else null
            else -> null
        }
        val selector = extensionMode?.let { ext ->
            extensions?.takeIf { runCatching { it.isExtensionAvailable(base, ext) }.getOrDefault(false) }
                ?.getExtensionEnabledCameraSelector(base, ext)
        } ?: base

        // Colour effects go through Camera2 request options, which extensions don't allow.
        val applyEffect = extensionMode == null && mode != CaptureMode.VIDEO &&
            effect != CameraMetadata.CONTROL_EFFECT_MODE_OFF

        val ratioStrategy = if (aspect == AspectSetting.R16_9) {
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        } else {
            AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        }

        val previewBuilder = Preview.Builder().setResolutionSelector(
            ResolutionSelector.Builder().setAspectRatioStrategy(ratioStrategy).build()
        )
        if (applyEffect) {
            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_EFFECT_MODE, effect)
        }
        val preview = previewBuilder.build().also { it.setSurfaceProvider(view.surfaceProvider) }

        imageCapture = null
        videoCapture = null
        val group = UseCaseGroup.Builder().addUseCase(preview)

        if (mode == CaptureMode.VIDEO) {
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(Quality.UHD, Quality.FHD, Quality.HD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder).also {
                it.targetRotation = targetRotation
                group.addUseCase(it)
            }
        } else {
            val resolution = ResolutionSelector.Builder().setAspectRatioStrategy(ratioStrategy)
            if (mode == CaptureMode.HIRES) {
                resolution.setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                resolution.setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            }
            val builder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(imageFlashMode())
                .setResolutionSelector(resolution.build())
                .setTargetRotation(targetRotation)
            if (applyEffect) {
                Camera2Interop.Extender(builder)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_EFFECT_MODE, effect)
            }
            imageCapture = builder.build().also { group.addUseCase(it) }
        }

        if (aspect == AspectSetting.R1_1) {
            group.setViewPort(ViewPort.Builder(Rational(1, 1), Surface.ROTATION_0).build())
        }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(owner, selector, group.build())
        } catch (e: Exception) {
            Log.e(TAG, "Binding failed for $mode", e)
            if (mode != CaptureMode.PHOTO) {
                toast("${mode.label} isn't supported here")
                mode = CaptureMode.PHOTO
                bind()
            }
            return
        }
        observeCamera(owner)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun observeCamera(owner: LifecycleOwner) {
        val cam = camera ?: return
        cam.cameraInfo.zoomState.removeObservers(owner)
        cam.cameraInfo.zoomState.observe(owner) { state ->
            zoomRatio = state.zoomRatio
            minZoom = state.minZoomRatio
            maxZoom = state.maxZoomRatio
        }

        val exposure = cam.cameraInfo.exposureState
        exposureSupported = exposure.isExposureCompensationSupported
        exposureMin = exposure.exposureCompensationRange.lower
        exposureMax = exposure.exposureCompensationRange.upper
        exposureStep = exposure.exposureCompensationStep.toFloat()
        exposureIndex = 0
        if (exposureSupported) cam.cameraControl.setExposureCompensationIndex(0)

        focusLocked = false
        focalLength = runCatching {
            val info = Camera2CameraInfo.from(cam.cameraInfo)
            val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            val sensor = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            if (focal != null && sensor != null) {
                val diagonal = kotlin.math.hypot(sensor.width.toDouble(), sensor.height.toDouble())
                (focal * 43.27 / diagonal).toFloat()
            } else {
                null
            }
        }.getOrNull()?.takeIf { it in 10f..80f } ?: 24f

        val available = runCatching {
            Camera2CameraInfo.from(cam.cameraInfo)
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
        }.getOrNull()
        effects = (available?.toList() ?: emptyList())
            .sortedBy { if (it == CameraMetadata.CONTROL_EFFECT_MODE_OFF) -1 else it }
            .map { ColorEffect(it, colorEffectLabel(it)) }

        if (mode == CaptureMode.VIDEO) cam.cameraControl.enableTorch(flash == FlashSetting.ON)
    }

    private fun imageFlashMode() = when (flash) {
        FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
        FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
    }

    // ---- Gallery --------------------------------------------------------------------------

    private fun setLastMedia(uri: Uri, mime: String) {
        lastMedia = uri to mime
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(uri, Size(256, 256), null) }.getOrNull()
            }
            if (bitmap != null) thumbnail = bitmap
        }
    }

    /** Shows the newest photo or video this app saved, if any. */
    private fun loadLastMedia() {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    val projection = arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.MEDIA_TYPE,
                    )
                    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
                    val args = arrayOf(
                        "$SAVE_DIR%",
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                    )
                    context.contentResolver.query(
                        collection, projection, selection, args,
                        "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                    )?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        val id = cursor.getLong(0)
                        val isVideo = cursor.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        if (isVideo) {
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id) to "video/mp4"
                        } else {
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id) to "image/jpeg"
                        }
                    }
                }.getOrNull()
            }
            if (found != null && lastMedia == null) setLastMedia(found.first, found.second)
        }
    }

    // ---- Helpers --------------------------------------------------------------------------

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    override fun onCleared() {
        recording?.stop()
        sound.release()
        shutterPool.release()
    }
}
