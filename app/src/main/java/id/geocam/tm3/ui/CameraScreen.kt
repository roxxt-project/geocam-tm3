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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.absolutePadding
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.geocam.tm3.SettingsStore
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
import kotlin.math.abs
import kotlin.math.roundToLong

/** "Green" accuracy: the photo is only taken without a warning at or below this. */
private const val GREEN_ACCURACY_M = 5f

/** Space kept free for the shutter bar at the physical bottom of the screen. */
private val CONTROL_RESERVE = 132.dp

private fun Location?.isGreen(): Boolean =
    this != null && hasAccuracy() && accuracy <= GREEN_ACCURACY_M

/**
 * Maps the sensor angle to the nearest quarter turn, with ±30° hysteresis so the HUD
 * does not flicker when the phone is held near 45°. Returns null inside the dead zone.
 */
private fun quarterOf(degrees: Int): Int? =
    listOf(0, 90, 180, 270).firstOrNull { centre ->
        abs(((degrees - centre + 540) % 360) - 180) <= 30
    }

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
    var autoCapture by remember { mutableStateOf(false) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var lastThumb by remember { mutableStateOf<Bitmap?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showAccuracyWarning by remember { mutableStateOf(false) }
    // HUD rotation in degrees: 0 portrait, -90 / 90 landscape, 180 upside down.
    var hudRotation by remember { mutableFloatStateOf(0f) }
    val flash = remember { Animatable(0f) }

    // ---- Camera ----
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        // Cap at ~12 MP: sharp enough, and keeps memory safe on low-end phones.
                        ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
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

    // The activity stays portrait; the HUD and the photo follow how the phone is held.
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                val quarter = quarterOf(degrees) ?: return
                imageCapture.targetRotation = when (quarter) {
                    90 -> Surface.ROTATION_270
                    180 -> Surface.ROTATION_180
                    270 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                hudRotation = when (quarter) {
                    90 -> -90f
                    180 -> 180f
                    270 -> 90f
                    else -> 0f
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

    // A short buzz the moment accuracy turns green.
    val green = location.isGreen()
    LaunchedEffect(green) {
        if (green) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

    // Armed auto-capture fires as soon as accuracy is green.
    LaunchedEffect(autoCapture, green, busy) {
        if (autoCapture && green && !busy) {
            autoCapture = false
            capture()
        }
    }

    fun onShutter() {
        when {
            autoCapture -> {
                autoCapture = false
                scope.launch { snackbar.showSnackbar("Jepret otomatis dibatalkan") }
            }
            green -> capture()
            else -> showAccuracyWarning = true
        }
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

    val accuracy = location?.takeIf { it.hasAccuracy() }?.accuracy
    val iconRotation by animateFloatAsState(hudRotation, tween(300), label = "icons")

    // ---- Layout ----
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ViewfinderGuides(Modifier.fillMaxSize())
        if (flash.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        }

        // HUD: re-laid out for the way the phone is held, with a soft fade between layouts.
        AnimatedContent(
            targetState = hudRotation,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            label = "hud",
        ) { rotation ->
            RotatedFrame(rotation, Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(reserveControls(rotation))
                        .padding(12.dp),
                ) {
                    InfoCard(
                        location = location,
                        address = if (settings.showAddress) address else null,
                        decimalComma = settings.decimalComma,
                        locationOff = locationOff,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StatusPill(
                            location = location,
                            locationOff = locationOff,
                            autoCapture = autoCapture,
                            decimalComma = settings.decimalComma,
                        )
                        Spacer(Modifier.height(10.dp))
                        NoteChip(note = settings.note, onClick = { showSettings = true })
                    }
                }
            }
        }

        // Shutter bar: fixed at the physical bottom; its icons turn with the phone.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                .navigationBarsPadding()
                .padding(top = 20.dp, bottom = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(lastThumb, Modifier.rotate(iconRotation), onClick = ::openLastPhoto)
                Shutter(
                    busy = busy,
                    accuracy = accuracy,
                    hasFix = location != null,
                    armed = autoCapture,
                    onClick = ::onShutter,
                )
                RoundIconButton(Modifier.rotate(iconRotation), onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Pengaturan", tint = Color.White)
                }
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 150.dp),
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
            title = { Text("Tunggu akurasi hijau") },
            text = {
                Text(
                    when {
                        fix == null -> "Lokasi belum terkunci. Tunggu di area terbuka sampai indikator berwarna hijau."
                        else -> "Akurasi saat ini ${if (fix.hasAccuracy()) Format.accuracy(fix.accuracy, settings.decimalComma) else "tidak diketahui"}. " +
                            "Untuk koordinat akurat, tunggu sampai indikator hijau (≤ 5 m). " +
                            "Diam di tempat terbuka, jauh dari gedung dan pohon."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAccuracyWarning = false
                    autoCapture = true
                }) { Text("Jepret otomatis saat hijau", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showAccuracyWarning = false
                        capture()
                    }) { Text("Tetap jepret", color = Muted) }
                    TextButton(onClick = { showAccuracyWarning = false }) { Text("Batal", color = Muted) }
                }
            },
        )
    }
}

/** Keeps HUD content off the shutter bar, whichever frame side the physical bottom maps to. */
private fun reserveControls(rotation: Float): Modifier = when (rotation) {
    90f -> Modifier.absolutePadding(right = CONTROL_RESERVE)
    -90f -> Modifier.absolutePadding(left = CONTROL_RESERVE)
    180f -> Modifier.absolutePadding(top = CONTROL_RESERVE)
    else -> Modifier.absolutePadding(bottom = CONTROL_RESERVE)
}

/**
 * Lays its single child out in a frame turned by [degrees] (multiples of 90), so text stays
 * upright and the HUD uses the long side of the screen in landscape.
 */
@Composable
private fun RotatedFrame(degrees: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val quarterTurn = degrees == 90f || degrees == -90f
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val childWidth = if (quarterTurn) height else width
        val childHeight = if (quarterTurn) width else height
        val placeable = measurables.first().measure(Constraints.fixed(childWidth, childHeight))
        layout(width, height) {
            placeable.placeWithLayer((width - childWidth) / 2, (height - childHeight) / 2) {
                rotationZ = degrees
            }
        }
    }
}

