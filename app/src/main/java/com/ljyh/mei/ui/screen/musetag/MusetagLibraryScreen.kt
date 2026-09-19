package com.ljyh.mei.ui.screen.musetag

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.ljyh.mei.R
import com.ljyh.mei.constants.MusetagServerKey
import com.ljyh.mei.constants.MusetagSessionKey
import com.ljyh.mei.constants.MusetagUsernameKey
import com.ljyh.mei.data.model.toMediaItem
import com.ljyh.mei.musetag.MusetagAlbum
import com.ljyh.mei.musetag.MusetagArtist
import com.ljyh.mei.musetag.MusetagClient
import com.ljyh.mei.musetag.MusetagSong
import com.ljyh.mei.musetag.MusetagStore
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MusetagTab { Songs, Albums, Artists, Search }

private const val PAGE_SIZE = 60

@OptIn(UnstableApi::class)
@Composable
fun MusetagLibraryScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val playerConnection = LocalPlayerConnection.current
    val (session) = remember { mutableStateOf(MusetagClient.currentSession()) }
    val (username) = remember { mutableStateOf(MusetagClient.currentUsername()) }
    val (server) = remember { mutableStateOf(MusetagClient.currentBase()) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(MusetagTab.Songs) }
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var albumFilter by remember { mutableStateOf<MusetagAlbum?>(null) }
    var artistFilter by remember { mutableStateOf<MusetagArtist?>(null) }
    var libVersion by remember { mutableIntStateOf(MusetagStore.version) }
    var visibleCount by remember { mutableIntStateOf(PAGE_SIZE) }

    // 搜索防抖，避免每键全库扫描
    LaunchedEffect(query) {
        if (query.isBlank()) {
            debouncedQuery = ""
            return@LaunchedEffect
        }
        delay(280)
        debouncedQuery = query
    }

    // 首帧用磁盘缓存立刻出列表；网络刷新不挡 UI
    LaunchedEffect(session) {
        if (session.isBlank()) {
            loading = false
            error = null
            return@LaunchedEffect
        }
        if (MusetagStore.library.songs.isEmpty()) {
            withContext(Dispatchers.IO) { MusetagStore.loadCache(context) }
            libVersion = MusetagStore.version
        }
        val needFullScreen = MusetagStore.library.songs.isEmpty()
        if (needFullScreen) loading = true
        val result = withContext(Dispatchers.IO) { MusetagClient.fetchLibrary() }
        loading = false
        result.fold(
            onSuccess = {
                withContext(Dispatchers.IO) { MusetagStore.saveCache(context) }
                libVersion = MusetagStore.version
            },
            onFailure = { e ->
                if (MusetagStore.library.songs.isEmpty()) error = e.message ?: "加载失败"
            },
        )
    }

    val displayed by remember(libVersion) {
        derivedStateOf {
            val lib = MusetagStore.library
            when {
                albumFilter != null -> MusetagStore.songsOfAlbum(albumFilter!!.id)
                artistFilter != null -> MusetagStore.songsOfArtist(artistFilter!!.id)
                tab == MusetagTab.Search && debouncedQuery.isNotBlank() ->
                    MusetagStore.search(debouncedQuery, limit = 200)
                else -> lib.songs
            }
        }
    }
    val albums by remember(libVersion) { derivedStateOf { MusetagStore.library.albums } }
    val artists by remember(libVersion) { derivedStateOf { MusetagStore.library.artists } }

    fun playAll(list: List<MusetagSong>, startIndex: Int = 0) {
        if (list.isEmpty() || playerConnection == null) return
        // 窗口化队列：避免一次 Map 全库导致主线程/jank/ANR
        val window = 120
        val from = (startIndex - 3).coerceAtLeast(0)
        val slice = list.drop(from).take(window)
        val idxInSlice = (startIndex - from).coerceIn(0, slice.lastIndex.coerceAtLeast(0))
        if (slice.isEmpty()) return
        scope.launch(Dispatchers.Default) {
            val queue = slice.map { song ->
                song.id to MusetagStore.toMediaMetadata(song).toMediaItem()
            }
            withContext(Dispatchers.Main) {
                playerConnection.playQueue(
                    ListQueue(
                        id = "musetag",
                        title = null,
                        items = queue,
                        startIndex = idxInSlice,
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.musetag_library),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = buildString {
                        append(username.ifBlank { server.ifBlank { "musetag" } })
                        append(" · ")
                        append(MusetagStore.library.songs.size)
                        append(" ")
                        append("首")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = {
                scope.launch {
                    MusetagClient.clearSession()
                    navController.navigate(Screen.MusetagLogin.route)
                }
            }) {
                Text(stringResource(R.string.musetag_logout))
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                query = value
                if (value.isNotBlank()) tab = MusetagTab.Search
                albumFilter = null
                artistFilter = null
                visibleCount = PAGE_SIZE
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(stringResource(R.string.musetag_search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            MusetagTab.entries.forEach { t ->
                TextButton(onClick = {
                    tab = t
                    if (t != MusetagTab.Search) query = ""
                    albumFilter = null
                    artistFilter = null
                    visibleCount = PAGE_SIZE
                }) {
                    Text(
                        text = when (t) {
                            MusetagTab.Songs -> stringResource(R.string.musetag_songs)
                            MusetagTab.Albums -> stringResource(R.string.musetag_albums)
                            MusetagTab.Artists -> stringResource(R.string.musetag_artists)
                            MusetagTab.Search -> stringResource(R.string.musetag_search)
                        },
                        color = if (tab == t) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (albumFilter != null || artistFilter != null) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = albumFilter?.title ?: artistFilter?.name ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    albumFilter = null
                    artistFilter = null
                    visibleCount = PAGE_SIZE
                }) { Text(stringResource(R.string.musetag_clear_filter)) }
            }
        }

        when {
            loading && MusetagStore.library.songs.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.padding(8.dp))
                        Text(
                            stringResource(R.string.musetag_loading_library),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            session.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.musetag_not_logged_in))
                    TextButton(onClick = { navController.navigate(Screen.MusetagLogin.route) }) {
                        Text(stringResource(R.string.musetag_login))
                    }
                }
            }
            error != null && MusetagStore.library.songs.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(error ?: "", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        TextButton(onClick = {
                            scope.launch {
                                loading = true
                                val r = withContext(Dispatchers.IO) { MusetagClient.fetchLibrary() }
                                loading = false
                                r.onSuccess {
                                    withContext(Dispatchers.IO) { MusetagStore.saveCache(context) }
                                    libVersion = MusetagStore.version
                                    error = null
                                }.onFailure { error = it.message }
                            }
                        }) { Text(stringResource(R.string.musetag_retry)) }
                    }
                }
            }
            tab == MusetagTab.Albums -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumRow(album = album, onClick = {
                        albumFilter = album
                        artistFilter = null
                        tab = MusetagTab.Songs
                        visibleCount = PAGE_SIZE
                    })
                }
            }
            tab == MusetagTab.Artists -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(artists, key = { it.id }) { artist ->
                    ArtistRow(artist = artist, onClick = {
                        artistFilter = artist
                        albumFilter = null
                        tab = MusetagTab.Songs
                        visibleCount = PAGE_SIZE
                    })
                }
            }
            else -> {
                val page = displayed.take(visibleCount)
                if (displayed.isNotEmpty()) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                        TextButton(onClick = { playAll(displayed, 0) }) {
                            Text(stringResource(R.string.musetag_play_all))
                        }
                        if (loading) {
                            Text(
                                stringResource(R.string.musetag_refreshing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 0.dp,
                        bottom = insets.calculateBottomPadding() + 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(page, key = { it.id }) { song ->
                        val index = displayed.indexOfFirst { it.id == song.id }
                        SongRow(
                            song = song,
                            onClick = { playAll(displayed, index.coerceAtLeast(0)) },
                        )
                    }
                    if (displayed.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.musetag_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    if (visibleCount < displayed.size) {
                        item(key = "more") {
                            TextButton(
                                onClick = { visibleCount += PAGE_SIZE },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(R.string.musetag_load_more) +
                                        " (${displayed.size - visibleCount})",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: MusetagSong, onClick: () -> Unit) {
    // 预计算，避免 Row 内每帧查表
    val cover = remember(song.id) { MusetagStore.albumCover(song) }
    val subtitle = remember(song.id) {
        buildString {
            append(MusetagStore.artistName(song))
            val album = MusetagStore.albumTitle(song)
            if (album.isNotBlank()) append(" · ").append(album)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title ?: "未知歌曲",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

@Composable
private fun AlbumRow(album: MusetagAlbum, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = MusetagStore.absoluteCover(album.coverUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(album.title ?: "未知专辑", maxLines = 1, overflow = TextOverflow.Ellipsis)
            val count = remember(album.id) { MusetagStore.songsOfAlbum(album.id).size }
            Text(
                text = "${album.year ?: ""} · $count ${stringResource(R.string.musetag_song_unit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtistRow(artist: MusetagArtist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = MusetagStore.absoluteCover(artist.avatarUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(artist.name ?: "未知艺术家", maxLines = 1, overflow = TextOverflow.Ellipsis)
            val count = remember(artist.id) { MusetagStore.songsOfArtist(artist.id).size }
            Text(
                text = "$count ${stringResource(R.string.musetag_song_unit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
