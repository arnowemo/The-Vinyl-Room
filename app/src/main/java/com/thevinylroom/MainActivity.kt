package com.thevinylroom

import android.os.Bundle
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import org.xmlpull.v1.XmlPullParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val amber = Color(0xFFFFB66D)
private val cream = Color(0xFFFFE8CE)
private val muted = Color(0xFFC5A58B)
private val roomDark = Color(0xFF160B07)

private data class Album(val artist: String, val title: String, val colors: List<Color>, val mark: String)
private data class LocalTrack(val uri: String, val name: String, val artist: String = "", val album: String = "", val durationSeconds: Int = 0)
private data class BandcampAlbum(val id: String, val title: String, val artist: String, val coverArt: String, val songCount: Int)
private data class BandcampSong(val id: String, val title: String, val artist: String, val album: String, val coverArt: String, val duration: Int)
private val albums = listOf(
    Album("Explosions in the Sky", "The Earth Is Not a Cold Dead Place", listOf(Color(0xFF152A42), Color(0xFFDB8A56), Color(0xFF080D16)), "✳"),
    Album("Alcest", "Écailles de lune", listOf(Color(0xFF101B31), Color(0xFFB8C5D5), Color(0xFF283C58)), "☾"),
    Album("Amenra", "Mass VI", listOf(Color(0xFF1B1A19), Color(0xFF77716A), Color(0xFF090909)), "M"),
    Album("Agalloch", "The Mantle", listOf(Color(0xFF171B19), Color(0xFF8C8978), Color(0xFF202923)), "♠"),
    Album("Sigur Rós", "Ágætis byrjun", listOf(Color(0xFF7D3434), Color(0xFFD5A1A0), Color(0xFF241D25)), "✺"),
    Album("Godspeed You! Black Emperor", "Lift Your Skinny Fists", listOf(Color(0xFF6D593B), Color(0xFFB8A17B), Color(0xFF17130F)), "✧")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(18, 9, 5)
        window.navigationBarColor = android.graphics.Color.rgb(12, 7, 5)
        setContent { VinylRoomApp() }
    }
}

