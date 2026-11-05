package tv.own.owntv.hub

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.SyncScopeChoice
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * DROPZ TV remote setup: a TV that has nobody to set it up shows a 4-digit code and waits; the
 * playlist is then set up from afar (a row in the DROPZ Supabase project) and the TV imports it by
 * itself. Every app start asks again, so a later change reaches a TV that is already in use.
 *
 * The server holds one row per TV, keyed by a random id that never leaves this device; only the
 * three functions below are reachable with the public key, and each one needs that id.
 */
object RemoteConfig : KoinComponent {
    private const val TAG = "RemoteConfig"
    private const val API = "https://iwvzebbxvuskllmbvgsg.supabase.co/rest/v1/rpc/"
    private const val KEY = "sb_publishable_ZrhzG_pXI9mARfuta_TUKg_oK9Xpn6u"
    private const val PREFS = "dropz_tv"
    private const val PREF_DEVICE = "remote_device"
    private const val PREF_CODE = "remote_code"
    private const val PREF_APPLIED = "remote_applied"
    private const val PREF_PENDING_PROFILE = "remote_pending_profile"
    private const val POLL_MS = 8_000L
    private const val TYPE_M3U = "m3u"

    sealed interface State {
        data object Connecting : State
        data object Offline : State
        data class Waiting(val code: String) : State
        data class Applying(val code: String, val name: String) : State
        data class Failed(val code: String, val reason: String) : State
        data class Done(val profileId: Long) : State
    }

    private val _state = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = _state.asStateFlow()

