package com.ljyh.mei.ui.screen.musetag

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ljyh.mei.R
import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.data.model.toMediaItem
import com.ljyh.mei.musetag.MusetagStore
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.model.UiPlaylist
import com.ljyh.mei.ui.screen.playlist.CommonSongListScreen

/**
 * 通用歌曲列表页：专辑 / 喜爱歌曲 / 歌单共用 CommonSongListScreen。
 * 封面统一用第一首曲的专辑封面。
 */
object MusetagSongListRequest {
    @Volatile
    var title: String = ""

    @Volatile
    var songIds: List<String> = emptyList()

    @Volatile
    var cover: String = ""

    @Volatile
    var creator: String = "musetag"
}

@Composable
fun MusetagSongListScreen() {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val title = MusetagSongListRequest.title
    val ids = MusetagSongListRequest.songIds
    val coverOverride = MusetagSongListRequest.cover
    val creator = MusetagSongListRequest.creator

    val tracks = remember(title, ids) {
        ids.mapNotNull { id -> MusetagStore.library.songs.find { it.id == id } }
            .map { MusetagStore.toMediaMetadata(it) }
    }

    val cover = remember(tracks, coverOverride) {
        if (coverOverride.isNotBlank()) coverOverride
        else tracks.firstOrNull()?.coverUrl.orEmpty()
    }

    val uiData = remember(title, tracks, cover, creator) {
        UiPlaylist(
            id = title.hashCode().toLong(),
            title = title,
            count = tracks.size,
            subscriberCount = 0L,
            cover = cover,
            coverList = listOf(cover),
            creatorName = creator,
            description = null,
            tracks = tracks,
            isSubscribed = false,
        )
    }

    fun buildQueue(startIndex: Int): ListQueue? {
        if (tracks.isEmpty()) return null
        val items = tracks.map { it.id.toString() to it.toMediaItem() }
        return ListQueue(
            id = "musetag_list_${title.hashCode()}",
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

/** 组装并导航到通用歌曲列表 */
fun openMusetagSongList(
    navController: com.ljyh.mei.ui.navigation.MeiNavigator,
    title: String,
    songs: List<com.ljyh.mei.musetag.MusetagSong>,
    creator: String = "musetag",
) {
    val firstCover = songs.firstOrNull()?.let { MusetagStore.albumCover(it) }.orEmpty()
    MusetagSongListRequest.title = title
    MusetagSongListRequest.songIds = songs.map { it.id }
    MusetagSongListRequest.cover = firstCover
    MusetagSongListRequest.creator = creator
    navController.navigate(com.ljyh.mei.ui.screen.Screen.MusetagSongList.route)
}
