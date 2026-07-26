package io.github.fanfeast.anonpdf.storage

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri

/**
 * How much of the device's storage the user has let us see.
 *
 * Either the built-in browser can read the disk or it cannot; there is no useful
 * middle setting. Under scoped storage the media permissions only reach photos
 * and video, and a PDF in Downloads is neither, so all-files access is the only
 * thing that makes a file browser possible.
 *
 * It stays optional. With [NONE] the app still opens and saves through the
 * Storage Access Framework, so declining costs convenience, not function.
 */
enum class StorageAccessLevel {
    /** Nothing granted. The system file picker is the only route in. */
    NONE,

    /** All-files access: the browser can list PDFs anywhere on the device. */
    ALL_FILES,
}

object StorageAccess {

    fun level(context: Context): StorageAccessLevel =
        if (hasAllFiles()) StorageAccessLevel.ALL_FILES else StorageAccessLevel.NONE

    /**
     * All-files access is not a runtime permission; it is a per-app toggle in
     * Settings, so it has to be read back from the OS rather than requested.
     */
    fun hasAllFiles(): Boolean = Environment.isExternalStorageManager()

    /**
     * The Settings screens that can grant all-files access, best first.
     *
     * Returned as a list rather than resolved here on purpose: from API 30
     * onwards `resolveActivity` reports null for anything outside the manifest's
     * `<queries>` declaration, so probing would always claim the per-app screen is
     * missing and always fall through to the general list. The caller launches
     * these in order and moves on when one throws ActivityNotFoundException.
     */
    fun allFilesSettingsIntents(context: Context): List<Intent> = listOf(
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:${context.packageName}".toUri(),
        ),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
            "package:${context.packageName}".toUri(),
        ),
    )
}