@Composable
private fun VinylRoomApp() {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vinyl_room", Context.MODE_PRIVATE) }
    var likedAlbums by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var activeTab by remember { mutableStateOf("Albums") }
    var activeNav by remember { mutableStateOf("Ma bibliothèque") }
    var showAlbumDetails by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var immersive by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showBandcamp by remember { mutableStateOf(false) }
    var bandcampAlbums by remember { mutableStateOf<List<BandcampAlbum>>(emptyList()) }
    var bandcampArtwork by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var playbackError by remember { mutableStateOf("") }
    var bandcampStatus by remember { mutableStateOf("Non connecté") }
    var bandcampBusy by remember { mutableStateOf(false) }
    var selectedBandcampAlbum by remember { mutableStateOf<BandcampAlbum?>(null) }
    var bandcampSongs by remember { mutableStateOf<List<BandcampSong>>(emptyList()) }
    var bandcampDetailBusy by remember { mutableStateOf(false) }
    var bandcampDetailError by remember { mutableStateOf("") }
    var bandcampUsername by remember { mutableStateOf("") }
    var bandcampPassword by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        bandcampUsername = prefs.getString("bc_user", "") ?: ""
        bandcampPassword = decryptSecret(context, prefs.getString("bc_pass", null))
    }
    var localTracks by remember {
        mutableStateOf<List<LocalTrack>>(prefs.getStringSet("local_tracks", emptySet<String>())?.map { raw ->
            val parts = raw.split("|", limit = 2)
            LocalTrack(parts[0], parts.getOrElse(1) { "Titre audio" })
        } ?: emptyList<LocalTrack>())
    }
    var currentTrackIndex by remember { mutableIntStateOf(-1) }
    var currentTrackName by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playerPrepared by remember { mutableStateOf(false) }
    var trackDuration by remember { mutableLongStateOf(0L) }
    var trackPosition by remember { mutableLongStateOf(0L) }
    var trackArtist by remember { mutableStateOf("") }
    var trackAlbum by remember { mutableStateOf("") }
    var embeddedArtwork by remember { mutableStateOf<ImageBitmap?>(null) }
    DisposableEffect(player) {
        onDispose { runCatching { player?.release() } }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val added = uris.map { uri ->
                runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                } ?: "Titre audio"
                LocalTrack(uri.toString(), name)
            }
            localTracks = (localTracks + added).distinctBy { it.uri }
            prefs.edit().putStringSet("local_tracks", localTracks.map { it.uri + "|" + it.name }.toSet()).apply()
            showQueue = true
        }
    }
    val libraryAlbums = if (bandcampAlbums.isNotEmpty()) bandcampAlbums.map { item ->
        Album(item.artist, item.title, listOf(Color(0xFF34251E), Color(0xFFB27A4E), Color(0xFF130D0A)), "♫")
    } else albums
    val safeSelected = selected.coerceIn(0, libraryAlbums.lastIndex)
    val album = libraryAlbums[safeSelected]
    val activeBandcampAlbum = bandcampAlbums.getOrNull(safeSelected)

    fun startLocalTrack(index: Int) {
        if (index !in localTracks.indices) return
        runCatching { player?.release() }
        player = null
        playerPrepared = false
        currentTrackIndex = index
        currentTrackName = localTracks[index].name
        trackPosition = 0L
        trackDuration = localTracks[index].durationSeconds * 1000L
        trackArtist = localTracks[index].artist
        trackAlbum = localTracks[index].album
        embeddedArtwork = null
        playbackError = ""
        playing = false
        runCatching {
            val next = ExoPlayer.Builder(context).build()
            player = next
            next.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            playerPrepared = false
                            currentTrackName = localTracks.getOrNull(index)?.name ?: "Chargement…"
                        }
                        Player.STATE_READY -> {
                            playerPrepared = true
                            if (next.duration > 0L) trackDuration = next.duration
                            playing = next.isPlaying
                        }
                        Player.STATE_ENDED -> {
                            playing = false
                            trackPosition = trackDuration
                            if (currentTrackIndex < localTracks.lastIndex) startLocalTrack(currentTrackIndex + 1)
                        }
                        Player.STATE_IDLE -> playerPrepared = false
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                    if (isPlaying) playbackError = ""
                }
                override fun onPlayerError(error: PlaybackException) {
                    playing = false
                    playerPrepared = false
                    playbackError = "Erreur audio : ${error.errorCodeName} · ${error.message ?: "flux indisponible"}"
                }
            })
            next.setMediaItem(MediaItem.fromUri(localTracks[index].uri))
            next.prepare()
            next.playWhenReady = true
        }.onFailure {
            playing = false
            playbackError = it.message ?: "Impossible de démarrer le lecteur audio."
        }
    }
    fun playBandcampSong(song: BandcampSong) {
        playbackError = ""
        val streamUrl = bandcampStreamUrl(bandcampUsername, bandcampPassword, song.id)
        val track = LocalTrack(streamUrl, song.title, song.artist, song.album, song.duration)
        localTracks = localTracks.filterNot { it.uri == streamUrl } + track
        startLocalTrack(localTracks.lastIndex)
    }

    LaunchedEffect(player, playing, playerPrepared) {
        while (player != null && playerPrepared && playing) {
            runCatching { trackPosition = player?.currentPosition ?: trackPosition }
            delay(500)
        }
    }
    val liked = album.artist in likedAlbums

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(roomDark)) {
            RoomBackdrop(Modifier.fillMaxSize())
            AmbientDust(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color(0x33090000), .45f to Color(0x18090000), .72f to Color(0x44090000), 1f to Color(0xCC100804)
            )))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Menu, null, tint = cream, modifier = Modifier.size(25.dp))
                        Spacer(Modifier.width(14.dp))
                        AnimatedEqualizer(playing = playing, modifier = Modifier.size(width = 28.dp, height = 25.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("THE VINYL ROOM", color = cream, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.3.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LibraryAdd, null, tint = cream, modifier = Modifier.size(24.dp).clickable { audioPicker.launch(arrayOf("audio/*")) })
                        Spacer(Modifier.width(14.dp))
                        Icon(Icons.Default.Cloud, null, tint = cream, modifier = Modifier.size(25.dp).clickable { showBandcamp = true })
                        Spacer(Modifier.width(17.dp))
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFAA795E), Color(0xFF171016)))).border(1.dp, amber, CircleShape), contentAlignment = Alignment.Center) {
                            Text("●", color = cream, fontSize = 10.sp)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(28.dp)).background(Color(0x77170C08))
                        .border(1.dp, amber.copy(alpha = .22f), RoundedCornerShape(28.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Albums", "Artistes", "Playlists", "Titres").forEach { tab ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                                .background(if (activeTab == tab) Brush.verticalGradient(listOf(Color(0xFFFFC27F), Color(0xFF8B4A20))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                                .clickable { activeTab = tab }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tab, color = if (activeTab == tab) Color(0xFF2B160C) else cream.copy(alpha = .8f), fontSize = 12.sp, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ma bibliothèque", color = cream, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (bandcampAlbums.isEmpty()) "Collection locale   ·   " else "Bandcamp · ${bandcampAlbums.size} albums   ·   ", color = muted, fontSize = 11.sp)
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (bandcampAlbums.isNotEmpty()) Color(0xFF6FCF97) else muted))
                        Text(if (bandcampAlbums.isNotEmpty()) "  Connecté" else "  Bandcamp à connecter", color = cream.copy(alpha = .82f), fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.weight(.20f))
                BoxWithConstraints(
                    Modifier.fillMaxWidth().weight(3.1f).pointerInput(selected) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, drag -> total += drag },
                            onDragEnd = {
                                if (abs(total) > 35f) selected = if (total < 0) (safeSelected + 1) % libraryAlbums.size else (safeSelected - 1 + libraryAlbums.size) % libraryAlbums.size
                                total = 0f
                            }
                        )
                    },
                    contentAlignment = Alignment.Center
                ) {
                    val sideW = (maxWidth * .19f).coerceAtLeast(54.dp)
                    val centerW = (maxWidth * .64f).coerceAtMost(290.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        val left = (safeSelected - 1 + libraryAlbums.size) % libraryAlbums.size
                        val right = (safeSelected + 1) % libraryAlbums.size
                        AlbumSleeve(libraryAlbums[left], false, Modifier.width(sideW).heightIn(min = 130.dp, max = 185.dp).graphicsLayer { rotationY = 22f; alpha = .88f } .clickable { selected = left })
                        Spacer(Modifier.width(5.dp))
                        AlbumSleeve(
                            album,
                            true,
                            Modifier.width(centerW).aspectRatio(1f).shadow(22.dp, RoundedCornerShape(5.dp)).clickable {
                                if (activeBandcampAlbum != null) {
                                    selectedBandcampAlbum = activeBandcampAlbum
                                    showBandcamp = true
                                    bandcampDetailBusy = true
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            bandcampFetchAlbumSongs(bandcampUsername, bandcampPassword, activeBandcampAlbum.id)
                                        }
                                        bandcampDetailBusy = false
                                        if (result.isSuccess) bandcampSongs = result.getOrThrow()
                                        else bandcampDetailError = result.exceptionOrNull()?.message ?: "Impossible de charger cet album."
                                    }
                                } else showAlbumDetails = true
                            },
                            bandcampArtwork[activeBandcampAlbum?.id]
                        )
                        Spacer(Modifier.width(5.dp))
                        AlbumSleeve(libraryAlbums[right], false, Modifier.width(sideW).heightIn(min = 130.dp, max = 185.dp).graphicsLayer { rotationY = -22f; alpha = .88f }.clickable { selected = right }, bandcampArtwork[bandcampAlbums.getOrNull(right)?.id])
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { selected = (safeSelected - 1 + libraryAlbums.size) % libraryAlbums.size }) { Icon(Icons.Default.ChevronLeft, null, tint = amber, modifier = Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (currentTrackIndex >= 0) currentTrackName else album.artist, color = cream, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                        if (playbackError.isNotBlank()) Text(playbackError, color = Color(0xFFFF9C85), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
                        Text(if (currentTrackIndex >= 0) listOf(trackArtist, trackAlbum).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Fichier audio local" } else album.title, color = muted, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                    IconButton(onClick = { selected = (safeSelected + 1) % libraryAlbums.size }) { Icon(Icons.Default.ChevronRight, null, tint = amber, modifier = Modifier.size(30.dp)) }
                }

                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = {
                        likedAlbums = if (liked) likedAlbums - album.artist else likedAlbums + album.artist
                        prefs.edit().putStringSet("favorites", likedAlbums).apply()
                    }) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (liked) amber else cream) }
                    IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.AddBox, null, tint = cream) }
                    IconButton(onClick = { immersive = true }) { Icon(Icons.Default.OpenInFull, null, tint = cream) }
                }

                Spacer(Modifier.weight(.12f))
                Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (currentTrackIndex >= 0) formatTime(trackPosition) else "—:—", color = cream, fontSize = 11.sp)
                        Text(if (currentTrackIndex >= 0) formatTime(trackDuration) else "—:—", color = cream, fontSize = 11.sp)
                    }
                    Slider(
                        value = if (trackDuration > 0L) trackPosition.coerceIn(0L, trackDuration).toFloat() else 0f,
                        onValueChange = { value ->
                            trackPosition = value.toLong()
                            if (playerPrepared) runCatching { player?.seekTo(value.toLong()) }
                        },
                        valueRange = 0f..trackDuration.coerceAtLeast(1L).toFloat(),
                        enabled = currentTrackIndex >= 0 && playerPrepared && trackDuration > 0L,
                        colors = SliderDefaults.colors(
                            thumbColor = amber,
                            activeTrackColor = amber,
                            inactiveTrackColor = Color(0xFF73513B)
                        ),
                        modifier = Modifier.fillMaxWidth().height(22.dp)
                    )
                    Spacer(Modifier.height(13.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shuffle, null, tint = muted)
                        Icon(Icons.Default.SkipPrevious, null, tint = cream, modifier = Modifier.size(29.dp).clickable { if (localTracks.isNotEmpty()) startLocalTrack((currentTrackIndex - 1).let { if (it < 0) localTracks.lastIndex else it }) })
                        val pulse = rememberInfiniteTransition(label = "playGlow")
                        val glow by pulse.animateFloat(
                            initialValue = 0.82f, targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "playPulse"
                        )
                        Box(
                            Modifier.size(68.dp).graphicsLayer { scaleX = if (playing) glow else 1f; scaleY = if (playing) glow else 1f }
                                .shadow(if (playing) 24.dp else 8.dp, CircleShape)
                                .clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFFFD49D), amber)))
                                .clickable {
                                    if (player != null && playerPrepared) {
                                        if (player?.isPlaying == true) { player?.pause(); playing = false }
                                        else { player?.play(); playing = true }
                                    } else if (currentTrackIndex >= 0 && localTracks.isNotEmpty()) {
                                        startLocalTrack(currentTrackIndex)
                                    } else if (activeBandcampAlbum != null) {
                                        bandcampDetailBusy = true
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) { bandcampFetchAlbumSongs(bandcampUsername, bandcampPassword, activeBandcampAlbum.id) }
                                            bandcampDetailBusy = false
                                            if (result.isSuccess) {
                                                val firstSong = result.getOrThrow().firstOrNull()
                                                if (firstSong != null) {
                                                    playBandcampSong(firstSong)
                                                } else bandcampDetailError = "Aucun morceau disponible dans cet album."
                                            } else bandcampDetailError = result.exceptionOrNull()?.message ?: "Impossible de lire cet album."
                                        }
                                    } else if (player == null && localTracks.isNotEmpty()) startLocalTrack(0)
                                    else if (player == null) showBandcamp = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color(0xFF21130D), modifier = Modifier.size(35.dp))
                        }
                        Icon(Icons.Default.SkipNext, null, tint = cream, modifier = Modifier.size(29.dp).clickable { if (localTracks.isNotEmpty()) startLocalTrack((currentTrackIndex + 1).let { if (it > localTracks.lastIndex) 0 else it }) })
                        Icon(Icons.Default.Repeat, null, tint = muted)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(Color(0xCC130A06)).border(1.dp, amber.copy(alpha = .16f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(Icons.Default.Home to "Accueil", Icons.Default.LibraryMusic to "Ma bibliothèque", Icons.Default.Search to "Rechercher", Icons.Default.FavoriteBorder to "Favoris", Icons.Default.Tune to "Paramètres").forEach { (icon, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            activeNav = label
                            if (label == "Rechercher") showSearch = true
                            if (label == "Favoris") {
                                val firstFavorite = libraryAlbums.indexOfFirst { it.artist in likedAlbums }
                                if (firstFavorite >= 0) selected = firstFavorite
                            }
                        }) {
                            Icon(icon, null, tint = if (activeNav == label) amber else muted, modifier = Modifier.size(22.dp))
                            Text(label, color = if (activeNav == label) cream else muted, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
            if (showAlbumDetails) {
                AlbumDetailsDialog(album = album, onDismiss = { showAlbumDetails = false })
            }
            if (showBandcamp) {
                BandcampDialog(
                    username = bandcampUsername,
                    password = bandcampPassword,
                    albums = bandcampAlbums,
                    status = bandcampStatus,
                    busy = bandcampBusy,
                    selectedAlbum = selectedBandcampAlbum,
                    songs = bandcampSongs,
                    detailBusy = bandcampDetailBusy,
                    detailError = bandcampDetailError,
                    onSelectAlbum = { item ->
                        selectedBandcampAlbum = item
                        bandcampSongs = emptyList()
                        bandcampDetailError = ""
                        bandcampDetailBusy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                bandcampFetchAlbumSongs(bandcampUsername, bandcampPassword, item.id)
                            }
                            bandcampDetailBusy = false
                            if (result.isSuccess) bandcampSongs = result.getOrThrow()
                            else bandcampDetailError = result.exceptionOrNull()?.message ?: "Impossible de charger cet album."
                        }
                    },
                    onPlaySong = { song ->
                        playBandcampSong(song)
                        showBandcamp = false
                    },
                    onBackToAlbums = { selectedBandcampAlbum = null; bandcampSongs = emptyList() },
                    onUsernameChange = { bandcampUsername = it },
                    onPasswordChange = { bandcampPassword = it },
                    onConnect = {
                        bandcampBusy = true
                        bandcampStatus = "Connexion à Bandcamp…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { bandcampFetchAlbums(bandcampUsername.trim(), bandcampPassword) }
                            bandcampBusy = false
                            if (result.isSuccess) {
                                bandcampAlbums = result.getOrThrow()
                                selected = 0
                                bandcampStatus = "Connexion réussie · ${bandcampAlbums.size} albums récupérés · chargement des pochettes…"
                                val covers = withContext(Dispatchers.IO) {
                                    bandcampAlbums.mapNotNull { item ->
                                        if (item.coverArt.isBlank()) null else bandcampFetchCoverArt(bandcampUsername.trim(), bandcampPassword, item.coverArt)?.let { item.id to it }
                                    }.toMap()
                                }
                                bandcampArtwork = covers
                                bandcampStatus = "Connexion réussie · ${bandcampAlbums.size} albums · ${covers.size} pochettes"
                                prefs.edit().putString("bc_user", bandcampUsername.trim()).putString("bc_pass", encryptSecret(context, bandcampPassword)).apply()
                            } else {
                                bandcampStatus = result.exceptionOrNull()?.message ?: "Connexion impossible"
                            }
                        }
                    },
                    onDismiss = { showBandcamp = false }
                )
            }
            if (showQueue) {
                LocalLibraryDialog(
                    tracks = localTracks,
                    currentIndex = currentTrackIndex,
                    onAdd = { audioPicker.launch(arrayOf("audio/*")) },
                    onPlay = { index -> startLocalTrack(index); showQueue = false },
                    onRemove = { index ->
                        localTracks = localTracks.filterIndexed { i, _ -> i != index }
                        prefs.edit().putStringSet("local_tracks", localTracks.map { it.uri + "|" + it.name }.toSet()).apply()
                        if (currentTrackIndex == index) { player?.release(); player = null; currentTrackIndex = -1; playing = false }
                    },
                    onDismiss = { showQueue = false }
                )
            }
            if (showSearch) {
                SearchDialog(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSelect = { index -> selected = index; showSearch = false; activeNav = "Ma bibliothèque" },
                    onDismiss = { showSearch = false }
                )
            }
            if (immersive) {
                ImmersiveDialog(album = album, artwork = embeddedArtwork, playing = playing, onTogglePlay = {
                    if (player?.isPlaying == true) { player?.pause(); playing = false }
                    else if (player != null && playerPrepared) { player?.play(); playing = true }
                    else if (player == null && localTracks.isNotEmpty()) startLocalTrack(0)
                    else if (player == null) showQueue = true
                }, onDismiss = { immersive = false })
            }
        }
    }
}

