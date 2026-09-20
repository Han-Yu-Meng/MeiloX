package com.ljyh.mei.ui.screen.musetag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ljyh.mei.R
import com.ljyh.mei.constants.PlaylistCardSize
import com.ljyh.mei.constants.PlaylistCardSizeTablet
import com.ljyh.mei.musetag.MusetagBootstrap
import com.ljyh.mei.musetag.MusetagStore
import com.ljyh.mei.ui.component.home.RecommendCard
import com.ljyh.mei.ui.component.home.PlaylistCard
import com.ljyh.mei.ui.component.home.CardExtInfo
import com.ljyh.mei.ui.component.utils.rememberDeviceInfo
import com.ljyh.mei.ui.glass.IosPinnedPage
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.main.home.HomeViewModel
import com.ljyh.mei.utils.DateUtils.getGreeting
import com.ljyh.mei.utils.largeImage
import com.ljyh.mei.utils.middleImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage

/** 首页：问候 + 横向分区（喜爱歌曲/歌单/最近专辑/喜爱专辑/艺术家） */
@Composable
fun MusetagHomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val device = rememberDeviceInfo()
    val cardSize = if (device.isTablet) PlaylistCardSizeTablet else PlaylistCardSize
    val glassColors = LocalGlassColors.current
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        MusetagBootstrap.awaitCache()
        MusetagBootstrap.silentRefreshOnce(context)
        tick++
    }

    val likedSongs = remember(tick) { MusetagStore.likedSongs() }
    val playlists = remember(tick) { MusetagStore.playlists() }
    val recentAlbums = remember(tick) { MusetagStore.recentAlbums(20) }
    val likedAlbums = remember(tick) { MusetagStore.likedAlbums() }
    val likedArtists = remember(tick) { MusetagStore.likedArtists() }
    val topArtists = remember(tick) { MusetagStore.libraryArtistsSorted().take(20) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding()),
        contentPadding = PaddingValues(
            bottom = insets.calculateBottomPadding() + 100.dp,
        ),
    ) {
            item("greeting") {
                Text(
                    text = getGreeting(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = glassColors.content,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            // 歌单区：第一个固定为「喜爱的歌曲」
            if (likedSongs.isNotEmpty() || playlists.isNotEmpty()) {
                item("playlists") {
                    HomeTitle(stringResource(R.string.musetag_playlists))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (likedSongs.isNotEmpty()) {
                            item(key = "liked-as-pl") {
                                val cover = remember(likedSongs, tick) {
                                    likedSongs.firstOrNull()?.let { MusetagStore.albumCover(it) }.orEmpty()
                                }
                                PlaylistCard(
                                    id = "liked_songs",
                                    title = stringResource(R.string.musetag_liked_songs),
                                    coverImg = cover,
                                    subTitle = listOf("${likedSongs.size} ${stringResource(R.string.musetag_song_unit)}"),
                                    showPlay = false,
                                    cardSize = cardSize,
                                    onClick = {
                                        openMusetagSongList(
                                            navController = navController,
                                            title = stringResource(R.string.musetag_liked_songs),
                                            songs = likedSongs,
                                        )
                                    },
                                )
                            }
                        }
                        items(playlists, key = { it.id }) { pl ->
                            val songs = MusetagStore.playlistSongs(pl.id)
                            val cover = remember(pl.id, tick) {
                                pl.coverUrl?.let { MusetagStore.absoluteCover(it) }.orEmpty()
                                    .ifBlank { songs.firstOrNull()?.let { MusetagStore.albumCover(it) }.orEmpty() }
                            }
                            PlaylistCard(
                                id = pl.id,
                                title = pl.title.orEmpty(),
                                coverImg = cover,
                                subTitle = listOf("${songs.size} ${stringResource(R.string.musetag_song_unit)}"),
                                showPlay = false,
                                cardSize = cardSize,
                                onClick = {
                                    openMusetagSongList(
                                        navController = navController,
                                        title = pl.title.orEmpty(),
                                        songs = songs,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // 最近专辑
            if (recentAlbums.isNotEmpty()) {
                item("recent_albums") {
                    HomeTitle(stringResource(R.string.musetag_recent_albums))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recentAlbums, key = { "ra-${it.id}" }) { album ->
                            PlaylistCard(
                                id = album.id,
                                title = album.title.orEmpty(),
                                coverImg = MusetagStore.absoluteCover(album.coverUrl).orEmpty(),
                                subTitle = listOf("${MusetagStore.songsOfAlbum(album.id).size} ${stringResource(R.string.musetag_song_unit)}"),
                                cardSize = cardSize,
                                onClick = {
                                    Screen.Album.navigate(navController) {
                                        addPath(encodeMusetagId(album.id))
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // 喜爱的专辑
            if (likedAlbums.isNotEmpty()) {
                item("liked_albums") {
                    HomeTitle(stringResource(R.string.musetag_liked_albums))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(likedAlbums, key = { "la-${it.id}" }) { album ->
                            PlaylistCard(
                                id = album.id,
                                title = album.title.orEmpty(),
                                coverImg = MusetagStore.absoluteCover(album.coverUrl).orEmpty(),
                                subTitle = listOf("${MusetagStore.songsOfAlbum(album.id).size} ${stringResource(R.string.musetag_song_unit)}"),
                                cardSize = cardSize,
                                onClick = {
                                    Screen.Album.navigate(navController) {
                                        addPath(encodeMusetagId(album.id))
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // 艺术家：圆形头像，不显示歌曲数
            val artistRow = likedArtists.ifEmpty { topArtists }
            if (artistRow.isNotEmpty()) {
                item("artists") {
                    HomeTitle(
                        if (likedArtists.isNotEmpty()) stringResource(R.string.musetag_liked_artists)
                        else stringResource(R.string.musetag_artists)
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(artistRow, key = { it.id }) { artist ->
                            val avatar = MusetagStore.absoluteCover(artist.avatarUrl).orEmpty()
                            Column(
                                modifier = Modifier
                                    .width(88.dp)
                                    .clickable {
                                        Screen.Artist.navigate(navController) {
                                            addPath(encodeMusetagId(artist.id))
                                        }
                                    },
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            ) {
                                AsyncImage(
                                    model = avatar.largeImage(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = artist.name.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun HomeTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun HorizontalCoverCard(
    cover: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = cover.largeImage(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
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
