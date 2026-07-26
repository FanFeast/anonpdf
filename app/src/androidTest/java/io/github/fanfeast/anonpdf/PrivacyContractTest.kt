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
 * source.
 *
 * AnonPDF reads files from storage, so it is not a zero-permission app. What it
 * still guarantees is that nothing it reads can leave the device: there is no
 * network permission, and the OS enforces that rather than the app promising it.
 * The allowlist below is the whole set of permissions this app is allowed to
 * hold — anything else appearing in the merged manifest fails the build.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyContractTest {

    /**
     * Every permission AnonPDF may legitimately declare, and why.
     *
     * Storage entries exist so the built-in browser can list PDFs instead of
     * routing every file through the system picker. AndroidX contributes a
     * signature-level permission so an app can register a not-exported receiver
     * against itself; it grants access to nothing and no other app can hold it.
     */
    private val allowed = setOf(
        // The one permission this app asks for, and only so the built-in browser
        // can list PDFs. Optional: declining it falls back to the system picker.
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    )

    /** Anything that could carry a document off the device. */
    private val forbidden = setOf(
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.ACCESS_NETWORK_STATE,
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.CHANGE_NETWORK_STATE,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.READ_PHONE_STATE,
    )

    private fun declaredPermissions(): List<String> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        return (info.requestedPermissions ?: emptyArray()).toList()
    }

    @Test
    fun onlyReviewedPermissionsAreDeclared() {
        val unexpected = declaredPermissions().filterNot { permission ->
            allowed.any { permission == it || permission.endsWith(it) }
        }
        assertEquals(
            "A permission appeared that nobody reviewed: $unexpected. If it is " +
                "intentional, add it to the allowlist and update PRIVACY.md.",
            emptyList<String>(),
            unexpected,
        )
    }

    @Test
    fun nothingThatCouldExfiltrateADocumentIsDeclared() {
        val declared = declaredPermissions()
        val offenders = forbidden.filter { it in declared }
        assertEquals(
            "These must never be declared: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun theOsRefusesOutboundConnections() {
        // Without INTERNET the kernel denies the socket. This is the guarantee the
        // whole app rests on: it holds even if some dependency tried to phone home.
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
    fun dnsResolutionAlsoFails() {
        // Belt and braces: no name lookups either, so nothing can be leaked as a
        // hostname to a DNS server.
        val failure = runCatching {
            java.net.InetAddress.getByName("example.com")
        }.exceptionOrNull()
        assertTrue(
            "DNS must not resolve, instead it: ${failure ?: "succeeded"}",
            failure != null,
        )
    }

    @Test
    fun backupIsDisabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            "allowBackup must be off so documents cannot leave via backup",
            0,
            context.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP,
        )
    }
}
