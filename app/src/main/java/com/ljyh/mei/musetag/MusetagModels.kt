package com.ljyh.mei.musetag

import com.google.gson.annotations.SerializedName

data class MusetagUser(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("likedSongs") val likedSongs: List<MusetagLiked>? = null,
    @SerializedName("likedAlbums") val likedAlbums: List<MusetagLiked>? = null,
    @SerializedName("likedArtists") val likedArtists: List<MusetagLiked>? = null,
    @SerializedName("playlists") val playlists: List<MusetagPlaylist>? = null,
)

data class MusetagLiked(
    @SerializedName("id") val id: String,
    @SerializedName("date") val date: Long?,
)

data class MusetagPlaylist(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String?,
    @SerializedName("content") val content: List<MusetagPlaylistItem>?,
    @SerializedName("coverUrl") val coverUrl: String? = null,
)

data class MusetagPlaylistItem(
    @SerializedName("songId") val songId: String,
    @SerializedName("date") val date: Long?,
)

data class MusetagLibrary(
    @SerializedName("songs") val songs: List<MusetagSong> = emptyList(),
    @SerializedName("albums") val albums: List<MusetagAlbum> = emptyList(),
    @SerializedName("artists") val artists: List<MusetagArtist> = emptyList(),
    @SerializedName("tags") val tags: List<MusetagTag> = emptyList(),
    @SerializedName("version") val version: Long = 0,
)

data class MusetagSong(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String?,
    @SerializedName("artistId") val artistId: String? = null,
    @SerializedName("artistIds") val artistIds: List<String>? = null,
    @SerializedName("albumId") val albumId: String? = null,
    @SerializedName("trackNumber") val trackNumber: Int? = null,
    @SerializedName("discNumber") val discNumber: Int? = null,
    @SerializedName("duration") val duration: Double? = null,
    @SerializedName("filePath") val filePath: String? = null,
    @SerializedName("playCount") val playCount: Int? = null,
    @SerializedName("dateAdded") val dateAdded: Long? = null,
    @SerializedName("audioUrl") val audioUrl: String? = null,
    @SerializedName("tagIds") val tagIds: List<String>? = null,
)

data class MusetagAlbum(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String?,
    @SerializedName("artistId") val artistId: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("coverUrl") val coverUrl: String? = null,
    @SerializedName("tags") val tags: List<String>? = null,
)

data class MusetagArtist(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String?,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("tags") val tags: List<String>? = null,
)

data class MusetagTag(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String?,
    @SerializedName("category") val category: String? = null,
    @SerializedName("parentId") val parentId: String? = null,
)

data class MusetagLyrics(
    @SerializedName("lrcContent") val lrcContent: String? = null,
    @SerializedName("ttmlContent") val ttmlContent: String? = null,
)

data class MusetagLoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
)
