package tv.own.owntv.hub

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import tv.own.owntv.R
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import java.io.File

/**
 * DROPZ TV cameras: network cameras (RTSP) as channels of a playlist of their own, so they play in the
 * full player (H.265 included), and a link that opens one straight away.
 *
 *   owntv://camera/setup?name=Corredor&url=rtsp://…   adds or updates a camera, then opens it
 *   owntv://camera?name=Corredor                       opens that camera
 *   owntv://camera                                     opens the first camera
 *
 * The cameras live in an M3U file in the app's private storage; the playlist reads it as a local file.
 */
object CameraLinks : KoinComponent {
    private const val TAG = "CameraLinks"
    private const val FILE_NAME = "cameras.m3u"
    private const val HOST = "camera"
    private const val PATH_SETUP = "setup"
    private const val PARAM_NAME = "name"
    private const val PARAM_URL = "url"
    private const val SCHEME = "owntv"

    fun isCameraLink(uri: Uri?): Boolean = uri != null && uri.scheme == SCHEME && uri.host == HOST

    /** Whether any camera has been added (the sidebar shows its Cameras item only then). */
    fun hasCameras(context: Context): Boolean = File(context.filesDir, FILE_NAME).let { it.exists() && it.length() > 0 }

    /** Opens the first camera through the activity's own deep-link path. */
    fun openFirst(context: Context) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.Builder().scheme(SCHEME).authority(HOST).build())
            .setPackage(context.packageName)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(intent) }.onFailure { Log.w(TAG, "open camera failed", it) }
    }

    /** Adds the camera when asked, then returns the deep link that plays it (null when there is none). */
    suspend fun handle(context: Context, uri: Uri): LauncherDeepLink.Live? = withContext(Dispatchers.IO) {
        runCatching {
            val sourceName = context.getString(R.string.dropz_cameras_source)
            val file = File(context.filesDir, FILE_NAME)
            val entries = readEntries(file)
            var wanted = uri.getQueryParameter(PARAM_NAME)?.trim().orEmpty()
            if (uri.pathSegments.firstOrNull() == PATH_SETUP) {
                val url = uri.getQueryParameter(PARAM_URL)?.trim().orEmpty()
                if (wanted.isEmpty()) wanted = context.getString(R.string.dropz_camera_default_name)
                if (url.isNotEmpty()) {
                    entries[wanted] = url
                    writeEntries(file, entries, sourceName)
                    ensureSource(sourceName, file)
                }
            }
            val source = get<SourceDao>().observeAll().first().firstOrNull { it.name == sourceName } ?: return@runCatching null
            // A launcher link only opens channels of the active profile's playlists: make sure the
            // cameras are one of them (a no-op when already linked).
            val profileId = get<SettingsRepository>().activeProfileIdNow()
            if (profileId >= 0) {
                get<SourceDao>().link(tv.own.owntv.core.database.entity.ProfileSourceCrossRef(profileId = profileId, sourceId = source.id))
            }
            val name = wanted.takeIf { it.isNotEmpty() && entries.containsKey(it) } ?: entries.keys.firstOrNull() ?: return@runCatching null
            LauncherDeepLink.Live(sourceId = source.id, name = name)
        }.onFailure { Log.w(TAG, "camera link failed", it) }.getOrNull()
    }

    private suspend fun ensureSource(sourceName: String, file: File) {
        val existing = get<SourceDao>().observeAll().first().firstOrNull { it.name == sourceName }
        if (existing == null) {
            // Without a profile the importer would create one of its own; wait for setup to finish.
            val profileId = get<SettingsRepository>().activeProfileIdNow()
            if (profileId < 0) return
            val importer: SourceImporter = get()
            importer.useProfile(profileId)
            importer.m3u(sourceName, file.absolutePath)
        } else {
            get<CatalogSyncScheduler>().enqueueSync(
                existing.id,
                reason = SYNC_REASON,
                contentTypes = SyncContentTypes(live = true, movies = false, series = false),
            )
        }
        // Cameras send RTSP (often H.265): mpv plays it, ExoPlayer here has no RTSP module. Pin the
        // playlist to mpv so a tune never wastes seconds on the engine that cannot open it.
        get<SourceDao>().observeAll().first().firstOrNull { it.name == sourceName }?.let { source ->
            get<SourceDao>().updateLiveEnginePreference(source.id, tv.own.owntv.core.player.EnginePreference.MPV_ONLY.name)
        }
    }

    /** name → url, in file order. */
    private fun readEntries(file: File): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        if (!file.exists()) return out
        var pendingName: String? = null
        file.readLines().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith(EXTINF) -> pendingName = line.substringAfterLast(',').trim()
                line.isNotEmpty() && !line.startsWith(COMMENT) -> {
                    pendingName?.let { out[it] = line }
                    pendingName = null
                }
            }
        }
        return out
    }

    private fun writeEntries(file: File, entries: Map<String, String>, group: String) {
        val text = buildString {
            append(HEADER).append('\n')
            entries.forEach { (name, url) ->
                append(EXTINF).append(EXTINF_GROUP_OPEN).append(group).append(EXTINF_GROUP_CLOSE).append(name).append('\n')
                append(url).append('\n')
            }
        }
        file.writeText(text)
    }

    private const val HEADER = "#EXTM3U"
    private const val EXTINF = "#EXTINF:-1"
    private const val EXTINF_GROUP_OPEN = " group-title=\""
    private const val EXTINF_GROUP_CLOSE = "\","
    private const val COMMENT = "#"
    private const val SYNC_REASON = "camera_setup"
}
