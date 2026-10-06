package com.ozyern.brinacam

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ozyern.brinacam.ui.BrinaCamTheme
import com.ozyern.brinacam.ui.BrinaColors
import com.ozyern.brinacam.ui.CameraScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            BrinaCamTheme {
                BrinaCamApp()
            }
        }
    }
}

@Composable
private fun BrinaCamApp() {
    val context = LocalContext.current
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] == true || granted(Manifest.permission.CAMERA)
    }
    val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(Unit) {
        if (!cameraGranted || !granted(Manifest.permission.RECORD_AUDIO)) launcher.launch(permissions)
    }

    if (cameraGranted) {
        CameraScreen()
    } else {
        PermissionScreen(onAllow = { launcher.launch(permissions) })
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("BrinaCam", color = BrinaColors.Accent, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Allow camera access to start shooting. Microphone access is used for video sound.",
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAllow,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrinaColors.Accent),
            ) {
                Text("Allow camera", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
