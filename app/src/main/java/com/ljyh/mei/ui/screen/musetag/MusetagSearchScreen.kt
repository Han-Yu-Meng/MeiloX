package com.ljyh.mei.ui.screen.musetag

import androidx.compose.foundation.clickable
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
import com.ljyh.mei.musetag.MusetagClient
import com.ljyh.mei.musetag.MusetagStore
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.utils.smallImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 全局搜索框提交后传入 musetag 搜索 */
object MusetagSearchQuery {
    @Volatile
    var pending: String? = null
}

/** 搜索本机已同步的 musetag 曲库（与网页端本地检索一致） */
@Composable
fun MusetagSearchScreen(
    initialQuery: String? = null,
    onQuerySync: ((String) -> Unit)? = null,
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val playerConnection = LocalPlayerConnection.current

    var query by remember {
        mutableStateOf(initialQuery ?: MusetagSearchQuery.pending.orEmpty())
    }
    var debounced by remember { mutableStateOf(query) }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        MusetagSearchQuery.pending?.let {
            if (it.isNotEmpty() && query.isEmpty()) query = it
            MusetagSearchQuery.pending = null
        }
        com.ljyh.mei.musetag.MusetagBootstrap.awaitCache()
        com.ljyh.mei.musetag.MusetagBootstrap.silentRefreshOnce(context)
    }

    LaunchedEffect(query) {
        onQuerySync?.invoke(query)
        delay(200)
        debounced = query
    }

    val songs = remember(debounced) { MusetagStore.search(debounced, limit = 200) }
    val albums = remember(debounced) { MusetagStore.searchAlbums(debounced) }
    val artists = remember(debounced) { MusetagStore.searchArtists(debounced) }

    fun playList(list: List<com.ljyh.mei.musetag.MusetagSong>, index: Int) {
        if (list.isEmpty() || playerConnection == null) return
        val from = (index - 2).coerceAtLeast(0)
        val slice = list.drop(from).take(100)
        if (slice.isEmpty()) return
        val idx = (index - from).coerceIn(0, slice.lastIndex)
        scope.launch(Dispatchers.Default) {
            val queue = slice.map { it.id to MusetagStore.toMediaMetadata(it).toMediaItem() }
            withContext(Dispatchers.Main) {
                playerConnection.playQueue(
                    ListQueue(id = "musetag-search", title = debounced, items = queue, startIndex = idx)
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding()),
    ) {
        Text(
            text = stringResource(R.string.musetag_search),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(stringResource(R.string.musetag_search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
            listOf(
                stringResource(R.string.musetag_songs) to 0,
                stringResource(R.string.musetag_albums) to 1,
                stringResource(R.string.musetag_artists) to 2,
            ).forEach { (label, value) ->
                TextButton(onClick = { tab = value }) {
                    Text(
                        label,
                        color = if (tab == value) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        when (tab) {
            0 -> LazyColumn(
                contentPadding = PaddingValues(
                    bottom = insets.calculateBottomPadding() + 100.dp,
                ),
            ) {
                items(songs, key = { it.id }) { song ->
                    val index = songs.indexOfFirst { it.id == song.id }
                    DetailSongRow(song = song, onClick = { playList(songs, index) })
                }
                if (songs.isEmpty() && debounced.isNotBlank()) {
                    item { EmptySearchHint() }
                }
            }
            1 -> LazyColumn(
                contentPadding = PaddingValues(bottom = insets.calculateBottomPadding() + 100.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Screen.Album.navigate(navController) {
                                    addPath(encodeMusetagId(album.id))
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = MusetagStore.absoluteCover(album.coverUrl)?.smallImage(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(album.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val artist = album.artistId?.let { MusetagStore.findArtist(it)?.name } ?: ""
                            Text(
                                text = "${artist} · ${MusetagStore.songsOfAlbum(album.id).size} ${stringResource(R.string.musetag_song_unit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (albums.isEmpty() && debounced.isNotBlank()) {
                    item { EmptySearchHint() }
                }
            }
            2 -> LazyColumn(
                contentPadding = PaddingValues(bottom = insets.calculateBottomPadding() + 100.dp),
            ) {
                items(artists, key = { it.id }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Screen.Artist.navigate(navController) {
                                    addPath(encodeMusetagId(artist.id))
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = MusetagStore.absoluteCover(artist.avatarUrl)?.smallImage(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(24.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(artist.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${MusetagStore.songsOfArtist(artist.id).size} ${stringResource(R.string.musetag_song_unit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (artists.isEmpty() && debounced.isNotBlank()) {
                    item { EmptySearchHint() }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchHint() {
    Text(
        text = stringResource(R.string.musetag_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}
