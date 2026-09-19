package com.ljyh.mei.ui.screen.musetag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil3.compose.AsyncImage
import com.ljyh.mei.R
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
import com.ljyh.mei.utils.DateUtils.getGreeting
import com.ljyh.mei.utils.largeImage
import com.ljyh.mei.utils.smallImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 首页：个人喜爱 + 曲库入口；全部歌曲在音乐库 */
@Composable
fun MusetagHomeScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val playerConnection = LocalPlayerConnection.current
    val username = MusetagClient.currentUsername()

    var loading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // 1) 磁盘缓存先上，不转圈
        com.ljyh.mei.musetag.MusetagBootstrap.awaitCache()
        refreshTick++
        loading = MusetagStore.library.songs.isEmpty()
        // 2) 静默拉一次更新，失败不打断 UI
        com.ljyh.mei.musetag.MusetagBootstrap.silentRefreshOnce(context)
        loading = false
        refreshTick++
    }

    val likedSongs = remember(refreshTick) { MusetagStore.likedSongs() }
    val likedAlbums = remember(refreshTick) { MusetagStore.likedAlbums() }
    val likedArtists = remember(refreshTick) { MusetagStore.likedArtists() }
    val recentAlbums = remember(refreshTick) { MusetagStore.recentAlbums(24) }
    val allSongCount = remember(refreshTick) { MusetagStore.library.songs.size }

    fun playSongs(list: List<MusetagSong>, startIndex: Int = 0) {
        if (list.isEmpty() || playerConnection == null) return
        val from = (startIndex - 2).coerceAtLeast(0)
        val slice = list.drop(from).take(100)
        if (slice.isEmpty()) return
        val idx = (startIndex - from).coerceIn(0, slice.lastIndex)
        scope.launch(Dispatchers.Default) {
            val queue = slice.map { it.id to MusetagStore.toMediaMetadata(it).toMediaItem() }
            withContext(Dispatchers.Main) {
                playerConnection.playQueue(
                    ListQueue(id = "musetag-home", title = null, items = queue, startIndex = idx)
                )
            }
        }
    }

    var showAllLiked by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding()),
        contentPadding = PaddingValues(
            bottom = insets.calculateBottomPadding() + 100.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item("header") {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    text = getGreeting(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = username.ifBlank { stringResource(R.string.musetag_library) },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = { navController.navigate(Screen.Library.route) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("${stringResource(R.string.musetag_library)} · $allSongCount")
                }
            }
        }

        if (loading) {
            item("loading") {
                BoxCenterProgress()
            }
        }

        if (likedSongs.isNotEmpty() || MusetagStore.playlists().isNotEmpty()) {
            item("playlists") {
                MusetagPlaylistSection(
                    onOpen = { id ->
                        Screen.PlayList.navigate(navController) { addPath(encodeMusetagId(id)) }
                    },
                    onOpenAll = {
                        Screen.PlayList.navigate(navController) { addPath("__all__") }
                    },
                )
            }
        }

        if (likedSongs.isNotEmpty()) {
            item("liked_songs_title") {
                SectionTitle(
                    title = stringResource(R.string.musetag_liked_songs),
                    action = {
                        TextButton(onClick = { showAllLiked = !showAllLiked }) {
                            Text(
                                if (showAllLiked) stringResource(R.string.musetag_collapse)
                                else stringResource(R.string.musetag_show_all)
                            )
                        }
                    },
                )
            }
            if (showAllLiked) {
                items(likedSongs, key = { "liked-all-${it.id}" }) { song ->
                    DetailSongRow(
                        song = song,
                        onClick = {
                            playSongs(likedSongs, likedSongs.indexOfFirst { it.id == song.id })
                        },
                    )
                }
            } else {
                item("liked_songs") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(likedSongs.take(20), key = { it.id }) { song ->
                            SongCard(
                                song = song,
                                onClick = {
                                    playSongs(likedSongs, likedSongs.indexOfFirst { it.id == song.id })
                                },
                            )
                        }
                    }
                }
            }
        }

        if (likedAlbums.isNotEmpty()) {
            item("liked_albums_title") {
                SectionTitle(title = stringResource(R.string.musetag_liked_albums))
            }
            item("liked_albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(likedAlbums, key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = {
                            Screen.Album.navigate(navController) {
                                addPath(encodeMusetagId(album.id))
                            }
                        })
                    }
                }
            }
        }

        if (likedArtists.isNotEmpty()) {
            item("liked_artists_title") {
                SectionTitle(title = stringResource(R.string.musetag_liked_artists))
            }
            item("liked_artists") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(likedArtists, key = { it.id }) { artist ->
                        ArtistCard(artist = artist, onClick = {
                            Screen.Artist.navigate(navController) {
                                addPath(encodeMusetagId(artist.id))
                            }
                        })
                    }
                }
            }
        }

        if (recentAlbums.isNotEmpty()) {
            item("recent_title") {
                SectionTitle(title = stringResource(R.string.musetag_recent_albums))
            }
            item("recent") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recentAlbums, key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = {
                            Screen.Album.navigate(navController) {
                                addPath(encodeMusetagId(album.id))
                            }
                        })
                    }
                }
            }
        }

        if (!loading && likedSongs.isEmpty() && recentAlbums.isEmpty()) {
            item("empty") {
                Text(
                    text = stringResource(R.string.musetag_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxCenterProgress() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SectionTitle(
    title: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
private fun SongCard(song: MusetagSong, onClick: () -> Unit) {
    val cover = remember(song.id) { MusetagStore.albumCover(song).largeImage() }
    val subtitle = remember(song.id) { MusetagStore.artistName(song) }
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = song.title ?: "",
            style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun AlbumCard(album: MusetagAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = MusetagStore.absoluteCover(album.coverUrl)?.largeImage(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.title ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = (album.year ?: "").toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArtistCard(artist: MusetagArtist, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = MusetagStore.absoluteCover(artist.avatarUrl)?.largeImage(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(44.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist.name ?: "",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
