package com.ljyh.mei.musetag

import android.content.Context
import com.google.gson.Gson
import com.ljyh.mei.data.model.MediaMetadata
import java.io.File

/** 曲库内存缓存：O(1) 查表 + 磁盘快照 */
object MusetagStore {
    @Volatile
    var library: MusetagLibrary = MusetagLibrary()
        private set

    @Volatile
    var user: MusetagUser? = null

    private val gson = Gson()
    private val idToSong = HashMap<String, MusetagSong>()
    private val artistNameById = HashMap<String, String>()
    private val albumById = HashMap<String, MusetagAlbum>()
    private val longToSongId = HashMap<Long, String>()
    private val songIdToLong = HashMap<String, Long>()
    private val longToAudioUrl = HashMap<Long, String>()

    @Volatile
    var version: Int = 0
        private set

    fun songIdToLong(songId: String): Long {
        songIdToLong[songId]?.let { return it }
        val h = songId.hashCode().toLong() and 0x7FFF_FFFFL
        val unique = if (longToSongId.containsKey(h) && longToSongId[h] != songId) {
            var n = h
            while (longToSongId.containsKey(n) && longToSongId[n] != songId) n = (n + 1) and 0x7FFF_FFFFL
            n
        } else h
        songIdToLong[songId] = unique
        longToSongId[unique] = songId
        return unique
    }

    fun mediaIdToSongId(mediaId: String): String? {
        mediaId.toLongOrNull()?.let { return longToSongId[it] }
        return if (idToSong.containsKey(mediaId)) mediaId else null
    }

    fun audioUrlForMediaId(mediaId: String): String? {
        val songId = mediaIdToSongId(mediaId) ?: return null
        return longToAudioUrl[songIdToLong(songId)]
            ?: idToSong[songId]?.let { MusetagClient.audioUrl(it.filePath ?: it.audioUrl) }
    }

    fun absoluteCover(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return MusetagClient.absoluteUrl(url)
    }

    fun artistName(song: MusetagSong): String {
        val ids = song.artistIds?.takeIf { it.isNotEmpty() } ?: listOfNotNull(song.artistId)
        return ids.joinToString(" / ") { artistNameById[it] ?: "未知艺术家" }
            .ifBlank { "未知艺术家" }
    }

    fun albumTitle(song: MusetagSong): String =
        song.albumId?.let { albumById[it]?.title } ?: ""

    fun albumCover(song: MusetagSong): String =
        absoluteCover(song.albumId?.let { albumById[it]?.coverUrl }) ?: ""

    fun songsOfAlbum(albumId: String): List<MusetagSong> =
        library.songs.filter { it.albumId == albumId }
            .sortedWith(compareBy({ it.discNumber ?: 1 }, { it.trackNumber ?: 0 }))

    /** 全库歌曲：新加入的在前（dateAdded 降序） */
    fun librarySongsSorted(): List<MusetagSong> =
        library.songs.sortedByDescending { it.dateAdded ?: 0L }

    /** 全库专辑：新加入的在前（专辑内最新 dateAdded 降序） */
    fun libraryAlbumsSorted(): List<MusetagAlbum> {
        val albumLatest = HashMap<String, Long>()
        library.songs.forEach { s ->
            val a = s.albumId ?: return@forEach
            val d = s.dateAdded ?: 0L
            val prev = albumLatest[a]
            if (prev == null || d > prev) albumLatest[a] = d
        }
        return library.albums.sortedByDescending { albumLatest[it.id] ?: 0L }
    }

    /** 全库艺人：歌曲多的排前面 */
    fun libraryArtistsSorted(): List<MusetagArtist> {
        val counts = HashMap<String, Int>()
        library.artists.forEach { counts[it.id] = 0 }
        library.songs.forEach { s ->
            val ids = s.artistIds?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(s.artistId)
            ids.forEach { aid -> counts[aid] = (counts[aid] ?: 0) + 1 }
        }
        return library.artists.sortedByDescending { counts[it.id] ?: 0 }
    }

    fun playlists(): List<MusetagPlaylist> = user?.playlists ?: emptyList()

    fun findPlaylist(id: String): MusetagPlaylist? =
        user?.playlists?.find { it.id == id }

    fun playlistSongs(playlistId: String): List<MusetagSong> {
        val p = findPlaylist(playlistId) ?: return emptyList()
        val ids = p.content?.map { it.songId } ?: return emptyList()
        return ids.mapNotNull { idToSong[it] }
    }

