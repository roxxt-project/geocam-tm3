package id.geocam.tm3.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.geocam.tm3.SettingsStore
import id.geocam.tm3.StampSettings
import id.geocam.tm3.geo.Tm3
import id.geocam.tm3.location.AddressLookup
import id.geocam.tm3.location.LocationTracker
import id.geocam.tm3.stamp.Format
import id.geocam.tm3.stamp.PhotoSaver
import id.geocam.tm3.stamp.StampInput
import id.geocam.tm3.stamp.isMockCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.roundToLong

private const val GOOD_ACCURACY_M = 10f

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val store = remember { SettingsStore(appContext) }
    var settings by remember { mutableStateOf(store.load()) }
    var location by remember { mutableStateOf<Location?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var locationOff by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var lastThumb by remember { mutableStateOf<Bitmap?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showAccuracyWarning by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }

    // ---- Camera ----
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    val resolution = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                // Cap at ~12 MP: sharp enough, and keeps memory safe on low-end phones.
                ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
            )
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolution)
            .build()
    }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build(),
                )
                .build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                scope.launch { snackbar.showSnackbar("Kamera tidak bisa dibuka") }
            }
        }, mainExecutor)
        onDispose {
            runCatching { if (future.isDone) future.get().unbindAll() }
        }
    }

    DisposableEffect(Unit) {
        onDispose { worker.shutdown() }
    }

    // Keep the stamp upright: tell CameraX how the phone is held (UI stays portrait).
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = when (degrees) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    // ---- Location: only while the screen is visible ----
    val tracker = remember { LocationTracker(appContext) }
    LifecycleStartEffect(Unit) {
        locationOff = !tracker.isLocationEnabled()
        tracker.start { location = it }
        onStopOrDispose { tracker.stop() }
    }

    // Address: look up again only after moving ~50 m, so lookups don't pile up every second.
    val cell = location?.let { (it.latitude * 2000).roundToLong() to (it.longitude * 2000).roundToLong() }
    LaunchedEffect(cell, settings.showAddress) {
        val fix = location
        if (!settings.showAddress || fix == null) {
            address = null
            return@LaunchedEffect
        }
        address = withContext(Dispatchers.IO) { AddressLookup.lookup(appContext, fix.latitude, fix.longitude) }
    }

    // ---- Capture ----
    fun capture() {
        if (busy) return
        busy = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            flash.snapTo(0.7f)
            flash.animateTo(0f, tween(260))
        }
        val input = StampInput(
            timeMillis = System.currentTimeMillis(),
            location = location,
            address = if (settings.showAddress) address else null,
            note = settings.note,
            decimalComma = settings.decimalComma,
        )
        imageCapture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val result = runCatching {
                    val jpeg = image.use { proxy ->
                        val buffer = proxy.planes[0].buffer
                        ByteArray(buffer.remaining()).also { buffer.get(it) } to proxy.imageInfo.rotationDegrees
                    }
                    PhotoSaver.save(appContext, jpeg.first, jpeg.second, input)
                }
                mainExecutor.execute {
                    busy = false
                    result.onSuccess {
                        lastUri = it.uri
                        lastThumb = it.thumbnail
                        scope.launch { snackbar.showSnackbar("Tersimpan di Galeri › DCIM/${PhotoSaver.FOLDER}") }
                    }.onFailure {
                        scope.launch { snackbar.showSnackbar("Gagal menyimpan: ${it.message ?: "kesalahan tidak dikenal"}") }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                mainExecutor.execute {
                    busy = false
                    scope.launch { snackbar.showSnackbar("Gagal mengambil foto") }
                }
            }
        })
    }

    fun onShutter() {
        val fix = location
        if (fix == null || !fix.hasAccuracy() || fix.accuracy > GOOD_ACCURACY_M) showAccuracyWarning = true
        else capture()
    }

    fun openLastPhoto() {
        val intent = lastUri
            ?.let { Intent(Intent.ACTION_VIEW).setDataAndType(it, "image/jpeg") }
            ?: Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Tidak ada aplikasi galeri") }
        }
    }

    // ---- Layout ----
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (flash.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        }

        InfoCard(
            location = location,
            address = if (settings.showAddress) address else null,
            decimalComma = settings.decimalComma,
            locationOff = locationOff,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NoteChip(note = settings.note, onClick = { showSettings = true })
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(lastThumb, onClick = ::openLastPhoto)
                Shutter(busy = busy, onClick = ::onShutter)
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Glass, contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Pengaturan")
                }
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 170.dp),
        )
    }

    if (showSettings) {
        SettingsDialog(
            initial = settings,
            onDismiss = { showSettings = false },
            onSave = {
                settings = it
                store.save(it)
                showSettings = false
            },
        )
    }

    if (showAccuracyWarning) {
        val fix = location
        AlertDialog(
            onDismissRequest = { showAccuracyWarning = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Fair) },
            title = { Text("Akurasi rendah") },
            text = {
                Text(
                    if (fix == null) "Lokasi belum terkunci. Foto akan disimpan tanpa koordinat."
                    else "Akurasi saat ini ${if (fix.hasAccuracy()) Format.accuracy(fix.accuracy, settings.decimalComma) else "tidak diketahui"}. " +
                        "Tunggu beberapa detik di area terbuka untuk hasil lebih baik.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAccuracyWarning = false
                    capture()
                }) { Text("Tetap jepret") }
            },
            dismissButton = {
                TextButton(onClick = { showAccuracyWarning = false }) { Text("Tunggu") }
            },
        )
    }
}

@Composable
private fun InfoCard(
    location: Location?,
    address: String?,
    decimalComma: Boolean,
    locationOff: Boolean,
    modifier: Modifier = Modifier,
) {
    val point = location?.let { Tm3.project(it.latitude, it.longitude) }
    val accuracy = location?.takeIf { it.hasAccuracy() }?.accuracy
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Glass)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = point?.let { "TM-3 · ${it.zone.name}" } ?: "TM-3",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Orange)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            if (location != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accuracyColor(accuracy)))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = accuracy?.let { Format.accuracy(it, decimalComma) } ?: "±? m",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        when {
            location == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Orange)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (locationOff) "Lokasi HP mati. Aktifkan Lokasi/GPS." else "Mencari sinyal GPS…",
                    color = Muted,
                    fontSize = 14.sp,
                )
            }
            point == null -> Text("Di luar cakupan TM-3 (93°–141° BT)", color = Muted, fontSize = 14.sp)
            else -> {
                CoordinateRow("X", Format.metres(point.easting, decimalComma))
                CoordinateRow("Y", Format.metres(point.northing, decimalComma))
            }
        }

        if (location != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                Format.latLon(location.latitude, location.longitude),
                color = Muted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            if (!address.isNullOrBlank()) {
                Text(address, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (location.isMockCompat()) {
                Text("Lokasi palsu terdeteksi", color = Poor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CoordinateRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, color = Orange, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Text(value, color = Color.White, fontSize = 24.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        Text(" m", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun NoteChip(note: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Edit, contentDescription = null, tint = Orange, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (note.isBlank()) "Tambah catatan" else note,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
    }
}

@Composable
private fun Thumbnail(bitmap: Bitmap?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Glass)
            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Foto terakhir",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Shutter(busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "shutter")
    Box(
        modifier = Modifier
            .size(84.dp)
            .scale(scale)
            .border(4.dp, Color.White, CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(if (busy) Orange.copy(alpha = 0.5f) else Orange)
            .clickable(interactionSource = interaction, indication = null, enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = Color.White)
    }
}
