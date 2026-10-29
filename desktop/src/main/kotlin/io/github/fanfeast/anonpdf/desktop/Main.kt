package io.github.fanfeast.anonpdf.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.fanfeast.anonpdf.ui.common.choosePdfs
import io.github.fanfeast.anonpdf.ui.theme.AnonPdfTheme
import io.github.fanfeast.anonpdf.ui.tools.ToolCatalog
import java.awt.Dimension
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // First, before anything can create a temp file: see PrivateScratch.
    PrivateScratch.install()
    FontCache.install()
    if (args.firstOrNull() == "--self-test") exitProcess(runCatching { SelfTest.run() }.getOrElse { it.printStackTrace(); 2 })
    val initial = args.firstOrNull()?.let(::File)?.takeIf { it.isFile }
    application {
        val navigator = remember { DesktopNavigator(initial) }
        Window(
            onCloseRequest = ::exitApplication,
            title = "AnonPDF",
            state = rememberWindowState(width = 1280.dp, height = 880.dp),
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(960, 640) }
            MenuBar {
                Menu("File", mnemonic = 'F') {
                    Item(
                        "Open PDF to edit…",
                        shortcut = KeyShortcut(Key.O, ctrl = true),
                        onClick = { choosePdfs(multiple = false).firstOrNull()?.let(navigator::edit) },
                    )
                    Item(
                        "Close document",
                        enabled = navigator.hasEditorSession,
                        shortcut = KeyShortcut(Key.W, ctrl = true),
                        onClick = navigator::closeEditor,
                    )
                    Separator()
                    Item("Quit", shortcut = KeyShortcut(Key.Q, ctrl = true), onClick = ::exitApplication)
                }
                Menu("Tools", mnemonic = 'T') {
                    Item("Edit PDF", onClick = { navigator.show(Pane.Editor) })
                    ToolCatalog.grouped.forEach { (_, tools) ->
                        Separator()
                        tools.forEach { tool -> Item(tool.title, onClick = { navigator.show(Pane.Tool(tool.id)) }) }
                    }
                }
            }
            AnonPdfTheme { DesktopApp(navigator) }
        }
    }
}
