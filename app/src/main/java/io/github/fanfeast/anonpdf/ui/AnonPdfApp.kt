package io.github.fanfeast.anonpdf.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.net.toUri
import io.github.fanfeast.anonpdf.ui.about.AboutScreen
import io.github.fanfeast.anonpdf.ui.home.HomeScreen
import io.github.fanfeast.anonpdf.ui.organize.OrganizeScreen
import io.github.fanfeast.anonpdf.ui.settings.SettingsScreen
import io.github.fanfeast.anonpdf.ui.sign.SignScreen
import io.github.fanfeast.anonpdf.ui.tools.ToolCatalog
import io.github.fanfeast.anonpdf.ui.tools.ToolId
import io.github.fanfeast.anonpdf.ui.tools.ToolScreen
import io.github.fanfeast.anonpdf.ui.viewer.ViewerScreen

private object Routes {
    const val HOME = "home"
    const val ABOUT = "about"
    const val SETTINGS = "settings"

    const val VIEWER = "viewer/{uri}"
    fun viewer(uri: Uri) = "viewer/${Uri.encode(uri.toString())}"

    const val TOOL = "tool/{toolId}?uri={uri}"
    fun tool(id: ToolId, uri: Uri?) = buildString {
        append("tool/${id.name}")
        if (uri != null) append("?uri=${Uri.encode(uri.toString())}")
    }

    const val ORGANIZE = "organize?uri={uri}"
    fun organize(uri: Uri?) = if (uri == null) {
        "organize"
    } else {
        "organize?uri=${Uri.encode(uri.toString())}"
    }

    const val SIGN = "sign?uri={uri}"
    fun sign(uri: Uri?) = if (uri == null) "sign" else "sign?uri=${Uri.encode(uri.toString())}"
}

/** Optional string argument shared by the routes that can be handed a document. */
private fun optionalUriArg() = listOf(
    navArgument("uri") {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    },
)

@Composable
fun AnonPdfApp(initialUri: Uri?) {
    val navController = rememberNavController()

    // A PDF opened from another app lands us straight in the viewer.
    LaunchedEffect(initialUri) {
        if (initialUri != null) navController.navigate(Routes.viewer(initialUri))
    }

    /** Tools with a bespoke screen get their own route; the rest share one. */
    fun openTool(id: ToolId, uri: Uri?) {
        val route = when (id) {
            ToolId.ORGANIZE -> Routes.organize(uri)
            ToolId.SIGN -> Routes.sign(uri)
            else -> Routes.tool(id, uri)
        }
        navController.navigate(route)
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDocument = { uri -> navController.navigate(Routes.viewer(uri)) },
                onOpenTool = { id -> openTool(id, null) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.VIEWER,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { entry ->
            val raw = entry.arguments?.getString("uri")
            if (raw == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ViewerScreen(
                    uri = raw.toUri(),
                    onBack = { navController.popBackStack() },
                    onOpenTool = { id, uri -> openTool(id, uri) },
                )
            }
        }

        composable(route = Routes.TOOL, arguments = optionalUriArg() + listOf(
            navArgument("toolId") { type = NavType.StringType },
        )) { entry ->
            val toolName = entry.arguments?.getString("toolId")
            val spec = ToolCatalog.all.firstOrNull { it.id.name == toolName }
            if (spec == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ToolScreen(
                    toolId = spec.id,
                    initialUri = entry.arguments?.getString("uri")?.toUri(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(route = Routes.ORGANIZE, arguments = optionalUriArg()) { entry ->
            OrganizeScreen(
                initialUri = entry.arguments?.getString("uri")?.toUri(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Routes.SIGN, arguments = optionalUriArg()) { entry ->
            SignScreen(
                initialUri = entry.arguments?.getString("uri")?.toUri(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
