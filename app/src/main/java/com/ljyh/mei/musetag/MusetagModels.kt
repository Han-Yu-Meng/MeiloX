package com.ljyh.mei.musetag

import com.google.gson.annotations.SerializedName

data class MusetagUser(
    val id: String,
    val username: String?,
    val role: String?,
    val avatarUrl: String?,
    val likedSongs: List<MusetagLiked>? = null,
    val likedAlbums: List<MusetagLiked>? = null,
    val likedArtists: List<MusetagLiked>? = null,
    val playlists: List<MusetagPlaylist>? = null,
)

data class MusetagLiked(val id: String, val date: Long?)

data class MusetagPlaylist(
    val id: String,
    val title: String?,
    val content: List<MusetagPlaylistItem>?,
    val coverUrl: String? = null,
)

data class MusetagPlaylistItem(val songId: String, val date: Long?)

data class MusetagLibrary(
    val songs: List<MusetagSong> = emptyList(),
    val albums: List<MusetagAlbum> = emptyList(),
    val artists: List<MusetagArtist> = emptyList(),
    val tags: List<MusetagTag> = emptyList(),
    val version: Long = 0,
)

data class MusetagSong(
    val id: String,
    val title: String?,
    val artistId: String? = null,
    val artistIds: List<String>? = null,
    val albumId: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val duration: Double? = null,
    val filePath: String? = null,
    val playCount: Int? = null,
    val dateAdded: Long? = null,
    val audioUrl: String? = null,
    val tagIds: List<String>? = null,
)

data class MusetagAlbum(
    val id: String,
    val title: String?,
    val artistId: String? = null,
    val year: Int? = null,
    val coverUrl: String? = null,
    val tags: List<String>? = null,
)

data class MusetagArtist(
    val id: String,
    val name: String?,
    val avatarUrl: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
)

data class MusetagTag(
    val id: String,
    val name: String?,
    val category: String? = null,
    val parentId: String? = null,
)

data class MusetagLyrics(
    @SerializedName("lrcContent") val lrcContent: String? = null,
    @SerializedName("ttmlContent") val ttmlContent: String? = null,
)

data class MusetagLoginRequest(val username: String, val password: String)
