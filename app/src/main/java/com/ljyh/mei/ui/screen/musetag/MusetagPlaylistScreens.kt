package com.ljyh.mei.ui.screen.musetag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import coil3.compose.AsyncImage
import com.ljyh.mei.R
import com.ljyh.mei.data.model.toMediaItem
import com.ljyh.mei.musetag.MusetagBootstrap
import com.ljyh.mei.musetag.MusetagPlaylist
import com.ljyh.mei.musetag.MusetagSong
import com.ljyh.mei.musetag.MusetagStore
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.glass.IosListRow
import com.ljyh.mei.ui.glass.IosPinnedPage
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.utils.smallImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add

/** 歌单列表 */
@Composable
fun MusetagPlaylistsScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    var playlists by remember { mutableStateOf(emptyList<MusetagPlaylist>()) }
    var showCreate by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        MusetagBootstrap.awaitCache()
        runCatching { com.ljyh.mei.musetag.MusetagBootstrap.silentRefreshOnce(context) }
        playlists = MusetagStore.playlists()
    }

    fun reload() {
        playlists = MusetagStore.playlists()
    }

    IosPinnedPage(
        title = stringResource(R.string.musetag_playlists),
        bottomPadding = insets.calculateBottomPadding(),
        onNavigateBack = { navController.popBackStack() },
        actions = {
            TextButton(onClick = { showCreate = true }) {
                Text(stringResource(R.string.musetag_new_playlist))
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                bottom = contentPadding.calculateBottomPadding(),
            ),
        ) {
            items(playlists, key = { it.id }) { pl ->
                val count = pl.content?.size ?: 0
                Column {
                    IosListRow(
                        title = pl.title.orEmpty().ifBlank { stringResource(R.string.musetag_new_playlist) },
                        onClick = {
                            Screen.PlayList.navigate(navController) {
                                addPath(encodeMusetagId(pl.id))
                            }
                        },
                    )
                    Text(
                        text = "$count ${stringResource(R.string.musetag_song_unit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                    )
                }
            }
            if (playlists.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.musetag_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.musetag_new_playlist)) },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.playlist_name_placeholder)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = createName.trim()
                    if (name.isBlank()) return@TextButton
                    scope.launch {
                        MusetagStore.createPlaylist(name)
                        createName = ""
                        showCreate = false
                        reload()
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            },
        )
    }
}

/** 歌单详情：列表 / 添加歌曲 / 更名 / 删除歌曲 */
@Composable
fun MusetagPlaylistDetailScreen(encodedPlaylistId: String) {
    val playlistId = decodeMusetagId(encodedPlaylistId)
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    var playlist by remember { mutableStateOf(MusetagStore.findPlaylist(playlistId)) }
    var songs by remember { mutableStateOf(MusetagStore.playlistSongs(playlistId)) }
    var tick by remember { mutableStateOf(0) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId, tick) {
        MusetagBootstrap.awaitCache()
        playlist = MusetagStore.findPlaylist(playlistId)
        songs = MusetagStore.playlistSongs(playlistId)
    }

    fun play(index: Int) {
        if (songs.isEmpty() || playerConnection == null) return
        val from = index.coerceAtLeast(0)
        val slice = songs.drop(from).take(150)
        val meta = slice.map { MusetagStore.toMediaMetadata(it) }
        val items = meta.map { it.id.toString() to it.toMediaItem() }
        playerConnection.playQueue(
            ListQueue(id = "musetag-pl", title = playlist?.title, items = items, startIndex = 0)
        )
    }

    IosPinnedPage(
        title = playlist?.title.orEmpty(),
        bottomPadding = insets.calculateBottomPadding(),
        onNavigateBack = { navController.popBackStack() },
        actions = {
            TextButton(onClick = { showAdd = true }) { Text(stringResource(R.string.musetag_add_songs)) }
            TextButton(onClick = {
                renameText = playlist?.title.orEmpty()
                showRename = true
            }) { Text(stringResource(R.string.musetag_rename_playlist)) }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                bottom = contentPadding.calculateBottomPadding(),
            ),
        ) {
            item("play") {
                if (songs.isNotEmpty()) {
                    TextButton(onClick = { play(0) }) {
                        Text(stringResource(R.string.musetag_play_all))
                    }
                }
            }
            items(songs, key = { it.id }) { song ->
                DetailSongRow(song = song, onClick = {
                    play(songs.indexOfFirst { it.id == song.id })
                })
            }
            if (songs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.musetag_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.musetag_rename_playlist)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = renameText.trim()
                    if (t.isBlank()) return@TextButton
                    scope.launch {
                        MusetagStore.renamePlaylist(playlistId, t)
                        showRename = false
                        tick++
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            },
        )
    }

    if (showAdd) {
        MusetagAddSongsSheet(
            playlistId = playlistId,
            onDismiss = { showAdd = false; tick++ },
        )
    }
}

@Composable
private fun MusetagAddSongsSheet(
    playlistId: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val allSongs = remember { MusetagStore.librarySongsSorted() }
    val current = remember { MusetagStore.findPlaylist(playlistId)?.content?.map { it.songId }?.toSet() ?: emptySet() }
    var added by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.musetag_add_songs)) },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(allSongs, key = { it.id }) { song ->
                    val isOn = song.id in added
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    if (isOn) {
                                        MusetagStore.removeSongFromPlaylist(playlistId, song.id)
                                        added = added - song.id
                                    } else {
                                        MusetagStore.addSongToPlaylist(playlistId, song.id)
                                        added = added + song.id
                                    }
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = MusetagStore.albumCover(song).smallImage(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                MusetagStore.artistName(song),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (isOn) "✓" else "+",
                            color = if (isOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.musetag_done)) }
        },
    )
}

/** 首页歌单区块 */
@Composable
fun MusetagPlaylistSection(onOpen: (String) -> Unit, onOpenAll: () -> Unit) {
    var playlists by remember { mutableStateOf(MusetagStore.playlists()) }
    LaunchedEffect(Unit) {
        playlists = MusetagStore.playlists()
    }
    if (playlists.isEmpty()) return
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.musetag_playlists),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenAll) {
                Text(stringResource(R.string.musetag_show_all))
            }
        }
        playlists.take(10).forEach { pl ->
            Column {
                IosListRow(
                    title = pl.title.orEmpty(),
                    onClick = { onOpen(pl.id) },
                )
                Text(
                    text = "${pl.content?.size ?: 0} ${stringResource(R.string.musetag_song_unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )
            }
        }
    }
}
