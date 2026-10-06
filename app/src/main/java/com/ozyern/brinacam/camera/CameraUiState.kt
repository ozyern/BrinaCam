package com.ozyern.brinacam.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
import androidx.compose.runtime.Immutable
import com.ozyern.brinacam.data.CameraSettings
import com.ozyern.brinacam.data.SavedMedia

@Immutable
data class ZoomState(
    val ratio: Float = 1f,
    val min: Float = 1f,
    val max: Float = 1f,
    /** 35 mm-equivalent focal length of the 1x lens. */
    val focalLength: Float = 24f,
)

@Immutable
data class ExposureState(
    val supported: Boolean = false,
    val index: Int = 0,
    val min: Int = 0,
    val max: Int = 0,
    val step: Float = 0f,
) {
    val value: Float get() = index * step
}

/** Everything the capture screen shows, as one immutable snapshot. */
@Immutable
data class CameraUiState(
    val surfaceRequest: SurfaceRequest? = null,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val availableModes: List<CaptureMode> = listOf(CaptureMode.VIDEO, CaptureMode.PHOTO, CaptureMode.HIRES),
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val settings: CameraSettings = CameraSettings(),
    val hdrAvailable: Boolean = false,
    val ultraHdrAvailable: Boolean = false,
    val zoom: ZoomState = ZoomState(),
    val exposure: ExposureState = ExposureState(),
    val effects: List<ColorEffect> = emptyList(),
    val effect: Int = 0,
    val focusLocked: Boolean = false,
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val countdown: Int = 0,
    /** Incremented on every capture so the UI can play the shutter animation. */
    val captureTick: Int = 0,
    val lastMedia: SavedMedia? = null,
)

/** Everything the user can do on the capture screen. */
sealed interface CameraEvent {
    data class SelectMode(val mode: CaptureMode) : CameraEvent
    data object SwitchLens : CameraEvent
    data object Shutter : CameraEvent
    data object CycleFlash : CameraEvent
    data object CycleTimer : CameraEvent
    data object ToggleGrid : CameraEvent
    data object CycleAspect : CameraEvent
    data object ToggleHdr : CameraEvent
    data object ToggleUltraHdr : CameraEvent
    data object ToggleMirror : CameraEvent
    data object ToggleSound : CameraEvent
    data object ToggleFocusLock : CameraEvent
    data class SelectEffect(val effect: Int) : CameraEvent
    data class SetZoom(val ratio: Float) : CameraEvent
    data class SetExposure(val index: Int) : CameraEvent
    /** Tap position in camera-surface coordinates. */
    data class FocusAt(val x: Float, val y: Float) : CameraEvent
    data class DeviceRotation(val surfaceRotation: Int) : CameraEvent
}
