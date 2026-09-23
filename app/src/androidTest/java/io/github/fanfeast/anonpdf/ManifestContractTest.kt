package io.github.fanfeast.anonpdf

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ways into the app, asserted against the installed package.
 *
 * Every entry point here is something users or other apps depend on: the
 * launcher icon, "Open with" for PDFs, "Share" to AnonPDF, and the share-out
 * provider. A manifest edit that silently drops one of them fails here instead
 * of in a Play review or a user report.
 */
@RunWith(AndroidJUnit4::class)
class ManifestContractTest {

    private lateinit var context: Context
    private lateinit var pm: PackageManager
    private val main by lazy { ComponentName(context, MainActivity::class.java) }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        pm = context.packageManager
    }

    private fun resolvesToMain(intent: Intent): Boolean =
        pm.queryIntentActivities(intent.setPackage(context.packageName), 0)
            .any { it.activityInfo.name == main.className }

    @Test
    fun launcherIconOpensMainActivity() {
        val launch = pm.getLaunchIntentForPackage(context.packageName)
        assertNotNull("No launcher entry", launch)
        assertEquals(main, launch!!.component)
    }

    @Test
    fun openWithPdfIsHandled() {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://example/doc.pdf"), "application/pdf")
            .addCategory(Intent.CATEGORY_DEFAULT)
        assertTrue("VIEW application/pdf no longer reaches MainActivity", resolvesToMain(intent))
    }

    @Test
    fun openWithPdfFromBrowserIsHandled() {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://example/doc.pdf"), "application/pdf")
            .addCategory(Intent.CATEGORY_BROWSABLE)
        assertTrue("BROWSABLE VIEW of a PDF no longer reaches MainActivity", resolvesToMain(intent))
    }

    @Test
    fun shareToAnonPdfIsHandled() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .addCategory(Intent.CATEGORY_DEFAULT)
        assertTrue("SEND application/pdf no longer reaches MainActivity", resolvesToMain(intent))
    }

    @Test
    fun onlyMainActivityIsExported() {
        val info = pm.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_PROVIDERS or
                PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS,
        )
        val exported = listOfNotNull(
            info.activities?.filter { it.exported }?.map { it.name },
            info.providers?.filter { it.exported }?.map { it.name },
            info.services?.filter { it.exported }?.map { it.name },
            info.receivers?.filter { it.exported && it.permission == null }?.map { it.name },
        ).flatten()
            // Compose's preview host comes in with debugImplementation(ui-tooling)
            // and is never part of a release build.
            .filterNot { it.startsWith("androidx.compose.ui.tooling.") }
        assertEquals(listOf(main.className), exported)
    }

    @Test
    fun shareOutProviderIsPrivateAndGrantsPerUri() {
        val provider = pm.resolveContentProvider("${context.packageName}.files", 0)
        assertNotNull("FileProvider authority is missing", provider)
        assertFalse("FileProvider must not be exported", provider!!.exported)
        assertTrue("FileProvider must grant per-URI access", provider.grantUriPermissions)
    }

    @Test
    fun backupIsOff() {
        val flags = context.applicationInfo.flags
        assertFalse(
            "allowBackup must stay false: documents never leave the device",
            (flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0,
        )
    }
}
