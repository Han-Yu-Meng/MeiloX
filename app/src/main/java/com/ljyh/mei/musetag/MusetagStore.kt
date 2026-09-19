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

    /** 全库歌曲：按加入时间（dateAdded）升序，先加入的先展示 */
    fun librarySongsSorted(): List<MusetagSong> =
        library.songs.sortedBy { it.dateAdded ?: 0L }

    /** 全库专辑：按专辑内最早 dateAdded 升序 */
    fun libraryAlbumsSorted(): List<MusetagAlbum> {
        val albumJoin = HashMap<String, Long>()
        library.songs.forEach { s ->
            val a = s.albumId ?: return@forEach
            val d = s.dateAdded ?: 0L
            val prev = albumJoin[a]
            if (prev == null || d < prev) albumJoin[a] = d
        }
        return library.albums.sortedBy { albumJoin[it.id] ?: Long.MAX_VALUE }
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

    fun toMediaMetadata(song: MusetagSong): MediaMetadata {
        val mediaId = songIdToLong(song.id)
        val artistName = artistName(song)
        val albumTitle = albumTitle(song)
        val cover = albumCover(song)
        val artistId = song.artistId ?: song.artistIds?.firstOrNull()
        return MediaMetadata(
            id = mediaId,
            title = song.title ?: "未知歌曲",
            coverUrl = cover,
            artists = listOf(
                MediaMetadata.Artist(
                    id = artistId?.let { songIdToLong(it) } ?: 0L,
                    name = artistName,
                )
            ),
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
