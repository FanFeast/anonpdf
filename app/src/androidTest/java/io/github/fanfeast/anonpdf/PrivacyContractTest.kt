package io.github.fanfeast.anonpdf

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The privacy promise, asserted against the installed package rather than the
 * source. If a dependency ever merges a permission into the manifest, this fails.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyContractTest {

    /**
     * AndroidX declares a signature-level permission so an app can register a
     * not-exported broadcast receiver against itself. It grants no access to
     * anything and cannot be held by another app, so it is not a user-facing
     * permission — but it is legitimately present, so allow exactly it.
     */
    private val allowed = setOf("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")

    @Test
    fun theAppRequestsNoPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requested = (info.requestedPermissions ?: emptyArray())
            .filterNot { permission -> allowed.any { permission.endsWith(it) } }

        assertEquals(
            "AnonPDF must request no permissions, found: $requested",
            emptyList<String>(),
            requested,
        )
    }

    @Test
    fun networkAccessIsNotDeclared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val declared = (info.requestedPermissions ?: emptyArray()).toList()

        assertTrue(
            "INTERNET must never be declared",
            declared.none { it == android.Manifest.permission.INTERNET },
        )
        assertTrue(
            "ACCESS_NETWORK_STATE must never be declared",
            declared.none { it == android.Manifest.permission.ACCESS_NETWORK_STATE },
        )
    }

    @Test
    fun theOsRefusesOutboundConnections() {
        // Without INTERNET, the kernel denies the socket. This proves the app
        // could not phone home even if some code tried to.
        val failure = runCatching {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("example.com", 80), 3000)
            }
        }.exceptionOrNull()

        assertTrue(
            "a socket connection must fail, instead it: ${failure ?: "succeeded"}",
            failure != null,
        )
    }

    @Test
    fun backupIsDisabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val flags = context.applicationInfo.flags
        assertEquals(
            "allowBackup must be off so documents cannot leave via backup",
            0,
            flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP,
        )
    }
}