private fun encryptSecret(context: Context, secret: String): String {
    if (secret.isBlank()) return ""
    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val key = (keyStore.getKey("vinyl_room_bandcamp", null) as? SecretKey) ?: run {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("vinyl_room_bandcamp", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        generator.generateKey()
    }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
}

private fun decryptSecret(context: Context, value: String?): String {
    if (value.isNullOrBlank()) return ""
    return runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val key = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey("vinyl_room_bandcamp", null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrDefault("")
}

private fun bandcampFetchAlbums(username: String, password: String): Result<List<BandcampAlbum>> = runCatching {
    require(username.isNotBlank() && password.isNotBlank()) { "Renseigne ton identifiant et ton mot de passe Subsonic." }

    fun request(method: String, extra: Map<String, String> = emptyMap()): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val token = MessageDigest.getInstance("MD5")
            .digest((password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val builder = Uri.parse("https://bandcamp.com/api/subsonic/rest/$method.view").buildUpon()
            .appendQueryParameter("u", username)
            .appendQueryParameter("t", token)
            .appendQueryParameter("s", salt)
            .appendQueryParameter("v", "1.16.1")
            .appendQueryParameter("c", "TheVinylRoom")
            .appendQueryParameter("f", "xml")
        extra.forEach { (key, value) -> builder.appendQueryParameter(key, value) }
        val connection = (URL(builder.build().toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/xml, text/xml")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = body.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(260)
                throw IllegalStateException("Bandcamp HTTP $code" + if (detail.isNotBlank()) " · $detail" else " · Réponse serveur vide.")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    fun parseResponse(xml: String, onAlbum: ((XmlPullParser) -> Unit)? = null): String {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        var responseStatus = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "subsonic-response" -> responseStatus = parser.getAttributeValue(null, "status") ?: ""
                    "error" -> throw IllegalStateException(parser.getAttributeValue(null, "message") ?: "Erreur renvoyée par Bandcamp.")
                    "album" -> onAlbum?.invoke(parser)
                }
            }
            event = parser.next()
        }
        if (responseStatus != "ok") throw IllegalStateException("Réponse Subsonic inattendue (statut : ${responseStatus.ifBlank { "absent" }}).")
        return responseStatus
    }

    // Vérifie d'abord les identifiants et la disponibilité de l'API avant de demander la collection.
    parseResponse(request("ping"))
    val found = mutableListOf<BandcampAlbum>()
    val response = request("getAlbumList2", mapOf("type" to "newest", "size" to "500", "offset" to "0"))
    parseResponse(response) { parser ->
        val id = parser.getAttributeValue(null, "id") ?: ""
        val title = parser.getAttributeValue(null, "name")
            ?: parser.getAttributeValue(null, "album") ?: "Album sans titre"
        val artist = parser.getAttributeValue(null, "artist") ?: "Artiste inconnu"
        val art = parser.getAttributeValue(null, "coverArt") ?: ""
        val count = parser.getAttributeValue(null, "songCount")?.toIntOrNull() ?: 0
        if (id.isNotBlank()) found.add(BandcampAlbum(id, title, artist, art, count))
    }
    found
}

@Composable
private fun BandcampDialog(
    username: String,
    password: String,
    albums: List<BandcampAlbum>,
    status: String,
    busy: Boolean,
    selectedAlbum: BandcampAlbum?,
    songs: List<BandcampSong>,
    detailBusy: Boolean,
    detailError: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onSelectAlbum: (BandcampAlbum) -> Unit,
    onPlaySong: (BandcampSong) -> Unit,
    onBackToAlbums: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF21130D),
        titleContentColor = cream,
        textContentColor = muted,
        title = {
            Column {
                Text("Bandcamp", fontWeight = FontWeight.Bold)
                if (selectedAlbum != null) Text(selectedAlbum.artist, color = amber, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (selectedAlbum == null) {
                    Text("API Subsonic · bandcamp.com", color = amber, fontSize = 12.sp)
                    OutlinedTextField(value = username, onValueChange = onUsernameChange, singleLine = true, label = { Text("Identifiant Subsonic") })
                    OutlinedTextField(value = password, onValueChange = onPasswordChange, singleLine = true, label = { Text("Mot de passe Subsonic") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Text(status, color = if (status.startsWith("Connexion réussie")) Color(0xFF8BD5A7) else muted, fontSize = 11.sp)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = amber)
                    if (albums.isNotEmpty()) {
                        Text("Ta collection · ${albums.size} albums", color = cream, fontWeight = FontWeight.SemiBold)
                        albums.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectAlbum(item) }.padding(vertical = 7.dp, horizontal = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Album, null, tint = amber, modifier = Modifier.size(23.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, color = cream, fontSize = 13.sp, maxLines = 1)
                                    Text("${item.artist} · ${item.songCount} titres", color = muted, fontSize = 11.sp, maxLines = 1)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = muted)
                            }
                        }
                    }
                } else {
                    TextButton(onClick = onBackToAlbums) { Text("‹  Tous les albums", color = amber) }
                    Text(selectedAlbum.title, color = cream, fontWeight = FontWeight.Bold)
                    Text("${selectedAlbum.songCount} titres · ${selectedAlbum.artist}", color = muted, fontSize = 11.sp)
                    if (detailBusy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = amber)
                    if (detailError.isNotBlank()) Text(detailError, color = Color(0xFFFF9C85), fontSize = 12.sp)
                    if (!detailBusy && songs.isEmpty() && detailError.isBlank()) Text("Aucun titre retourné pour cet album.", color = muted, fontSize = 12.sp)
                    songs.forEachIndexed { index, song ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { onPlaySong(song) }.padding(vertical = 8.dp, horizontal = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text((index + 1).toString().padStart(2, '0'), color = amber, modifier = Modifier.width(28.dp), fontSize = 11.sp)
                            Column(Modifier.weight(1f)) {
                                Text(song.title, color = cream, fontSize = 12.sp, maxLines = 2)
                                Text(if (song.duration > 0) formatTime(song.duration * 1000L) else "Bandcamp", color = muted, fontSize = 10.sp)
                            }
                            Icon(Icons.Default.PlayCircle, null, tint = amber, modifier = Modifier.size(23.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedAlbum == null) {
                TextButton(onClick = onConnect, enabled = !busy) { Text(if (busy) "Connexion…" else "Synchroniser", color = amber) }
            }
        },
        dismissButton = {
            Row {
                if (selectedAlbum != null) TextButton(onClick = onBackToAlbums) { Text("Retour", color = muted) }
                TextButton(onClick = onDismiss) { Text("Fermer", color = cream) }
            }
        }
    )
}

private fun bandcampAuthParams(username: String, password: String): Map<String, String> {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
    val token = MessageDigest.getInstance("MD5")
        .digest((password + salt).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return mapOf("u" to username, "t" to token, "s" to salt, "v" to "1.16.1", "c" to "TheVinylRoom", "f" to "xml")
}

private fun bandcampFetchCoverArt(username: String, password: String, coverId: String): ImageBitmap? = runCatching {
    val builder = Uri.parse("https://bandcamp.com/api/subsonic/rest/getCoverArt.view").buildUpon()
    bandcampAuthParams(username, password).forEach { (key, value) -> builder.appendQueryParameter(key, value) }
    builder.appendQueryParameter("id", coverId)
    builder.appendQueryParameter("size", "600")
    val connection = (URL(builder.build().toString()).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 18000
        setRequestProperty("Accept", "image/*")
        setRequestProperty("User-Agent", "TheVinylRoom/1.0")
    }
    try {
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    } finally { connection.disconnect() }
}.getOrNull()

private fun bandcampStreamUrl(username: String, password: String, songId: String): String {
    val builder = Uri.parse("https://bandcamp.com/api/subsonic/rest/stream.view").buildUpon()
    bandcampAuthParams(username, password).forEach { (key, value) -> builder.appendQueryParameter(key, value) }
    builder.appendQueryParameter("id", songId)
    builder.appendQueryParameter("format", "mp3")
    builder.appendQueryParameter("estimateContentLength", "true")
    return builder.build().toString()
}

private fun bandcampProbeStream(streamUrl: String): Result<Unit> = runCatching {
    val connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15000
        readTimeout = 20000
        setRequestProperty("User-Agent", "TheVinylRoom/1.0")
        setRequestProperty("Accept", "audio/*, application/octet-stream, */*")
        setRequestProperty("Range", "bytes=0-1023")
    }
    try {
        val code = connection.responseCode
        val contentType = connection.contentType.orEmpty()
        if (code !in 200..299 || contentType.contains("xml", ignoreCase = true) || contentType.contains("json", ignoreCase = true)) {
            val source = if (code in 200..299) connection.inputStream else connection.errorStream
            val detail = source?.bufferedReader()?.use { it.readText().take(1800) }.orEmpty()
            val message = Regex("message=[\"']([^\"']+)").find(detail)?.groupValues?.get(1)
                ?: detail.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(220)
            throw IllegalStateException("Flux Bandcamp refusé (HTTP $code)" + if (message.isNotBlank()) " : $message" else "")
        }
        if (!(contentType.startsWith("audio/") || contentType.contains("octet-stream") || contentType.contains("mp4"))) {
            throw IllegalStateException("Type de flux inattendu : " + contentType.ifBlank { "inconnu" } + " (HTTP $code)")
        }
        connection.inputStream.use { input -> val buffer = ByteArray(256); input.read(buffer) }
    } finally { connection.disconnect() }
}

private fun bandcampFetchAlbumSongs(username: String, password: String, albumId: String): Result<List<BandcampSong>> = runCatching {
    val builder = Uri.parse("https://bandcamp.com/api/subsonic/rest/getAlbum.view").buildUpon()
    bandcampAuthParams(username, password).forEach { (key, value) -> builder.appendQueryParameter(key, value) }
    builder.appendQueryParameter("id", albumId)
    val connection = (URL(builder.build().toString()).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 20000
        setRequestProperty("Accept", "application/xml, text/xml")
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        val result = mutableListOf<BandcampSong>()
        var status = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "subsonic-response" -> status = parser.getAttributeValue(null, "status") ?: ""
                    "error" -> throw IllegalStateException(parser.getAttributeValue(null, "message") ?: "Erreur Bandcamp.")
                    "song" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        if (id.isNotBlank()) result.add(
                            BandcampSong(
                                id = id,
                                title = parser.getAttributeValue(null, "title") ?: parser.getAttributeValue(null, "name") ?: "Titre inconnu",
                                artist = parser.getAttributeValue(null, "artist") ?: "Artiste inconnu",
                                album = parser.getAttributeValue(null, "album") ?: "",
                                coverArt = parser.getAttributeValue(null, "coverArt") ?: "",
                                duration = parser.getAttributeValue(null, "duration")?.toIntOrNull() ?: 0
                            )
                        )
                    }
                }
            }
            event = parser.next()
        }
        if (code !in 200..299) throw IllegalStateException("Bandcamp HTTP $code")
        if (status != "ok") throw IllegalStateException("Bandcamp n'a pas retourné les titres de cet album.")
        result
    } finally { connection.disconnect() }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

@Composable
private fun LocalLibraryDialog(
    tracks: List<LocalTrack>,
    currentIndex: Int,
    onAdd: () -> Unit,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF21130D),
        titleContentColor = cream,
        textContentColor = muted,
        title = { Text("Ma musique", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Importe les fichiers audio stockés sur ton téléphone. Ils restent sur ton appareil.", fontSize = 12.sp)
                if (tracks.isEmpty()) {
                    Text("Aucun titre importé pour le moment.", color = cream)
                } else {
                    tracks.forEachIndexed { index, track ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onPlay(index) }) {
                                Icon(if (index == currentIndex) Icons.Default.GraphicEq else Icons.Default.PlayArrow, null, tint = amber)
                            }
                            Text(track.name, color = cream, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2)
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(Icons.Default.Close, null, tint = muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onAdd) { Text("Importer", color = amber) }
                TextButton(onClick = onDismiss) { Text("Fermer", color = cream) }
            }
        }
    )
}

