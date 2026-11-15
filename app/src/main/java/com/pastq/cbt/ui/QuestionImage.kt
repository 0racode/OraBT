package com.pastq.cbt.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest

/** Question pictures are saved on the phone after the first time they load, so they work offline later. */
object QuestionImages {
    fun urls(image: String): List<String> =
        image.split("\n").map { it.trim() }.filter { it.startsWith("https://") }

    /** Starts saving the pictures of a whole practice or exam session so they are there offline. */
    fun prefetch(context: Context, images: List<String>) {
        val loader = context.imageLoader
        images.flatMap { urls(it) }.distinct().forEach { u ->
            loader.enqueue(ImageRequest.Builder(context).data(u).build())
        }
    }
}

@Composable
fun QuestionImage(image: String) {
    val urls = QuestionImages.urls(image)
    if (urls.isEmpty()) return
    var zoomed by remember { mutableStateOf<String?>(null) }
    urls.forEach { u ->
        Card(
            Modifier.fillMaxWidth().clickable { zoomed = u },
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            SubcomposeAsyncImage(
                model = u,
                contentDescription = "Picture for this question",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 320.dp).padding(4.dp),
                loading = { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                error = {
                    Text(
                        "The picture could not load. Connect to the internet once and it will be saved on your phone.",
                        modifier = Modifier.padding(12.dp), color = Color.DarkGray, style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }
        Text("Tap the picture to enlarge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    zoomed?.let { u ->
        Dialog(onDismissRequest = { zoomed = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.fillMaxSize().background(Color.White)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .clickable { zoomed = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = u, contentDescription = "Picture for this question", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
                Text(
                    "Pinch to zoom · tap to close", color = Color.DarkGray, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }
}
