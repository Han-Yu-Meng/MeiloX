package com.ljyh.mei.ui.screen.musetag

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ljyh.mei.musetag.MusetagAlbum
import com.ljyh.mei.musetag.MusetagBootstrap
import com.ljyh.mei.musetag.MusetagStore
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.glass.IosPinnedPage
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.utils.smallImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun MusetagArtistSongsScreen(encodedArtistId: String) {
    val artistId = decodeMusetagId(encodedArtistId)
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    var artistName by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf(emptyList<com.ljyh.mei.musetag.MusetagSong>()) }

    LaunchedEffect(artistId) {
        MusetagBootstrap.awaitCache()
        artistName = MusetagStore.resolveArtist(artistId)?.name.orEmpty()
        songs = MusetagStore.songsOfArtist(artistId)
    }

    IosPinnedPage(
        title = artistName.ifBlank { stringResource(R.string.musetag_songs) },
        bottomPadding = insets.calculateBottomPadding(),
        onNavigateBack = { navController.popBackStack() },
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
            items(songs, key = { it.id }) { song ->
                val index = songs.indexOfFirst { it.id == song.id }
                DetailSongRow(song = song, showCover = true) {
                    if (playerConnection == null) return@DetailSongRow
                    val slice = songs.drop(index.coerceAtLeast(0)).take(150)
                    val q = buildMusetagQueue(slice, 0, artistName) ?: return@DetailSongRow
                    scope.launch(Dispatchers.Main) { playerConnection.playQueue(q) }
                }
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
}

@Composable
fun MusetagArtistAlbumsScreen(encodedArtistId: String) {
    val artistId = decodeMusetagId(encodedArtistId)
    val navController = LocalNavController.current
    val context = LocalContext.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    var artistName by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf(emptyList<MusetagAlbum>()) }

    LaunchedEffect(artistId) {
        MusetagBootstrap.awaitCache()
        artistName = MusetagStore.resolveArtist(artistId)?.name.orEmpty()
        albums = MusetagStore.albumsOfArtist(artistId)
    }

    IosPinnedPage(
        title = artistName.ifBlank { stringResource(R.string.musetag_albums) },
        bottomPadding = insets.calculateBottomPadding(),
        onNavigateBack = { navController.popBackStack() },
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
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.foundation.layout.Column {
                        Text(
                            album.title ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${album.year ?: ""} · ${MusetagStore.songsOfAlbum(album.id).size} ${stringResource(R.string.musetag_song_unit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (albums.isEmpty()) {
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
}

internal fun buildMusetagQueue(
    songs: List<com.ljyh.mei.musetag.MusetagSong>,
    startIndex: Int,
    title: String?,
): ListQueue? {
    if (songs.isEmpty()) return null
    val items = songs.map { s ->
        val meta = MusetagStore.toMediaMetadata(s)
        meta.id.toString() to meta.toMediaItem()
    }
    return ListQueue(
        id = "musetag_list",
        title = title,
        items = items,
        startIndex = startIndex.coerceIn(0, items.lastIndex),
    )
}

internal fun musetagQueueOf(
    songs: List<com.ljyh.mei.musetag.MusetagSong>,
    startIndex: Int,
    title: String?,
): ListQueue? = buildMusetagQueue(songs, startIndex, title)
