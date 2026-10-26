package id.geocam.tm3.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private val requiredPermissions: Array<String> = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}.toTypedArray()

private fun Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Camera and any location are required; storage only on Android 8–9. */
private fun Context.ready(): Boolean =
    has(Manifest.permission.CAMERA) &&
        (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) &&
        (Build.VERSION.SDK_INT > Build.VERSION_CODES.P || has(Manifest.permission.WRITE_EXTERNAL_STORAGE))

@Composable
fun GeoCamApp() {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(context.ready()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ready = context.ready()
    }

    LaunchedEffect(Unit) { if (!ready) launcher.launch(requiredPermissions) }
    // Returning from system settings may have changed permissions.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ready = context.ready() }

    if (ready) {
        CameraScreen()
    } else {
        PermissionScreen(
            onRequest = { launcher.launch(requiredPermissions) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Orange.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Orange, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("GeoCam TM-3", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                "Izinkan kamera dan lokasi untuk mencetak waktu dan koordinat TM-3 di foto.\nTanpa login. Data tidak dikirim ke mana pun.",
                color = Muted,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Izinkan", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onOpenSettings) {
                Text("Buka pengaturan aplikasi", color = Muted)
            }
        }
    }
}
