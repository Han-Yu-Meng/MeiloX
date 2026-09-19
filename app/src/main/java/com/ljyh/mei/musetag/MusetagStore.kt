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

    fun songsOfArtist(artistId: String): List<MusetagSong> =
        library.songs.filter { s ->
            s.artistId == artistId || s.artistIds?.contains(artistId) == true
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
