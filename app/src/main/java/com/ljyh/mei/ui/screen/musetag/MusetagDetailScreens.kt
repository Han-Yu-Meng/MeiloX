package com.ljyh.mei.ui.screen.musetag

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ljyh.mei.R
import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.data.model.toMediaItem
import com.ljyh.mei.musetag.MusetagAlbum
import com.ljyh.mei.musetag.MusetagClient
import com.ljyh.mei.musetag.MusetagSong
import com.ljyh.mei.musetag.MusetagStore
import com.ljyh.mei.playback.PlayerConnection
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.component.item.Track
import com.ljyh.mei.ui.glass.IosListRow
import com.ljyh.mei.ui.glass.IosPinnedPage
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.model.UiPlaylist
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.playlist.CommonSongListScreen
import com.ljyh.mei.utils.largeImage
import com.ljyh.mei.utils.middleImage
import com.ljyh.mei.utils.smallImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

fun encodeMusetagId(id: String): String =
    android.util.Base64.encodeToString(id.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)

fun decodeMusetagId(raw: String): String {
    if (raw.isBlank()) return raw
    runCatching {
        val decoded = android.util.Base64.decode(raw, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        if (decoded.isNotEmpty()) return String(decoded, Charsets.UTF_8)
    }
    return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
}

@Composable
fun DetailSongRow(
    song: MusetagSong,
    showCover: Boolean = true,
    onClick: () -> Unit,
) {
    val cover = remember(song.id) { MusetagStore.albumCover(song).smallImage() }
    val subtitle = remember(song.id) { MusetagStore.artistName(song) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCover) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 专辑页：直接复用原仓库 CommonSongListScreen（同一套头部/列表/动画） */
@Composable
fun MusetagAlbumScreen(encodedId: String) {
    val albumId = decodeMusetagId(encodedId)
    val navController = LocalNavController.current
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return

    var album by remember { mutableStateOf(MusetagStore.resolveAlbum(albumId)) }
    var tracks by remember { mutableStateOf(emptyList<MediaMetadata>()) }

    LaunchedEffect(albumId) {
        com.ljyh.mei.musetag.MusetagBootstrap.awaitCache()
        com.ljyh.mei.musetag.MusetagBootstrap.silentRefreshOnce(context)
        album = MusetagStore.resolveAlbum(albumId)
        val cover = MusetagStore.absoluteCover(album?.coverUrl) ?: ""
        tracks = MusetagStore.songsOfAlbum(album?.id ?: albumId).map {
            MusetagStore.toMediaMetadata(it).copy(coverUrl = cover)
        }
    }

    val uiData = remember(album, tracks) {
        UiPlaylist(
            id = album?.id?.hashCode()?.toLong() ?: 0L,
            title = album?.title.orEmpty(),
            count = tracks.size,
            subscriberCount = 0L,
            cover = MusetagStore.absoluteCover(album?.coverUrl) ?: "",
            coverList = listOf(MusetagStore.absoluteCover(album?.coverUrl) ?: ""),
            creatorName = tracks.firstOrNull()?.artists?.joinToString { a -> a.name }.orEmpty(),
            description = null,
            tracks = tracks,
            isSubscribed = false,
        )
    }

    fun buildQueue(startIndex: Int): ListQueue? {
        if (tracks.isEmpty()) return null
        val items = tracks.map { it.id.toString() to it.toMediaItem() }
        return ListQueue(
            id = "album_musetag",
            title = uiData.title,
            items = items,
            startIndex = startIndex.coerceIn(0, items.lastIndex),
        )
    }

    CommonSongListScreen(
        uiData = uiData,
        pagingItems = null,
        isLoading = album == null && MusetagStore.library.songs.isEmpty(),
        onPlayAll = { buildQueue(0)?.let { playerConnection.playQueue(it) } },
        onHeaderAction = {},
        headerActionIcon = Icons.Default.FavoriteBorder,
        headerActionLabel = stringResource(R.string.musetag_albums),
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

/** 艺人页：结构对齐原 ArtistScreen（Hero / 热门 / 全部 / 专辑横滑） */
@Composable
fun MusetagArtistScreen(encodedId: String) {
    val artistId = decodeMusetagId(encodedId)
    val navController = LocalNavController.current
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()

    var artistName by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var desc by remember { mutableStateOf<String?>(null) }
    var songs by remember { mutableStateOf(emptyList<MusetagSong>()) }
    var albums by remember { mutableStateOf(emptyList<MusetagAlbum>()) }
    var descExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(artistId) {
        com.ljyh.mei.musetag.MusetagBootstrap.awaitCache()
        com.ljyh.mei.musetag.MusetagBootstrap.silentRefreshOnce(context)
        val a = MusetagStore.resolveArtist(artistId)
        artistName = a?.name.orEmpty()
        avatarUrl = MusetagStore.absoluteCover(a?.avatarUrl)
        desc = a?.description?.takeIf { it.isNotBlank() }
        songs = MusetagStore.songsOfArtist(artistId)
        albums = MusetagStore.albumsOfArtist(artistId)
    }

    val hotSongs = remember(songs) {
        songs.sortedByDescending { it.playCount ?: 0 }.take(10).ifEmpty { songs.take(10) }
    }

    val scrollState = rememberLazyListState()
    val heroHeightPx = with(LocalDensity.current) { 320.dp.toPx() }
    val topBarCollapseProgress by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex > 0) 1f
            else (scrollState.firstVisibleItemScrollOffset / (heroHeightPx * 0.6f)).coerceIn(0f, 1f)
        }
    }

    fun playSongs(list: List<MusetagSong>, start: Int) {
        val q = musetagQueueOf(list, start, artistName) ?: return
        playerConnection.playQueue(q)
    }

    Box(Modifier.fillMaxSize()) {
        IosPinnedPage(
            title = artistName,
            bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(),
            collapseProgress = topBarCollapseProgress,
            onNavigateBack = { navController.popBackStack() },
        ) { contentPadding ->
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                item("hero") {
                    var followed by remember(artistName) {
                        mutableStateOf(
                            MusetagStore.isArtistLiked(artistId)
                        )
                    }
                    MusetagArtistHero(
                        name = artistName,
                        avatarUrl = avatarUrl,
                        songCount = songs.size,
                        albumCount = albums.size,
                        description = desc,
                        descExpanded = descExpanded,
                        onToggleDesc = { descExpanded = !descExpanded },
                        isFollowed = followed,
                        onFollowClick = {
                            scope.launch {
                                val next = MusetagStore.toggleArtistLike(artistId)
                                followed = next
                            }
                        },
                    )
                }

                item("hot_title") {
                    Text(
                        text = stringResource(R.string.musetag_hot_songs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(hotSongs, key = { "hot-${it.id}" }) { song ->
                    val meta = remember(song.id) { MusetagStore.toMediaMetadata(song) }
                    Track(
                        track = meta,
                        onClick = {
                            playerConnection.onTrackClicked(
                                trackId = meta.id.toString(),
                                buildQueue = { musetagQueueOf(hotSongs, hotSongs.indexOfFirst { it.id == song.id }, artistName) },
                            )
                        },
                        onMoreClick = null,
                    )
                }

                if (songs.isNotEmpty()) {
                    item("all_songs") {
                        IosListRow(
                            title = stringResource(R.string.musetag_all_songs_fmt, songs.size),
                            modifier = Modifier.padding(horizontal = 6.dp),
                            onClick = {
                                Screen.ArtistSongs.navigate(navController) {
                                    addPath(encodeMusetagId(artistId))
                                }
                            },
                            trailing = {
                                SfIcon(
                                    "chevron.forward",
                                    null,
                                    modifier = Modifier.padding(start = 8.dp),
                                    size = 12.dp,
                                    tint = LocalGlassColors.current.secondaryContent,
                                )
                            },
                        )
                    }
                }

                if (albums.isNotEmpty()) {
                    item("album_title") {
                        Text(
                            text = stringResource(R.string.musetag_albums),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    item("album_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(albums, key = { it.id }) { album ->
                                MusetagAlbumCard(
                                    album = album,
                                    onClick = {
                                        Screen.Album.navigate(navController) {
                                            addPath(encodeMusetagId(album.id))
                                        }
                                    },
                                )
                            }
                        }
                    }
                    item("all_albums") {
                        IosListRow(
                            title = stringResource(R.string.musetag_all_albums_fmt, albums.size),
                            modifier = Modifier.padding(horizontal = 6.dp),
                            onClick = {
                                Screen.MusetagArtistAlbums.navigate(navController) {
                                    addPath(encodeMusetagId(artistId))
                                }
                            },
                            trailing = {
                                SfIcon(
                                    "chevron.forward",
                                    null,
                                    modifier = Modifier.padding(start = 8.dp),
                                    size = 12.dp,
                                    tint = LocalGlassColors.current.secondaryContent,
                                )
                            },
                        )
                    }
                }

                if (songs.isEmpty() && albums.isEmpty() && artistName.isBlank()) {
                    item("empty") {
                        Text(
                            stringResource(R.string.musetag_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusetagArtistHero(
    name: String,
    avatarUrl: String?,
    songCount: Int,
    albumCount: Int,
    description: String?,
    descExpanded: Boolean,
    onToggleDesc: () -> Unit,
    isFollowed: Boolean,
    onFollowClick: () -> Unit,
) {
    val bgColor = MaterialTheme.colorScheme.background
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            AsyncImage(
                model = avatarUrl?.largeImage(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to bgColor.copy(alpha = 0.20f),
                                0.35f to Color.Transparent,
                                0.62f to bgColor.copy(alpha = 0.55f),
                                0.80f to bgColor.copy(alpha = 0.88f),
                                1.00f to bgColor,
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AsyncImage(
                        model = avatarUrl?.middleImage(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text("$songCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.musetag_songs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f))
                    }
                    Column {
                        Text("$albumCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.musetag_albums), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f))
                    }
                }
            }
        }

        // 关注 + 简介：对齐原版 ArtistScreen Body
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            com.ljyh.mei.ui.glass.GlassButton(
                onClick = onFollowClick,
                emphasis = if (isFollowed) com.ljyh.mei.ui.glass.GlassEmphasis.Prominent
                else com.ljyh.mei.ui.glass.GlassEmphasis.Regular,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
            ) {
                Text(
                    stringResource(
                        if (isFollowed) R.string.artist_following else R.string.artist_follow
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    lineHeight = 20.sp,
                    maxLines = if (descExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (descExpanded) stringResource(R.string.musetag_collapse)
                    else stringResource(R.string.musetag_expand_all),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 8.dp)
                        .clickable(onClick = onToggleDesc),
                )
            }
        }
    }
}

@Composable
private fun MusetagAlbumCard(album: MusetagAlbum, onClick: () -> Unit) {
    val size = remember(album.id) { MusetagStore.songsOfAlbum(album.id).size }
    Column(
        modifier = Modifier
            .width(116.dp)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = MusetagStore.absoluteCover(album.coverUrl)?.middleImage(),
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(116.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.title.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$size ${stringResource(R.string.musetag_song_unit)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.42f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
