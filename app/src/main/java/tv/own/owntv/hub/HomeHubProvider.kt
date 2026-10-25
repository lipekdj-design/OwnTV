package tv.own.owntv.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.core.launcher.LauncherRecommendationPlanner
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Read-only summary for a home-screen app: what to continue watching and the favourite channels of
 * the active profile. Each row carries the owntv:// link that opens it, so the home screen never
 * needs the playlist, the player or the database itself.
 *
 *   content://tv.own.owntv.homehub/continue?limit=20
 *   content://tv.own.owntv.homehub/favorites?limit=30
 *
 * Columns: title, subtitle, image (URL or empty), link (owntv:// URI), progress (0-100, or -1).
 * Guarded by the READ_HOME_HUB permission; a profile with a PIN returns nothing, so the lock still holds.
 */
class HomeHubProvider : ContentProvider(), KoinComponent {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()?.coerceIn(1, 60) ?: 20
        runCatching {
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(TIMEOUT_MS) { fill(cursor, uri.pathSegments.firstOrNull(), limit) }
            }
        }.onFailure { Log.w(TAG, "query ${uri.path} failed", it) }
        return cursor
    }

    private suspend fun fill(cursor: MatrixCursor, what: String?, limit: Int) {
        val settings: SettingsRepository = get()
        val profileId = settings.activeProfileIdNow()
        if (profileId < 0) return
        val profile = get<tv.own.owntv.core.database.dao.ProfileDao>().getById(profileId)
        if (profile == null || profile.pinHash != null) return
        when (what) {
            "continue" -> get<LauncherRecommendationPlanner>().buildContinuationItems(profileId)
                .sortedByDescending { it.lastEngagementAt }
                .take(limit)
                .forEach { item ->
                    val progress = if (item.durationMs > 0) (item.positionMs * 100 / item.durationMs).toInt().coerceIn(0, 100) else -1
                    cursor.addRow(arrayOf<Any?>(item.title, item.subtitle.orEmpty(), item.posterUrl.orEmpty(), item.playbackUri.toString(), progress))
                }
            "favorites" -> get<ChannelDao>().favoritesListAlpha(profileId).first()
                .take(limit)
                .forEach { ch ->
                    val link = LauncherDeepLink.Live(sourceId = ch.sourceId, remoteId = ch.remoteId, name = ch.name, itemId = ch.id).toUri()
                    cursor.addRow(arrayOf<Any?>(ch.name, ch.number?.toString().orEmpty(), ch.logoUrl.orEmpty(), link.toString(), -1))
                }
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val TAG = "HomeHub"
        const val TIMEOUT_MS = 4_000L
        val COLUMNS = arrayOf("title", "subtitle", "image", "link", "progress")
    }
}