/** Rule-of-thirds grid and a centre crosshair. */
@Composable
private fun ViewfinderGuides(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val grid = Color.White.copy(alpha = 0.14f)
        val line = 1.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3
            val y = size.height * i / 3
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), line)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), line)
        }
        val c = center
        val arm = 14.dp.toPx()
        val gap = 5.dp.toPx()
        val cross = Color.White.copy(alpha = 0.8f)
        val w = 1.5.dp.toPx()
        drawLine(cross, Offset(c.x - arm, c.y), Offset(c.x - gap, c.y), w, StrokeCap.Round)
        drawLine(cross, Offset(c.x + gap, c.y), Offset(c.x + arm, c.y), w, StrokeCap.Round)
        drawLine(cross, Offset(c.x, c.y - arm), Offset(c.x, c.y - gap), w, StrokeCap.Round)
        drawLine(cross, Offset(c.x, c.y + gap), Offset(c.x, c.y + arm), w, StrokeCap.Round)
        drawCircle(cross, radius = 1.5.dp.toPx(), center = c)
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
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xD90B0F14), Color(0xA6000000))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = point?.let { "TM-3  ${it.zone.name}" } ?: "TM-3",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Orange)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                if (location?.isMockCompat() == true) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "LOKASI PALSU",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Poor)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            when {
                location == null -> Text(
                    if (locationOff) "Lokasi HP mati.\nAktifkan Lokasi / GPS." else "Mencari sinyal GPS…",
                    color = Muted,
                    fontSize = 15.sp,
                )
                point == null -> Text("Di luar cakupan TM-3\n(93°–141° BT)", color = Muted, fontSize = 15.sp)
                else -> {
                    CoordinateRow("X", Format.metres(point.easting, decimalComma))
                    CoordinateRow("Y", Format.metres(point.northing, decimalComma))
                }
            }

            if (location != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Muted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        Format.latLon(location.latitude, location.longitude),
                        color = Muted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (!address.isNullOrBlank()) {
                    Text(address, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        AccuracyGauge(accuracy = accuracy, hasFix = location != null, decimalComma = decimalComma)
    }
}

@Composable
private fun CoordinateRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, color = Orange, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
        Text(value, color = Color.White, fontSize = 24.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        Text(" m", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/** Ring that fills and turns green as accuracy improves (full at ≤ 1 m, empty at ≥ 20 m). */
@Composable
private fun AccuracyGauge(accuracy: Float?, hasFix: Boolean, decimalComma: Boolean) {
    val quality = if (accuracy == null) 0f else ((20f - accuracy) / 19f).coerceIn(0.04f, 1f)
    val sweep by animateFloatAsState(quality * 270f, tween(500), label = "sweep")
    val color by animateColorAsState(if (hasFix) accuracyColor(accuracy) else Muted, tween(400), label = "gauge")
    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val arcSize = GeoSize(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = 135f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                accuracy?.let { Format.accuracy(it, decimalComma).removePrefix("±").removeSuffix(" m") } ?: "--",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text("± m", color = Muted, fontSize = 10.sp)
        }
    }
}

/** One-line guidance above the shutter: what to do right now. */
@Composable
private fun StatusPill(location: Location?, locationOff: Boolean, autoCapture: Boolean, decimalComma: Boolean) {
    val accuracy = location?.takeIf { it.hasAccuracy() }?.accuracy
    val (tint, message) = when {
        location == null && locationOff -> Poor to "Aktifkan Lokasi / GPS di HP"
        location == null -> Poor to "Mencari sinyal GPS… tunggu sebentar"
        autoCapture -> Orange to "Jepret otomatis saat hijau · ketuk tombol untuk batal"
        location.isGreen() -> Good to "Akurasi baik · siap jepret"
        else -> {
            val now = accuracy?.let { Format.accuracy(it, decimalComma) } ?: "±? m"
            Pair(accuracyColor(accuracy), "Tunggu sampai hijau (≤ 5 m) · sekarang $now")
        }
    }
    val animatedTint by animateColorAsState(tint, tween(400), label = "pill")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (location.isGreen() && !autoCapture) 1f else 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC0B0F14))
            .border(1.dp, animatedTint.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(animatedTint.copy(alpha = dotAlpha)),
        )
        Spacer(Modifier.width(8.dp))
        Text(message, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NoteChip(note: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Edit, contentDescription = null, tint = Orange, modifier = Modifier.size(15.dp))
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
private fun Thumbnail(bitmap: Bitmap?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(shape)
            .background(Glass)
            .border(2.dp, Color.White.copy(alpha = 0.85f), shape)
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
private fun RoundIconButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Glass)
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Ring colour shows accuracy; the centre turns orange and pulses while auto-capture is armed. */
@Composable
private fun Shutter(busy: Boolean, accuracy: Float?, hasFix: Boolean, armed: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    val ring by animateColorAsState(if (hasFix) accuracyColor(accuracy) else Poor, tween(400), label = "ring")
    val pulse = rememberInfiniteTransition(label = "armed")
    val armedScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (armed) 0.82f else 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "armedScale",
    )
    Box(
        modifier = Modifier
            .size(86.dp)
            .scale(pressScale)
            .border(5.dp, ring, CircleShape)
            .padding(9.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .scale(armedScale)
                .clip(CircleShape)
                .background(
                    when {
                        busy -> Color.White.copy(alpha = 0.5f)
                        armed -> Orange
                        else -> Color.White
                    },
                ),
        )
        if (busy) CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = Orange)
    }
}
