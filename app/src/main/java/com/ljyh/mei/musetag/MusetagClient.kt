package com.ljyh.mei.musetag

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import com.ljyh.mei.AppContext
import com.ljyh.mei.constants.MusetagServerKey
import com.ljyh.mei.constants.MusetagSessionKey
import com.ljyh.mei.constants.MusetagUserIdKey
import com.ljyh.mei.constants.MusetagUsernameKey
import com.ljyh.mei.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Musetag Harmony 客户端：Cookie 会话 + 动态服务器地址 */
object MusetagClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Volatile private var cachedBase: String? = null
    @Volatile private var cachedSession: String? = null
    @Volatile private var cachedUsername: String? = null
    @Volatile private var prefsLoaded = false

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 在 Application 后台预加载，避免主线程 runBlocking */
    fun preloadFromDisk() {
        if (prefsLoaded) return
        // 先读同步快照（冷启动立刻知道是否登录）
        runCatching {
            val f = java.io.File(AppContext.instance.filesDir, "musetag_auth.txt")
            if (f.exists()) {
                val lines = f.readText().lines()
                if (lines.isNotEmpty()) cachedBase = normalizeServer(lines[0])
                if (lines.size > 1) cachedSession = lines[1]
                if (lines.size > 2) cachedUsername = lines[2]
            }
        }
        Thread {
            try {
                val prefs = runBlocking(Dispatchers.IO) {
                    val data = AppContext.instance.dataStore.data.first()
                    Triple(
                        normalizeServer(data[MusetagServerKey] ?: ""),
                        data[MusetagSessionKey] ?: "",
                        data[MusetagUsernameKey] ?: "",
                    )
                }
                if (prefs.first.isNotBlank()) cachedBase = prefs.first
                if (prefs.second.isNotBlank()) cachedSession = prefs.second
                if (prefs.third.isNotBlank()) cachedUsername = prefs.third
                prefsLoaded = true
                writeAuthSnapshot()
            } catch (_: Exception) {
                prefsLoaded = true
            }
        }.start()
    }

    private fun writeAuthSnapshot() {
        runCatching {
            java.io.File(AppContext.instance.filesDir, "musetag_auth.txt").writeText(
                listOf(cachedBase ?: "", cachedSession ?: "", cachedUsername ?: "").joinToString("\n")
            )
        }
    }

    private fun ensurePrefsLoaded() {
        if (prefsLoaded) return
        // 仅在非主线程同步读取
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            preloadFromDisk()
            return
        }
        try {
            val data = runBlocking(Dispatchers.IO) { AppContext.instance.dataStore.data.first() }
            cachedBase = normalizeServer(data[MusetagServerKey] ?: "")
            cachedSession = data[MusetagSessionKey] ?: ""
            cachedUsername = data[MusetagUsernameKey] ?: ""
            prefsLoaded = true
        } catch (_: Exception) {
            prefsLoaded = true
        }
    }

    fun normalizeServer(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        while (s.endsWith("/")) s = s.dropLast(1)
        val hostPort = s.removePrefix("http://").removePrefix("https://")
        if (!hostPort.contains(":") && !hostPort.contains("/")) {
            val scheme = if (s.startsWith("https://")) "https://" else "http://"
            s = "${scheme}${hostPort}:9876"
        }
        return s
    }

    fun currentBase(): String {
        ensurePrefsLoaded()
        cachedBase?.let { return it }
        return ""
    }

    fun currentSession(): String {
        ensurePrefsLoaded()
        return cachedSession ?: ""
    }

    fun currentUsername(): String {
        ensurePrefsLoaded()
        return cachedUsername ?: ""
    }

    fun isLoggedIn(): Boolean = currentSession().isNotBlank() && currentBase().isNotBlank()

    fun absoluteUrl(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl
        val base = currentBase().removeSuffix("/")
        if (base.isBlank()) return pathOrUrl
        val path = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
        return base + path
    }

    fun audioUrl(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        return absoluteUrl("/api/audio?path=" + URLEncoder.encode(filePath, "UTF-8"))
    }

    fun invalidate() {
        cachedBase = null
        cachedSession = null
        cachedUsername = null
        prefsLoaded = false
    }

    suspend fun saveConfig(server: String, session: String, username: String? = null, userId: String? = null) {
        val normalized = normalizeServer(server)
        AppContext.instance.dataStore.edit { prefs ->
            prefs[MusetagServerKey] = normalized
            prefs[MusetagSessionKey] = session
            if (username != null) prefs[MusetagUsernameKey] = username
            if (userId != null) prefs[MusetagUserIdKey] = userId
        }
        cachedBase = normalized
        cachedSession = session
        if (username != null) cachedUsername = username
        prefsLoaded = true
        writeAuthSnapshot()
    }

    suspend fun clearSession() {
        AppContext.instance.dataStore.edit { prefs ->
            prefs.remove(MusetagSessionKey)
        }
        cachedSession = ""
        writeAuthSnapshot()
        MusetagStore.clear()
    }

    suspend fun login(server: String, username: String, password: String): Result<MusetagUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = normalizeServer(server)
                if (base.isBlank()) error("请填写服务器地址")
                val body = JSONObject().put("username", username).put("password", password)
                    .toString().toRequestBody(jsonMedia)
                val request = Request.Builder()
                    .url("$base/api/auth/login")
                    .post(body)
                    .header("Content-Type", "application/json")
                    .build()
                okHttp.newCall(request).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val err = runCatching { JSONObject(respBody).optString("error") }.getOrNull().orEmpty()
                        error(err.ifBlank { "登录失败 HTTP ${resp.code}" })
                    }
                    val session = resp.headers("Set-Cookie").firstNotNullOfOrNull { raw ->
                        raw.split(';').map { it.trim() }
                            .firstOrNull { it.startsWith("mt_session=") }
                            ?.removePrefix("mt_session=")
                    } ?: error("服务器未返回会话 Cookie")
                    val user = parseUser(respBody)
                    saveConfig(base, session, user.username ?: username, user.id)
                    MusetagStore.clear()
                    user
                }
            }
        }

    suspend fun fetchMe(): Result<MusetagUser> = withContext(Dispatchers.IO) {
        runCatching {
            val json = get("/api/user/me")
            parseUser(json).also { MusetagStore.user = it }
        }
    }

    suspend fun fetchLibrary(): Result<MusetagLibrary> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(get("/api/library"))
            MusetagLibrary(
                songs = parseSongs(root.optJSONArray("songs")),
                albums = parseAlbums(root.optJSONArray("albums")),
                artists = parseArtists(root.optJSONArray("artists")),
                version = root.optLong("version"),
            ).also { MusetagStore.update(it) }
        }
    }

    suspend fun fetchLyrics(songId: String): Result<MusetagLyrics> = withContext(Dispatchers.IO) {
        runCatching {
            val json = get("/api/song/lyrics?id=" + URLEncoder.encode(songId, "UTF-8"))
            val root = JSONObject(json)
            MusetagLyrics(
                lrcContent = root.optString("lrcContent").takeIf { it.isNotBlank() && it != "null" },
                ttmlContent = root.optString("ttmlContent").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }

    fun get(path: String): String {
        val request = Request.Builder().url(absoluteUrl(path)).get()
        val session = currentSession()
        if (session.isNotBlank()) request.header("Cookie", "mt_session=$session")
        okHttp.newCall(request.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) error("未登录或会话过期")
            if (!resp.isSuccessful) error("请求失败 HTTP ${resp.code}")
            return body
        }
    }

    private fun parseUser(json: String): MusetagUser {
        val o = JSONObject(json)
        return MusetagUser(
            id = o.optString("id"),
            username = o.optString("username"),
            role = o.optString("role"),
            avatarUrl = o.optString("avatarUrl"),
            likedSongs = parseLiked(o.optJSONArray("likedSongs") ?: o.optJSONArray("likedSongIds")),
            likedAlbums = parseLiked(o.optJSONArray("likedAlbums") ?: o.optJSONArray("likedAlbumIds")),
            likedArtists = parseLiked(o.optJSONArray("likedArtists") ?: o.optJSONArray("likedArtistIds")),
            playlists = parsePlaylists(o.optJSONArray("playlists")),
        )
    }

    private fun parseLiked(arr: JSONArray?): List<MusetagLiked> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val el = arr.opt(i)
            when (el) {
                is JSONObject -> el.optString("id").takeIf { it.isNotBlank() }
                    ?.let { MusetagLiked(it, if (el.has("date")) el.optLong("date") else null) }
                is String -> if (el.isNotBlank()) MusetagLiked(el, null) else null
                else -> null
            }
        }
    }

    private fun parsePlaylists(arr: JSONArray?): List<MusetagPlaylist> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MusetagPlaylist(
                id = o.optString("id"),
                title = o.optString("title"),
                content = null,
                coverUrl = o.optString("coverUrl").ifBlank { null },
            )
        }
    }

    private fun parseSongs(arr: JSONArray?): List<MusetagSong> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MusetagSong(
                id = o.optString("id"),
                title = o.optString("title"),
                artistId = o.optString("artistId"),
                artistIds = o.optJSONArray("artistIds")?.let { a -> (0 until a.length()).map { a.optString(it) } },
                albumId = o.optString("albumId"),
                trackNumber = if (o.has("trackNumber")) o.optInt("trackNumber") else null,
                discNumber = if (o.has("discNumber")) o.optInt("discNumber") else null,
                duration = if (o.has("duration")) o.optDouble("duration") else null,
                filePath = o.optString("filePath"),
                playCount = if (o.has("playCount")) o.optInt("playCount") else null,
                dateAdded = if (o.has("dateAdded")) o.optLong("dateAdded") else null,
                audioUrl = o.optString("audioUrl"),
                tagIds = o.optJSONArray("tagIds")?.let { a -> (0 until a.length()).map { a.optString(it) } },
            )
        }
    }

    private fun parseAlbums(arr: JSONArray?): List<MusetagAlbum> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MusetagAlbum(
                id = o.optString("id"),
                title = o.optString("title"),
                artistId = o.optString("artistId"),
                year = if (o.has("year")) o.optInt("year") else null,
                coverUrl = o.optString("coverUrl"),
            )
        }
    }

    private fun parseArtists(arr: JSONArray?): List<MusetagArtist> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MusetagArtist(
                id = o.optString("id"),
                name = o.optString("name"),
                avatarUrl = o.optString("avatarUrl"),
                description = o.optString("description"),
            )
        }
    }
}