    private val lock = Mutex()
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }

    private class Poll(val code: String, val version: Int, val config: JSONObject?)

    private class Outcome(val profileId: Long, val ok: Boolean, val status: String)

    /** The code of this TV once it has one (shown on the waiting screen). */
    fun savedCode(context: Context): String? = prefs(context).getString(PREF_CODE, null)

    /**
     * First run with nothing set up: keep asking until a configuration arrives, import it and report
     * [State.Done] with the profile it went into. Runs while the waiting screen is on screen.
     */
    suspend fun waitForSetup(context: Context) {
        while (true) {
            val poll = runCatching { fetch(context) }.onFailure { Log.w(TAG, "poll failed", it) }.getOrNull()
            if (poll == null) {
                _state.value = savedCode(context)?.let { State.Waiting(it) } ?: State.Offline
            } else if (poll.config != null && poll.version > applied(context)) {
                val outcome = apply(context, poll)
                if (outcome.ok && outcome.profileId >= 0) {
                    _state.value = State.Done(outcome.profileId)
                    return
                }
                _state.value = State.Failed(poll.code, outcome.status)
            } else {
                _state.value = State.Waiting(poll.code)
            }
            delay(POLL_MS)
        }
    }

    /** App start on a TV already in use: apply a newer configuration if one is waiting. */
    suspend fun checkOnStart(context: Context) {
        runCatching {
            val poll = fetch(context) ?: return
            if (poll.config == null || poll.version <= applied(context)) return
            if (get<SettingsRepository>().activeProfileIdNow() < 0) return // the waiting screen handles it
            apply(context, poll)
        }.onFailure { Log.w(TAG, "start check failed", it) }
    }

    // ---- server ----

    private suspend fun fetch(context: Context): Poll? = withContext(Dispatchers.IO) {
        val device = deviceId(context)
        val version = BuildConfig.VERSION_NAME
        var json = call("dropztv_poll", JSONObject().put("p_device", device).put("p_version", version))
        if (json == null || json == "null") {
            val model = (Build.MANUFACTURER + " " + Build.MODEL).trim()
            call("dropztv_register", JSONObject().put("p_device", device).put("p_model", model).put("p_version", version))
            json = call("dropztv_poll", JSONObject().put("p_device", device).put("p_version", version))
        }
        if (json == null || json == "null") return@withContext null
        val o = JSONObject(json)
        val code = o.optString("code")
        if (code.isNotEmpty()) prefs(context).edit().putString(PREF_CODE, code).apply()
        Poll(code, o.optInt("config_version"), o.optJSONObject("config"))
    }

    private suspend fun ack(context: Context, version: Int, status: String) = withContext(Dispatchers.IO) {
        runCatching {
            call(
                "dropztv_ack",
                JSONObject().put("p_device", deviceId(context)).put("p_config_version", version).put("p_status", status),
            )
        }.onFailure { Log.w(TAG, "ack failed", it) }
        Unit
    }

    private fun call(function: String, body: JSONObject): String? {
        val request = Request.Builder()
            .url(API + function)
            .header("apikey", KEY)
            .header("Authorization", "Bearer $KEY")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return text.trim()
        }
    }

    // ---- applying ----

    private suspend fun apply(context: Context, poll: Poll): Outcome = lock.withLock {
        val outcome = runCatching { importAll(context, poll) }
            .getOrElse { Outcome(-1L, false, it.message ?: it.javaClass.simpleName) }
        if (outcome.ok) prefs(context).edit().putInt(PREF_APPLIED, poll.version).apply()
        ack(context, poll.version, (if (outcome.ok) "ok: " else "erro: ") + outcome.status)
        outcome
    }

    private suspend fun importAll(context: Context, poll: Poll): Outcome {
        val config = poll.config ?: return Outcome(-1L, false, "sem config")
        val playlists = config.optJSONArray("playlists") ?: return Outcome(-1L, false, "sem listas")
        val settings = get<SettingsRepository>()
        val sources = get<SourceDao>()
        val importer: SourceImporter = get()

        var profileId = settings.activeProfileIdNow()
        val newProfile = profileId < 0
        if (newProfile) {
            // A failed first try leaves its profile behind; reuse it instead of making another.
            val pending = prefs(context).getLong(PREF_PENDING_PROFILE, -1L)
            val known = pending > 0 && get<ProfileDao>().getById(pending) != null
            profileId = if (known) {
                pending
            } else {
                val name = config.optString("profile").ifBlank { context.getString(R.string.dropz_remote_profile) }
                importer.createProfile(name, 0, false, null).also {
                    prefs(context).edit().putLong(PREF_PENDING_PROFILE, it).apply()
                }
            }
        }
        importer.useProfile(profileId)
        // The profile name can be changed from afar too (the remote sets it when the TV is in use).
        val wantedName = config.optString("profile").trim()
        if (!newProfile && wantedName.isNotEmpty()) {
            val dao = get<ProfileDao>()
            dao.getById(profileId)?.takeIf { it.name != wantedName }?.let { dao.update(it.copy(name = wantedName)) }
        }

        val existing = sources.getAllOnce()
        var added = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        for (i in 0 until playlists.length()) {
            val p = playlists.optJSONObject(i) ?: continue
            val name = p.optString("name").ifBlank { context.getString(R.string.setup_default_iptv) }
            val isM3u = p.optString("type") == TYPE_M3U
            val already = if (isM3u) {
                existing.any { it.url == p.optString("url") }
            } else {
                existing.any { it.username == p.optString("username") && sameHost(it.url, p.optString("server")) }
            }
            if (already) { skipped++; continue }
            _state.value = State.Applying(poll.code, name)
            importer.reset()
            if (isM3u) {
                importer.m3u(name, p.optString("url"))
            } else {
                importer.xtream(
                    name = name,
                    server = p.optString("server"),
                    username = p.optString("username"),
                    password = p.optString("password"),
                    live = SyncScopeChoice.Now,
                    movies = SyncScopeChoice.Later,
                    series = SyncScopeChoice.Later,
                )
            }
            when (val s = importer.state.value) {
                is SourceImporter.ImportState.Success -> added++
                is SourceImporter.ImportState.Failed -> errors += name + ": " + s.failure.javaClass.simpleName
                else -> errors += name
            }
        }
        importer.reset()
        val ok = errors.isEmpty() && (added + skipped) > 0
        if (newProfile && ok) {
            importer.finish()
            prefs(context).edit().remove(PREF_PENDING_PROFILE).apply()
        }
        val status = "adicionadas $added, ja existiam $skipped" + if (errors.isEmpty()) "" else ", falhas " + errors.joinToString()
        Log.i(TAG, status)
        return Outcome(profileId, ok, status)
    }

    private fun sameHost(a: String, b: String): Boolean {
        fun host(u: String) = runCatching { android.net.Uri.parse(if ("://" in u) u else "http://$u").host?.lowercase() }.getOrNull()
        val ha = host(a)
        return ha != null && ha == host(b)
    }

    // ---- local state ----

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun applied(context: Context): Int = prefs(context).getInt(PREF_APPLIED, 0)

    private fun deviceId(context: Context): String {
        val p = prefs(context)
        p.getString(PREF_DEVICE, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        p.edit().putString(PREF_DEVICE, id).apply()
        return id
    }
}
