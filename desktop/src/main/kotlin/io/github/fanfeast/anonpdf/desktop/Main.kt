package io.github.fanfeast.anonpdf.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.fanfeast.anonpdf.pdf.PdfRasterizer
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main(args: Array<String>) {
    PrivateScratch.install()
    val initial = args.firstOrNull()?.let(::File)?.takeIf { it.isFile }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AnonPDF",
            state = rememberWindowState(width = 900.dp, height = 1000.dp),
        ) {
            MaterialTheme { Viewer(initial, pick = { pickPdf(window) }) }
        }
    }
}

@Composable
private fun Viewer(initial: File?, pick: () -> File?) {
    var file by remember { mutableStateOf(initial) }
    var page by remember(file) { mutableStateOf(0) }
    var image by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    val rasterizer = remember(file) { file?.let { runCatching { PdfRasterizer.open(it) }.getOrNull() } }
    DisposableEffect(rasterizer) { onDispose { rasterizer?.close() } }

    LaunchedEffect(rasterizer, page) {
        if (file != null && rasterizer == null) error = "That file could not be opened as a PDF."
        image = rasterizer?.renderByWidth(page, 1400)?.toComposeImageBitmap()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFE8E8EE))) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { pick()?.let { file = it } }) { Text("Open PDF…") }
            if (rasterizer != null) {
                TextButton(onClick = { page-- }, enabled = page > 0) { Text("‹ Previous") }
                Text("${page + 1} / ${rasterizer.pageCount}")
                TextButton(onClick = { page++ }, enabled = page < rasterizer.pageCount - 1) { Text("Next ›") }
                Text(file?.name.orEmpty(), color = Color.DarkGray)
            }
        }
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            when {
                error != null -> Text(error.orEmpty())
                image != null -> Image(image!!, contentDescription = "Page ${page + 1}", contentScale = ContentScale.Fit)
                file == null -> Text("Open a PDF to start. Nothing leaves this computer.")
            }
        }
    }
}

private fun pickPdf(owner: Frame): File? {
    val dialog = FileDialog(owner, "Open a PDF", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
        isVisible = true
    }
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}
