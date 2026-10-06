package com.ozyern.brinacam.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.AudioAttributes
import android.media.MediaActionSound
import android.media.SoundPool
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
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
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.ozyern.brinacam.R
import com.ozyern.brinacam.data.CameraSettings
import com.ozyern.brinacam.data.MediaRepository
import com.ozyern.brinacam.data.SavedMedia
import com.ozyern.brinacam.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

private const val TAG = "BrinaCam"

/**
 * Owns the CameraX session. The UI renders [state] and reports user actions
 * through [onEvent]; nothing else crosses the boundary.
 */
class CameraViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val media = MediaRepository(app)
    private val mainExecutor = ContextCompat.getMainExecutor(app)

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

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

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var owner: LifecycleOwner? = null
    private var targetRotation = Surface.ROTATION_0
    private var timerJob: Job? = null
    private var lastZoomRequest = 0L

    init {
        viewModelScope.launch {
            val settings = settingsRepo.current()
            _state.update { it.copy(settings = settings) }
            bind()
        }
        viewModelScope.launch {
            media.latest()?.let { latest -> _state.update { it.copy(lastMedia = latest) } }
        }
        viewModelScope.launch {
            hardwareShutter.collect { onEvent(CameraEvent.Shutter) }
        }
    }

    fun attach(owner: LifecycleOwner) {
        this.owner = owner
        viewModelScope.launch {
            try {
                if (provider == null) provider = ProcessCameraProvider.getInstance(getApplication()).await()
                if (extensions == null) {
                    extensions = runCatching {
                        ExtensionsManager.getInstanceAsync(getApplication(), provider!!).await()
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

    fun onEvent(event: CameraEvent) {
        val s = _state.value
        when (event) {
            is CameraEvent.SelectMode -> {
                if (event.mode == s.mode || s.isRecording || event.mode !in s.availableModes) return
                cancelTimer()
                _state.update { it.copy(mode = event.mode) }
                bind()
            }
            CameraEvent.SwitchLens -> switchLens()
            CameraEvent.Shutter -> onShutter()
            CameraEvent.CycleFlash -> {
                val next = FlashSetting.entries[(s.settings.flash.ordinal + 1) % FlashSetting.entries.size]
                updateSettings { it.copy(flash = next) }
                imageCapture?.flashMode = imageFlashMode(next)
                if (s.mode == CaptureMode.VIDEO) camera?.cameraControl?.enableTorch(next == FlashSetting.ON)
            }
            CameraEvent.CycleTimer -> updateSettings {
                it.copy(timerSeconds = when (it.timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 })
            }
            CameraEvent.ToggleGrid -> updateSettings { it.copy(gridOn = !it.gridOn) }
            CameraEvent.CycleAspect -> {
                updateSettings {
                    it.copy(aspect = AspectSetting.entries[(it.aspect.ordinal + 1) % AspectSetting.entries.size])
                }
                bind()
            }
            CameraEvent.ToggleHdr -> {
                if (!s.hdrAvailable) return
                updateSettings { it.copy(hdrOn = !it.hdrOn) }
                if (s.mode == CaptureMode.PHOTO) bind()
            }
            CameraEvent.ToggleUltraHdr -> {
                updateSettings { it.copy(ultraHdr = !it.ultraHdr) }
                if (s.mode != CaptureMode.VIDEO) bind()
            }
            CameraEvent.ToggleMirror -> updateSettings { it.copy(mirrorFront = !it.mirrorFront) }
            CameraEvent.ToggleSound -> updateSettings { it.copy(shutterSound = !it.shutterSound) }
            CameraEvent.ToggleFocusLock -> toggleFocusLock()
            is CameraEvent.SelectEffect -> {
                if (event.effect == s.effect) return
                _state.update { it.copy(effect = event.effect) }
                bind()
            }
            is CameraEvent.SetZoom -> setZoom(event.ratio)
            is CameraEvent.SetExposure -> setExposure(event.index)
            is CameraEvent.FocusAt -> focusAt(event.x, event.y)
            is CameraEvent.DeviceRotation -> {
                if (event.surfaceRotation == targetRotation) return
                targetRotation = event.surfaceRotation
                imageCapture?.targetRotation = targetRotation
                videoCapture?.targetRotation = targetRotation
            }
        }
    }

    // ---- Settings -------------------------------------------------------------------------

    private fun updateSettings(change: (CameraSettings) -> CameraSettings) {
        val updated = change(_state.value.settings)
        _state.update { it.copy(settings = updated) }
        viewModelScope.launch { settingsRepo.save(updated) }
    }

    private fun switchLens() {
        val s = _state.value
        if (s.isRecording) return
        val next = if (s.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        if (provider?.hasCamera(CameraSelector.Builder().requireLensFacing(next).build()) != true) return
        _state.update { it.copy(lensFacing = next) }
        refreshModes()
        bind()
    }

    // ---- Zoom, exposure, focus ------------------------------------------------------------

    private fun setZoom(ratio: Float) {
        val cam = camera ?: return
        val zoom = _state.value.zoom
        val clamped = ratio.coerceIn(zoom.min, zoom.max)
        lastZoomRequest = SystemClock.uptimeMillis()
        _state.update { it.copy(zoom = it.zoom.copy(ratio = clamped)) }
        cam.cameraControl.setZoomRatio(clamped)
    }

    private fun setExposure(index: Int) {
        val cam = camera ?: return
        val exposure = _state.value.exposure
        if (!exposure.supported) return
        val clamped = index.coerceIn(exposure.min, exposure.max)
        if (clamped == exposure.index) return
        _state.update { it.copy(exposure = it.exposure.copy(index = clamped)) }
        cam.cameraControl.setExposureCompensationIndex(clamped)
    }

    private fun meteringFactory(): SurfaceOrientedMeteringPointFactory? {
        val resolution = _state.value.surfaceRequest?.resolution ?: return null
        return SurfaceOrientedMeteringPointFactory(resolution.width.toFloat(), resolution.height.toFloat())
    }

    private fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        val factory = meteringFactory() ?: return
        _state.update { it.copy(focusLocked = false) }
        val action = FocusMeteringAction.Builder(
            factory.createPoint(x, y),
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        ).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    /** Locks focus and exposure on the centre of the frame until tapped again. */
    private fun toggleFocusLock() {
        val cam = camera ?: return
        if (_state.value.focusLocked) {
            cam.cameraControl.cancelFocusAndMetering()
            _state.update { it.copy(focusLocked = false) }
            return
        }
        val resolution = _state.value.surfaceRequest?.resolution ?: return
        val factory = meteringFactory() ?: return
        val action = FocusMeteringAction.Builder(
            factory.createPoint(resolution.width / 2f, resolution.height / 2f),
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        ).disableAutoCancel().build()
        cam.cameraControl.startFocusAndMetering(action)
        _state.update { it.copy(focusLocked = true) }
    }

    // ---- Capture --------------------------------------------------------------------------

    private fun onShutter() {
        val s = _state.value
        when {
            s.mode == CaptureMode.VIDEO -> toggleRecording()
            timerJob != null -> cancelTimer()
            s.settings.timerSeconds > 0 -> {
                timerJob = viewModelScope.launch {
                    for (i in s.settings.timerSeconds downTo 1) {
                        _state.update { it.copy(countdown = i) }
                        delay(1000)
                    }
                    _state.update { it.copy(countdown = 0) }
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
        _state.update { it.copy(countdown = 0) }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val s = _state.value
        _state.update { it.copy(captureTick = it.captureTick + 1) }
        if (s.settings.shutterSound) shutterPool.play(shutterSoundId, 1f, 1f, 1, 0, 1f)

        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = s.lensFacing == CameraSelector.LENS_FACING_FRONT && s.settings.mirrorFront
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            getApplication<Application>().contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            media.newImageValues(),
        ).setMetadata(metadata).build()

        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let { showSaved(it, "image/jpeg") }
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
        val app = getApplication<Application>()
        val output = MediaStoreOutputOptions.Builder(app.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(media.newVideoValues())
            .build()

        var pending = capture.output.prepareRecording(app, output)
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        val withSound = _state.value.settings.shutterSound
        if (withSound) sound.play(MediaActionSound.START_VIDEO_RECORDING)
        _state.update { it.copy(isRecording = true, recordingSeconds = 0) }
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Status -> _state.update {
                    it.copy(recordingSeconds = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos).toInt())
                }
                is VideoRecordEvent.Finalize -> {
                    _state.update { it.copy(isRecording = false) }
                    recording = null
                    if (withSound) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    val uri = event.outputResults.outputUri
                    if (uri != Uri.EMPTY) {
                        showSaved(uri, "video/mp4")
                    } else if (event.hasError()) {
                        Log.e(TAG, "Recording failed: ${event.error}", event.cause)
                        toast("Couldn't save video")
                    }
                }
                else -> Unit
            }
        }
    }

    private fun showSaved(uri: Uri, mime: String) {
        _state.update { it.copy(lastMedia = SavedMedia(uri, mime, it.lastMedia?.thumbnail)) }
        viewModelScope.launch {
            val thumb = media.thumbnail(uri) ?: return@launch
            _state.update { s ->
                val last = s.lastMedia
                if (last?.uri == uri) s.copy(lastMedia = last.copy(thumbnail = thumb)) else s
            }
        }
    }

    // ---- Binding --------------------------------------------------------------------------

    private fun hasExtension(selector: CameraSelector, extension: Int): Boolean =
        extensions?.let { runCatching { it.isExtensionAvailable(selector, extension) }.getOrDefault(false) } ?: false

    private fun refreshModes() {
        val base = CameraSelector.Builder().requireLensFacing(_state.value.lensFacing).build()
        val modes = buildList {
            if (hasExtension(base, ExtensionMode.NIGHT)) add(CaptureMode.NIGHT)
            add(CaptureMode.VIDEO)
            add(CaptureMode.PHOTO)
            if (hasExtension(base, ExtensionMode.BOKEH)) add(CaptureMode.PORTRAIT)
            add(CaptureMode.HIRES)
        }
        _state.update {
            it.copy(
                availableModes = modes,
                hdrAvailable = hasExtension(base, ExtensionMode.HDR),
                mode = if (it.mode in modes) it.mode else CaptureMode.PHOTO,
            )
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bind() {
        val provider = provider ?: return
        val owner = owner ?: return
        val s = _state.value

        val base = CameraSelector.Builder().requireLensFacing(s.lensFacing).build()
        val extensionMode = when (s.mode) {
            CaptureMode.NIGHT -> ExtensionMode.NIGHT
            CaptureMode.PORTRAIT -> ExtensionMode.BOKEH
            CaptureMode.PHOTO -> if (s.settings.hdrOn && s.hdrAvailable) ExtensionMode.HDR else null
            else -> null
        }?.takeIf { hasExtension(base, it) }
        val selector = extensionMode?.let { extensions!!.getExtensionEnabledCameraSelector(base, it) } ?: base

        // Colour effects go through Camera2 request options, which extensions don't allow.
        val applyEffect = extensionMode == null && s.mode != CaptureMode.VIDEO &&
            s.effect != CameraMetadata.CONTROL_EFFECT_MODE_OFF
        val ratioStrategy = if (s.settings.aspect == AspectSetting.R16_9) {
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        } else {
            AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        }

        val previewBuilder = Preview.Builder().setResolutionSelector(
            ResolutionSelector.Builder().setAspectRatioStrategy(ratioStrategy).build()
        )
        if (applyEffect) {
            Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_EFFECT_MODE, s.effect)
        }
        val preview = previewBuilder.build().also { preview ->
            preview.setSurfaceProvider { request -> _state.update { it.copy(surfaceRequest = request) } }
        }

        imageCapture = null
        videoCapture = null
        val group = UseCaseGroup.Builder().addUseCase(preview)

        if (s.mode == CaptureMode.VIDEO) {
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
            if (s.mode == CaptureMode.HIRES) {
                resolution.setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                resolution.setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            }
            val builder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(imageFlashMode(s.settings.flash))
                .setResolutionSelector(resolution.build())
                .setTargetRotation(targetRotation)

            // Ultra HDR (JPEG_R) photos where the camera supports them.
            val ultraHdrAvailable = extensionMode == null && runCatching {
                ImageCapture.getImageCaptureCapabilities(provider.getCameraInfo(selector))
                    .supportedOutputFormats.contains(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
            }.getOrDefault(false)
            _state.update { it.copy(ultraHdrAvailable = ultraHdrAvailable) }
            if (ultraHdrAvailable && s.settings.ultraHdr && !applyEffect) {
                builder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
            }
            if (applyEffect) {
                Camera2Interop.Extender(builder).setCaptureRequestOption(CaptureRequest.CONTROL_EFFECT_MODE, s.effect)
            }
            imageCapture = builder.build().also { group.addUseCase(it) }
        }

        if (s.settings.aspect == AspectSetting.R1_1) {
            group.setViewPort(ViewPort.Builder(Rational(1, 1), Surface.ROTATION_0).build())
        }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(owner, selector, group.build())
        } catch (e: Exception) {
            Log.e(TAG, "Binding failed for ${s.mode}", e)
            if (s.mode != CaptureMode.PHOTO) {
                toast("${s.mode.label} isn't supported here")
                _state.update { it.copy(mode = CaptureMode.PHOTO) }
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
        cam.cameraInfo.zoomState.observe(owner) { zoom ->
            // While the user is zooming, the camera reports values that lag behind
            // the gesture; keep showing the requested value so the dial doesn't jitter.
            val follow = SystemClock.uptimeMillis() - lastZoomRequest > 400
            _state.update {
                it.copy(
                    zoom = it.zoom.copy(
                        ratio = if (follow) zoom.zoomRatio else it.zoom.ratio,
                        min = zoom.minZoomRatio,
                        max = zoom.maxZoomRatio,
                    )
                )
            }
        }

        val exposure = cam.cameraInfo.exposureState
        if (exposure.isExposureCompensationSupported) cam.cameraControl.setExposureCompensationIndex(0)

        val info = runCatching { Camera2CameraInfo.from(cam.cameraInfo) }.getOrNull()
        val focalLength = runCatching {
            val focal = info?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            val sensor = info?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            if (focal != null && sensor != null) {
                (focal * 43.27 / hypot(sensor.width.toDouble(), sensor.height.toDouble())).toFloat()
            } else {
                null
            }
        }.getOrNull()?.takeIf { it in 10f..80f } ?: 24f
        val effects = (info?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)?.toList() ?: emptyList())
            .sortedBy { if (it == CameraMetadata.CONTROL_EFFECT_MODE_OFF) -1 else it }
            .map { ColorEffect(it, colorEffectLabel(it)) }

        _state.update {
            it.copy(
                exposure = ExposureState(
                    supported = exposure.isExposureCompensationSupported,
                    index = 0,
                    min = exposure.exposureCompensationRange.lower,
                    max = exposure.exposureCompensationRange.upper,
                    step = exposure.exposureCompensationStep.toFloat(),
                ),
                zoom = it.zoom.copy(focalLength = focalLength),
                effects = effects,
                focusLocked = false,
            )
        }

        if (_state.value.mode == CaptureMode.VIDEO) {
            cam.cameraControl.enableTorch(_state.value.settings.flash == FlashSetting.ON)
        }
    }

    private fun imageFlashMode(flash: FlashSetting) = when (flash) {
        FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
        FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
    }

    private fun toast(text: String) = Toast.makeText(getApplication(), text, Toast.LENGTH_SHORT).show()

    override fun onCleared() {
        recording?.stop()
        sound.release()
        shutterPool.release()
    }

    companion object {
        private val hardwareShutterEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Volume-key presses forwarded by the activity. */
        val hardwareShutter: SharedFlow<Unit> = hardwareShutterEvents.asSharedFlow()

        fun pressHardwareShutter() {
            hardwareShutterEvents.tryEmit(Unit)
        }
    }
}