@Composable
private fun AlbumDetailsDialog(album: Album, onDismiss: () -> Unit) {
    val tracks = if (album.artist == "Explosions in the Sky") listOf(
        "First Breath After Coma", "The Only Moment We Were Alone",
        "Six Days at the Bottom of the Ocean", "Memorial", "Your Hand in Mine"
    ) else emptyList()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF21130D),
        titleContentColor = cream,
        textContentColor = muted,
        title = { Text(album.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(album.artist, color = amber, fontWeight = FontWeight.SemiBold)
                Text(if (tracks.isNotEmpty()) "Titres de l'album" else "Informations de l'album")
                if (tracks.isNotEmpty()) {
                    tracks.forEachIndexed { index, track ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text((index + 1).toString().padStart(2, '0'), color = amber, modifier = Modifier.width(30.dp))
                            Text(track, color = cream, fontSize = 13.sp)
                        }
                    }
                } else {
                    Text("La liste des titres sera disponible lorsque les données de cet album seront ajoutées.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer", color = amber) } }
    )
}

@Composable
private fun SearchDialog(query: String, onQueryChange: (String) -> Unit, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val results = albums.mapIndexedNotNull { index, album ->
        if (query.isBlank() || album.artist.contains(query, true) || album.title.contains(query, true)) index else null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF21130D),
        titleContentColor = cream,
        title = { Text("Rechercher dans la bibliothèque") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = onQueryChange, singleLine = true, label = { Text("Artiste ou album") })
                results.forEach { index ->
                    TextButton(onClick = { onSelect(index) }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(albums[index].artist, color = cream)
                            Text(albums[index].title, color = muted, fontSize = 12.sp)
                        }
                    }
                }
                if (results.isEmpty()) Text("Aucun résultat", color = muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer", color = amber) } }
    )
}

