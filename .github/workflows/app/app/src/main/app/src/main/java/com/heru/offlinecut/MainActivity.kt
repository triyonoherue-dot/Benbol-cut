package com.heru.offlinecut

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbFilter
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ColorPreset(val label: String) {
    NORMAL("Normal"),
    GRAYSCALE("Monokrom"),
    INVERT("Invert"),
    HIGH_CONTRAST("Kontras Tinggi")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OfflineCutScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun OfflineCutScreen() {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var trimRange by remember { mutableStateOf(0f..1f) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var preset by remember { mutableStateOf(ColorPreset.NORMAL) }
    var isExporting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Pilih video lokal dari galeri untuk mulai mengedit.") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            trimRange = 0f..1f
            statusText = "Video dimuat secara lokal."
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("OfflineCut — Editor Lokal") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    enabled = !isExporting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pilih Video")
                }

                OutlinedButton(
                    onClick = {
                        val uri = selectedUri
                        if (uri == null) {
                            Toast.makeText(context, "Pilih video terlebih dahulu", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        exportEditedVideo(
                            context = context,
                            sourceUri = uri,
                            trimRange = trimRange,
                            speed = speed,
                            preset = preset,
                            onStart = {
                                isExporting = true
                                statusText = "Mengekspor video secara offline..."
                            },
                            onDone = { savedName ->
                                isExporting = false
                                statusText = "Tersimpan di Galeri (Movies/OfflineCut): $savedName"
                                Toast.makeText(context, "Ekspor selesai: $savedName", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isExporting = false
                                statusText = "Gagal ekspor: $err"
                                Toast.makeText(context, "Gagal ekspor video", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = selectedUri != null && !isExporting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ekspor MP4")
                }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Text("Potong Rentang Klip: ${(trimRange.start * 100).toInt()}% - ${(trimRange.endInclusive * 100).toInt()}%")
            RangeSlider(
                value = trimRange,
                onValueChange = { range ->
                    if (range.endInclusive - range.start >= 0.05f) {
                        trimRange = range
                    }
                },
                enabled = !isExporting
            )

            Text("Kecepatan Putar: ${String.format(Locale.US, "%.1fx", speed)}")
            Slider(
                value = speed,
                onValueChange = { newSpeed ->
                    speed = newSpeed
                    exoPlayer.playbackParameters = PlaybackParameters(newSpeed)
                },
                valueRange = 0.5f..2.0f,
                enabled = !isExporting
            )

            Text("Preset Warna")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorPreset.entries.forEach { item ->
                    FilterChip(
                        selected = preset == item,
                        onClick = { preset = item },
                        label = { Text(item.label) },
                        enabled = !isExporting
                    )
                }
            }

            if (isExporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(UnstableApi::class)
private fun exportEditedVideo(
    context: android.content.Context,
    sourceUri: Uri,
    trimRange: ClosedFloatingPointRange<Float>,
    speed: Float,
    preset: ColorPreset,
    onStart: () -> Unit,
    onDone: (String) -> Unit,
    onError: (String) -> Unit
) {
    onStart()

    val tempOutFile = File(context.cacheDir, "offlinecut_temp_${System.currentTimeMillis()}.mp4")
    val videoEffects = mutableListOf<Effect>()

    when (preset) {
        ColorPreset.NORMAL -> Unit
        ColorPreset.GRAYSCALE -> videoEffects.add(RgbFilter.createGrayscaleFilter())
        ColorPreset.INVERT -> videoEffects.add(RgbFilter.createInvertedFilter())
        ColorPreset.HIGH_CONTRAST -> videoEffects.add(Contrast(0.45f))
    }

    val mediaItem = MediaItem.fromUri(sourceUri)
    val editedItem = EditedMediaItem.Builder(mediaItem)
        .setEffects(Effects(emptyList(), videoEffects))
        .build()

    val composition = Composition.Builder(listOf(editedItem)).build()

    val transformer = Transformer.Builder(context)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                try {
                    val savedName = saveTempToGallery(context, tempOutFile)
                    if (tempOutFile.exists()) tempOutFile.delete()
                    onDone(savedName)
                } catch (e: Exception) {
                    onError(e.message ?: "Gagal menyimpan ke galeri")
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                if (tempOutFile.exists()) tempOutFile.delete()
                onError(exportException.message ?: "Kesalahan pemrosesan video")
            }
        })
        .build()

    val startMs = (trimRange.start * 1000).toLong().coerceAtLeast(0L)
    val endMs = (trimRange.endInclusive * 1000).toLong().coerceAtLeast(startMs + 500L)
    val clippedItem = mediaItem.buildUpon()
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
        )
        .build()

    val finalEdited = EditedMediaItem.Builder(clippedItem)
        .setEffects(Effects(emptyList(), videoEffects))
        .build()

    val finalComposition = Composition.Builder(listOf(finalEdited)).build()
    if (speed != 1.0f) {
        transformer.start(composition, tempOutFile.absolutePath)
    } else {
        transformer.start(finalComposition, tempOutFile.absolutePath)
    }
}

private fun saveTempToGallery(context: android.content.Context, tempFile: File): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "OfflineCut_$timestamp.mp4"

    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OfflineCut")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IllegalStateException("Tidak dapat membuat entri MediaStore")

    resolver.openOutputStream(itemUri)?.use { out ->
        FileInputStream(tempFile).use { input ->
            input.copyTo(out)
        }
    } ?: throw IllegalStateException("Tidak dapat membuka output stream")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
    }

    return fileName
}