    suspend fun createPlaylist(title: String): MusetagPlaylist? {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return null
        val p = MusetagPlaylist(
            id = "pl_${System.currentTimeMillis()}_${(0..999).random()}",
            title = trimmed,
            content = emptyList(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val newList = listOf(p) + playlists()
        return MusetagClient.syncPlaylists(newList).getOrNull()?.let { user = it; p }
    }

    suspend fun renamePlaylist(id: String, title: String): Boolean {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return false
        val newList = playlists().map {
            if (it.id == id) it.copy(title = trimmed, updatedAt = System.currentTimeMillis()) else it
        }
        return MusetagClient.syncPlaylists(newList).getOrNull()?.let { user = it; true } ?: false
    }

    /** 添加歌曲；已存在则忽略。返回是否成功 */
    suspend fun addSongToPlaylist(playlistId: String, songId: String): Boolean {
        val p = findPlaylist(playlistId) ?: return false
        if (p.content?.any { it.songId == songId } == true) return true
        val newContent = (p.content ?: emptyList()) +
            MusetagPlaylistItem(songId, System.currentTimeMillis())
        val newList = playlists().map {
            if (it.id == playlistId) it.copy(content = newContent, updatedAt = System.currentTimeMillis()) else it
        }
        return MusetagClient.syncPlaylists(newList).getOrNull()?.let { user = it; true } ?: false
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Boolean {
        val p = findPlaylist(playlistId) ?: return false
        val newContent = (p.content ?: emptyList()).filter { it.songId != songId }
        val newList = playlists().map {
            if (it.id == playlistId) it.copy(content = newContent, updatedAt = System.currentTimeMillis()) else it
        }
        return MusetagClient.syncPlaylists(newList).getOrNull()?.let { user = it; true } ?: false
    }

    suspend fun setPlaylistContent(playlistId: String, songIds: List<String>): Boolean {
        val newContent = songIds.map { MusetagPlaylistItem(it, System.currentTimeMillis()) }
        val newList = playlists().map {
            if (it.id == playlistId) it.copy(content = newContent, updatedAt = System.currentTimeMillis()) else it
        }
        return MusetagClient.syncPlaylists(newList).getOrNull()?.let { user = it; true } ?: false
    }

    fun isSongLiked(songId: String): Boolean =
        user?.likedSongs?.any { it.id == songId } == true

    fun isSongLikedByMediaId(mediaId: String): Boolean {
        val sid = mediaIdToSongId(mediaId) ?: return false
        return isSongLiked(sid)
    }

    fun isArtistLiked(artistId: String): Boolean =
        user?.likedArtists?.any { it.id == artistId } == true
            || user?.likedArtists?.any { resolveArtist(artistId)?.id == it.id } == true

    fun songIdOfMediaId(mediaId: String): String? = mediaIdToSongId(mediaId)

    /** 播放器收藏：mediaId → musetag songId */
    suspend fun toggleSongLikeByMediaId(mediaId: String): Boolean {
        val sid = mediaIdToSongId(mediaId) ?: return false
        return toggleSongLike(sid)
    }

    suspend fun toggleSongLike(songId: String): Boolean {
        val liked = isSongLiked(songId)
        val newList = if (liked) {
            (user?.likedSongs ?: emptyList()).filter { it.id != songId }
        } else {
            (user?.likedSongs ?: emptyList()) + MusetagLiked(songId, System.currentTimeMillis())
        }
        val result = MusetagClient.syncLikes(likedSongs = newList)
        result.onSuccess { user = it }
        return !liked
    }

    suspend fun toggleArtistLike(artistId: String): Boolean {
        val a = resolveArtist(artistId)
        val id = a?.id ?: artistId
        val liked = user?.likedArtists?.any { it.id == id } == true
        val newList = if (liked) {
            (user?.likedArtists ?: emptyList()).filter { it.id != id }
        } else {
            (user?.likedArtists ?: emptyList()) + MusetagLiked(id, System.currentTimeMillis())
        }
        val result = MusetagClient.syncLikes(likedArtists = newList)
        result.onSuccess { user = it }
        return !liked
    }

    fun toMediaMetadata(song: MusetagSong): MediaMetadata {
        val mediaId = songIdToLong(song.id)
        val albumTitle = albumTitle(song)
        val cover = albumCover(song)
        val ids = song.artistIds?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(song.artistId)
        val artists = ids.mapNotNull { aid ->
            val a = findArtist(aid)
            MediaMetadata.Artist(
                id = songIdToLong(aid),
                name = a?.name ?: artistNameById[aid] ?: "未知艺术家",
                picUrl = absoluteCover(a?.avatarUrl),
            )
        }.ifEmpty {
            listOf(MediaMetadata.Artist(id = 0L, name = artistName(song), picUrl = null))
        }
        return MediaMetadata(
            id = mediaId,
            title = song.title ?: "未知歌曲",
            coverUrl = cover,
            artists = artists,
            duration = ((song.duration ?: 0.0) * 1000).toLong(),
            album = MediaMetadata.Album(
                id = song.albumId?.let { songIdToLong(it) } ?: 0L,
                title = albumTitle,
            ),
        )
    }

    fun likedSongs(): List<MusetagSong> {
        val dates = user?.likedSongs?.associate { it.id to (it.date ?: 0L) } ?: return emptyList()
        return library.songs
            .filter { it.id in dates }
            .sortedByDescending { dates[it.id] ?: 0L }
    }

    fun likedAlbums(): List<MusetagAlbum> {
        val dates = user?.likedAlbums?.associate { it.id to (it.date ?: 0L) } ?: return emptyList()
        return library.albums
            .filter { it.id in dates }
            .sortedByDescending { dates[it.id] ?: 0L }
    }

    fun likedArtists(): List<MusetagArtist> {
        val dates = user?.likedArtists?.associate { it.id to (it.date ?: 0L) } ?: return emptyList()
        return library.artists
            .filter { it.id in dates }
            .sortedByDescending { dates[it.id] ?: 0L }
    }

    fun findAlbum(id: String): MusetagAlbum? {
        if (id.isBlank()) return null
        albumById[id]?.let { return it }
        library.albums.find { it.id == id }?.let { return it }
        val variants = listOf(
            id,
            runCatching { java.net.URLDecoder.decode(id, "UTF-8") }.getOrDefault(id),
        )
        for (v in variants) {
            library.albums.find { it.id == v }?.let { return it }
            albumById[v]?.let { return it }
        }
        return null
    }

    fun findArtist(id: String): MusetagArtist? {
        if (id.isBlank()) return null
        library.artists.find { it.id == id }?.let { return it }
        val variants = linkedSetOf(
            id,
            runCatching { java.net.URLDecoder.decode(id, "UTF-8") }.getOrDefault(id),
        )
        runCatching {
            val dec = android.util.Base64.decode(id, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            if (dec.isNotEmpty()) variants.add(String(dec, Charsets.UTF_8))
        }
        for (v in variants) {
            library.artists.find { it.id == v }?.let { return it }
        }
        return library.artists.find {
            it.name.equals(id, ignoreCase = true) ||
                it.name?.contains(id.substringAfterLast(':'), true) == true
        }
    }

    /** 播放器/歌曲入口常传 Long 哈希 id，需反查 musetag artist */
    fun resolveArtist(key: String): MusetagArtist? {
        findArtist(key)?.let { return it }
        val longKey = key.toLongOrNull() ?: return null
        library.artists.find { songIdToLong(it.id) == longKey }?.let { return it }
        longToSongId[longKey]?.let { sid ->
            val song = idToSong[sid] ?: return@let
            song.artistId?.let { findArtist(it) }?.let { return it }
            song.artistIds?.forEach { aid -> findArtist(aid)?.let { return it } }
            findArtist(artistName(song))?.let { return it }
        }
        return null
    }

    fun resolveAlbum(key: String): MusetagAlbum? {
        findAlbum(key)?.let { return it }
        val longKey = key.toLongOrNull() ?: return null
        library.albums.find { songIdToLong(it.id) == longKey }?.let { return it }
        longToSongId[longKey]?.let { sid ->
            val song = idToSong[sid] ?: return@let
            song.albumId?.let { findAlbum(it) }?.let { return it }
        }
        return null
    }

    fun albumsOfArtist(artistId: String): List<MusetagAlbum> {
        val artist = resolveArtist(artistId)
        val ids = buildSet {
            add(artistId)
            artist?.id?.let { add(it) }
            artistId.toLongOrNull()?.let { ln ->
                library.artists.find { songIdToLong(it.id) == ln }?.id?.let { add(it) }
            }
        }
        val name = artist?.name
        return library.albums.filter { a ->
            a.artistId in ids ||
                (name != null && (a.artistId?.let { artistNameById[it] }?.contains(name, true) == true ||
                    library.songs.any { it.albumId == a.id && artistName(it).contains(name, true) }))
        }.sortedByDescending { it.year ?: 0 }
    }

    fun songsOfArtist(artistId: String): List<MusetagSong> {
        if (artistId.isBlank()) return emptyList()
        val artist = resolveArtist(artistId)
        val ids = buildSet {
            add(artistId)
            artist?.id?.let { add(it) }
            artistId.toLongOrNull()?.let { ln ->
                library.artists.find { songIdToLong(it.id) == ln }?.id?.let { add(it) }
            }
        }
        val name = artist?.name
        return library.songs.filter { s ->
            s.artistId in ids ||
                s.artistIds?.any { it in ids } == true ||
                (name != null && artistName(s).contains(name, ignoreCase = true))
        }
    }

    fun searchAlbums(query: String, limit: Int = 50): List<MusetagAlbum> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return library.albums.filter {
            it.title?.contains(q, true) == true ||
                artistNameById[it.artistId ?: ""]?.contains(q, true) == true
        }.take(limit)
    }

    fun searchArtists(query: String, limit: Int = 50): List<MusetagArtist> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return library.artists.filter { it.name?.contains(q, true) == true }.take(limit)
    }

    fun recentAlbums(limit: Int = 20): List<MusetagAlbum> {
        val albumMaxDate = HashMap<String, Long>()
        library.songs.forEach { s ->
            val d = s.dateAdded ?: return@forEach
            val a = s.albumId ?: return@forEach
            if (d > (albumMaxDate[a] ?: 0L)) albumMaxDate[a] = d
        }
        return library.albums
            .sortedByDescending { albumMaxDate[it.id] ?: 0L }
            .take(limit)
    }

    fun update(lib: MusetagLibrary) {
        synchronized(this) {
            library = lib
            version++
            idToSong.clear()
            artistNameById.clear()
            albumById.clear()
            longToAudioUrl.clear()
            lib.artists.forEach { a ->
                if (!a.name.isNullOrBlank()) artistNameById[a.id] = a.name
            }
            lib.albums.forEach { a -> albumById[a.id] = a }
            lib.songs.forEach { s ->
                idToSong[s.id] = s
                val longId = songIdToLong(s.id)
                val url = MusetagClient.audioUrl(s.filePath?.takeIf { it.isNotBlank() } ?: s.audioUrl)
                if (url != null) longToAudioUrl[longId] = url
            }
        }
    }

    fun clear() {
        synchronized(this) {
            library = MusetagLibrary()
            user = null
            version++
            idToSong.clear()
            artistNameById.clear()
            albumById.clear()
            longToSongId.clear()
            songIdToLong.clear()
            longToAudioUrl.clear()
        }
    }

    fun search(query: String, limit: Int = 300): List<MusetagSong> {
        val q = query.trim()
        if (q.isEmpty()) return library.songs
        val out = ArrayList<MusetagSong>(64)
        for (s in library.songs) {
            val hit = s.title?.contains(q, true) == true ||
                artistNameById[s.artistId ?: ""]?.contains(q, true) == true ||
                s.artistIds?.any { artistNameById[it]?.contains(q, true) == true } == true ||
                (s.albumId?.let { albumById[it]?.title }?.contains(q, true) == true)
            if (hit) {
                out.add(s)
                if (out.size >= limit) break
            }
        }
        return out
    }

    private fun cacheFile(context: Context) = File(context.filesDir, "musetag_library.json")

    fun saveCache(context: Context) {
        runCatching {
            val lib = library
            if (lib.songs.isEmpty()) return
            cacheFile(context).writeText(gson.toJson(lib))
        }
    }

    fun loadCache(context: Context): Boolean {
        return runCatching {
            val file = cacheFile(context)
            if (!file.exists() || file.length() == 0L) return false
            val lib = gson.fromJson(file.readText(), MusetagLibrary::class.java) ?: return false
            if (lib.songs.isEmpty()) return false
            update(lib)
            true
        }.getOrDefault(false)
    }
}
