package io.github.fanfeast.anonpdf.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class RecentDocument(
    val uri: Uri,
    val name: String,
    val openedAt: Long,
)

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "anonpdf_settings",
)

/**
 * The only thing AnonPDF persists: a short list of recently opened documents and
 * a handful of display toggles.
 *
 * The recents list holds SAF URIs and file names, stays in app-private storage,
 * is excluded from cloud backup, and can be switched off entirely — see
 * [setRememberRecents]. There is no identifier of any kind in here.
 */
class AppPreferences(private val context: Context) {

    val rememberRecents: Flow<Boolean> =
        context.preferencesStore.data.map { it[KEY_REMEMBER_RECENTS] ?: true }

    val invertPdfColors: Flow<Boolean> =
        context.preferencesStore.data.map { it[KEY_INVERT_COLORS] ?: false }

    val keepScreenOn: Flow<Boolean> =
        context.preferencesStore.data.map { it[KEY_KEEP_SCREEN_ON] ?: false }

    val recents: Flow<List<RecentDocument>> = context.preferencesStore.data.map { preferences ->
        if (preferences[KEY_REMEMBER_RECENTS] == false) return@map emptyList()
        decode(preferences[KEY_RECENTS])
    }

    suspend fun setRememberRecents(enabled: Boolean) {
        context.preferencesStore.edit { preferences ->
            preferences[KEY_REMEMBER_RECENTS] = enabled
            // Turning the feature off should also destroy what it already collected.
            if (!enabled) preferences.remove(KEY_RECENTS)
        }
    }

    suspend fun setInvertPdfColors(enabled: Boolean) {
        context.preferencesStore.edit { it[KEY_INVERT_COLORS] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.preferencesStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun addRecent(uri: Uri, name: String) {
        context.preferencesStore.edit { preferences ->
            if (preferences[KEY_REMEMBER_RECENTS] == false) return@edit
            val existing = decode(preferences[KEY_RECENTS]).filterNot { it.uri == uri }
            val updated = (listOf(RecentDocument(uri, name, System.currentTimeMillis())) + existing)
                .take(MAX_RECENTS)
            preferences[KEY_RECENTS] = encode(updated)
        }
    }

    suspend fun removeRecent(uri: Uri) {
        context.preferencesStore.edit { preferences ->
            val updated = decode(preferences[KEY_RECENTS]).filterNot { it.uri == uri }
            preferences[KEY_RECENTS] = encode(updated)
        }
    }

    suspend fun clearRecents() {
        context.preferencesStore.edit { it.remove(KEY_RECENTS) }
    }

    /**
     * Keeps read access to a document across restarts.
     *
     * Only works for URIs that came from the document picker; a URI handed to us
     * by another app's share sheet is not persistable, hence the runCatching.
     */
    fun tryPersistAccess(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun encode(documents: List<RecentDocument>): String {
        val array = JSONArray()
        documents.forEach { document ->
            array.put(
                JSONObject().apply {
                    put("uri", document.uri.toString())
                    put("name", document.name)
                    put("at", document.openedAt)
                },
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<RecentDocument> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val uri = item.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RecentDocument(
                    uri = uri.toUri(),
                    name = item.optString("name", "document.pdf"),
                    openedAt = item.optLong("at", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY_RECENTS = stringPreferencesKey("recents")
        val KEY_REMEMBER_RECENTS = booleanPreferencesKey("remember_recents")
        val KEY_INVERT_COLORS = booleanPreferencesKey("invert_pdf_colors")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        const val MAX_RECENTS = 20
    }
}
