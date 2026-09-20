package com.ljyh.mei.ui.screen.musetag

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ljyh.mei.R
import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.data.model.toMediaItem
import com.ljyh.mei.musetag.MusetagBootstrap
import com.ljyh.mei.musetag.MusetagStore
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.model.UiPlaylist
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.playlist.CommonSongListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 歌单详情：与专辑页共用 CommonSongListScreen
 * 封面 = 歌单第一首的专辑封面
 */
@Composable
fun MusetagPlaylistDetailScreen(encodedPlaylistId: String) {
    val playlistId = decodeMusetagId(encodedPlaylistId)
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return

    var tick by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf(emptyList<MediaMetadata>()) }
    var title by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }

    LaunchedEffect(playlistId, tick) {
        MusetagBootstrap.awaitCache()
        withContext(Dispatchers.IO) { MusetagBootstrap.silentRefreshOnce(com.ljyh.mei.AppContext.instance) }
        val pl = MusetagStore.findPlaylist(playlistId)
        title = pl?.title.orEmpty()
        val songs = MusetagStore.playlistSongs(playlistId)
        cover = pl?.coverUrl?.let { MusetagStore.absoluteCover(it) }.orEmpty()
            .ifBlank {
                songs.firstOrNull()?.let { MusetagStore.albumCover(it) }.orEmpty()
            }
        tracks = songs.map {
            MusetagStore.toMediaMetadata(it).copy(coverUrl = cover)
        }
    }

    val uiData = remember(title, tracks, cover) {
        UiPlaylist(
            id = playlistId.hashCode().toLong(),
            title = title,
            count = tracks.size,
            subscriberCount = 0L,
            cover = cover,
            coverList = listOf(cover),
            creatorName = "musetag",
            description = null,
            tracks = tracks,
            isSubscribed = false,
        )
    }

    fun buildQueue(startIndex: Int): ListQueue? {
        if (tracks.isEmpty()) return null
        val items = tracks.map { it.id.toString() to it.toMediaItem() }
        return ListQueue(
            id = "musetag_pl",
            title = title,
            items = items,
            startIndex = startIndex.coerceIn(0, items.lastIndex),
        )
    }

    CommonSongListScreen(
        uiData = uiData,
        pagingItems = null,
        isLoading = false,
        onPlayAll = { buildQueue(0)?.let { playerConnection.playQueue(it) } },
        onHeaderAction = {},
        headerActionIcon = Icons.Default.FavoriteBorder,
        headerActionLabel = stringResource(R.string.musetag_songs),
        onTrackClick = { meta, index ->
            val idx = tracks.indexOfFirst { it.id == meta.id }.takeIf { it >= 0 } ?: index
            playerConnection.onTrackClicked(
                trackId = meta.id.toString(),
                buildQueue = { buildQueue(idx) },
            )
        },
        onBack = { navController.navigateUp() },
    )
}

/** 歌单列表入口（首页/音乐库） */
@Composable
fun MusetagPlaylistsScreen() {
    val navController = LocalNavController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var playlists by remember { mutableStateOf(MusetagStore.playlists()) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        MusetagBootstrap.awaitCache()
        MusetagBootstrap.silentRefreshOnce(context)
        playlists = MusetagStore.playlists()
    }

    // 简单列表：点击进入 CommonSongListScreen 详情
    androidx.compose.foundation.lazy.LazyColumn {
        items(playlists, key = { it.id }) { pl ->
            val songs = MusetagStore.playlistSongs(pl.id)
            com.ljyh.mei.ui.glass.IosListRow(
                title = pl.title.orEmpty(),
                onClick = {
                    Screen.PlayList.navigate(navController) { addPath(encodeMusetagId(pl.id)) }
                },
            )
        }
    }
}
