package com.ozyern.brinacam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.brinacam.camera.CameraEvent
import com.ozyern.brinacam.camera.CameraUiState
import com.ozyern.brinacam.data.MediaRepository

/** Camera settings as a Material 3 bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(state: CameraUiState, onEvent: (CameraEvent) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = state.settings
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1C),
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                "Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SettingSwitch(
                title = "Ultra HDR photos",
                subtitle = if (state.ultraHdrAvailable) {
                    "Saves extra brightness data for HDR screens"
                } else {
                    "Not supported by this camera"
                },
                checked = settings.ultraHdr && state.ultraHdrAvailable,
                enabled = state.ultraHdrAvailable,
                onToggle = { onEvent(CameraEvent.ToggleUltraHdr) },
            )
            SettingSwitch(
                title = "Auto HDR",
                subtitle = if (state.hdrAvailable) "Uses the phone's HDR processing" else "Not offered by this phone",
                checked = settings.hdrOn && state.hdrAvailable,
                enabled = state.hdrAvailable,
                onToggle = { onEvent(CameraEvent.ToggleHdr) },
            )
            SettingSwitch(
                title = "Grid",
                subtitle = "Rule-of-thirds lines on the viewfinder",
                checked = settings.gridOn,
                onToggle = { onEvent(CameraEvent.ToggleGrid) },
            )
            SettingSwitch(
                title = "Mirror front camera",
                subtitle = "Save selfies as you see them",
                checked = settings.mirrorFront,
                onToggle = { onEvent(CameraEvent.ToggleMirror) },
            )
            SettingSwitch(
                title = "Shutter sound",
                subtitle = "Click when taking a photo. Volume keys also act as the shutter",
                checked = settings.shutterSound,
                onToggle = { onEvent(CameraEvent.ToggleSound) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = Color(0xFF2E2E2E))
            Text(
                "Saved to ${MediaRepository.SAVE_DIR}",
                color = BrinaColors.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "BrinaCam · CameraX, Jetpack Compose, Manrope and Kyant's Liquid Glass",
                color = BrinaColors.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else BrinaColors.TextDim,
            )
            Text(subtitle, fontSize = 13.sp, color = BrinaColors.TextDim)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrinaColors.Accent,
                uncheckedThumbColor = Color(0xFFBDBDBD),
                uncheckedTrackColor = Color(0xFF3A3A3A),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