@Composable
private fun ImmersiveDialog(album: Album, artwork: ImageBitmap?, playing: Boolean, onTogglePlay: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF100804),
        titleContentColor = cream,
        textContentColor = cream,
        title = { Text("Écoute immersive") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (artwork != null) {
                    Image(artwork!!, contentDescription = "Pochette intégrée au fichier audio", modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                } else AlbumSleeve(album, true, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(album.artist, color = cream, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(album.title, color = muted, textAlign = TextAlign.Center)
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(64.dp).background(amber, CircleShape)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = roomDark, modifier = Modifier.size(34.dp))
                }
                Text("Mode écoute immersive", color = muted, fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terminer", color = amber) } }
    )
}

@Composable
private fun AnimatedEqualizer(playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val heights = (0..4).map { i ->
        val animated by transition.animateFloat(
            initialValue = .28f, targetValue = if (playing) .95f else .28f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (playing) 360 + i * 95 else 900, easing = FastOutSlowInEasing, delayMillis = i * 75),
                repeatMode = RepeatMode.Reverse
            ), label = "bar$i"
        )
        if (playing) animated else .22f + i * .035f
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEach { h ->
            Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(3.dp)).background(amber))
        }
    }
}

@Composable
private fun AmbientDust(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ambientDust")
    val drift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "dustDrift"
    )
    val shimmer by transition.animateFloat(
        initialValue = .12f, targetValue = .34f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dustShimmer"
    )
    Canvas(modifier) {
        val particles = listOf(
            .08f to .18f, .19f to .34f, .31f to .12f, .44f to .26f, .58f to .17f,
            .72f to .31f, .86f to .14f, .94f to .42f, .13f to .57f, .37f to .68f,
            .63f to .55f, .81f to .72f, .24f to .83f, .51f to .91f, .91f to .86f
        )
        particles.forEachIndexed { index, point ->
            val x = size.width * point.first + kotlin.math.sin((drift * 6.28f + index).toDouble()).toFloat() * 9.dp.toPx()
            val y = size.height * ((point.second + drift * (.10f + index % 3 * .025f)) % 1f)
            val radius = (1.1f + (index % 3) * .45f).dp.toPx()
            drawCircle(Color(0xFFFFD7A5).copy(alpha = shimmer * if (index % 2 == 0) 1f else .55f), radius, androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@Composable
private fun RoomBackdrop(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.room_background),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun AlbumSleeve(album: Album, active: Boolean, modifier: Modifier = Modifier, artwork: ImageBitmap? = null) {
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(Brush.linearGradient(album.colors))) {
        if (artwork != null) {
            Image(artwork, contentDescription = "Pochette Bandcamp", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (active && album.artist == "Explosions in the Sky") {
            Image(
                painter = painterResource(id = R.drawable.cover_eits),
                contentDescription = "Pochette de The Earth Is Not a Cold Dead Place",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (active) {
            Canvas(Modifier.fillMaxSize()) {
                for(i in 0..35) {
                    val x=size.width*(i%6)/6f
                    val y=size.height*(i/6)/6f
                    drawLine(Color(0xFFFFC58D).copy(alpha=.10f), androidx.compose.ui.geometry.Offset(x,y), androidx.compose.ui.geometry.Offset(x+size.width*.18f,y-size.height*.12f), 1.2f)
                }
                drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.65f))))
            }
        } else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha=.7f)))))
        if (artwork == null && !(active && album.artist == "Explosions in the Sky")) {
            Text(album.mark, color = Color.White.copy(alpha=.88f), fontSize = if (active) 78.sp else 42.sp, modifier = Modifier.align(Alignment.Center))
        }
        if (active) Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(album.artist.uppercase(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
            Text(album.title, color = Color.White.copy(alpha=.9f), fontSize = 7.sp, maxLines = 2)
        }
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha=if(active).42f else .2f), RoundedCornerShape(5.dp)))
    }
}
