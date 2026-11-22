package com.thevinylroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

private val amber = Color(0xFFFFB66D)
private val cream = Color(0xFFFFE8CE)
private val muted = Color(0xFFC5A58B)

private data class Album(val artist: String, val title: String, val colors: List<Color>, val symbol: String)
private val albums = listOf(
    Album("Explosions in the Sky", "The Earth Is Not a Cold Dead Place", listOf(Color(0xFF172A3D), Color(0xFFDA8750), Color(0xFF080D16)), "✳"),
    Album("Alcest", "Écailles de lune", listOf(Color(0xFF101B31), Color(0xFFB8C5D5), Color(0xFF283C58)), "☾"),
    Album("Amenra", "Mass VI", listOf(Color(0xFF1B1A19), Color(0xFF77716A), Color(0xFF090909)), "M"),
    Album("Agalloch", "The Mantle", listOf(Color(0xFF171B19), Color(0xFF8C8978), Color(0xFF202923)), "♠"),
    Album("Sigur Rós", "Ágætis byrjun", listOf(Color(0xFF7D3434), Color(0xFFD5A1A0), Color(0xFF241D25)), "✺")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VinylRoomApp() }
    }
}

@Composable
private fun VinylRoomApp() {
    var selected by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    val album = albums[selected]

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF100A07), Color(0xFF382013), Color(0xFF170D08)))
        )) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, null, tint = amber, modifier = Modifier.size(25.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("THE VINYL ROOM", color = cream, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                    Icon(Icons.Default.NotificationsNone, null, tint = cream.copy(alpha = .8f))
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center) {
                    listOf("Albums", "Artistes", "Playlists", "Titres").forEachIndexed { i, label ->
                        Text(
                            label, color = if (i == 0) cream else muted, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(22.dp))
                                .background(if (i == 0) Color(0xFF654027) else Color.Transparent)
                                .border(if (i == 0) 1.dp else 0.dp, amber.copy(alpha = if (i == 0) .65f else 0f), RoundedCornerShape(22.dp))
                                .clickable { }.padding(horizontal = 11.dp, vertical = 9.dp)
                        )
                    }
                }

                Column(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ma bibliothèque", color = cream, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ta collection   ·   ", color = muted, fontSize = 12.sp)
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF1DB954)))
                        Text("  Connecté à Spotify", color = muted, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(28.dp))
                // Three sleeves stay in view: the selected record is forward,
                // while its neighbours tilt away like records on the reference shelf.
                BoxWithConstraints(Modifier.fillMaxWidth().height(292.dp), contentAlignment = Alignment.Center) {
                    val centerCoverWidth = (this.maxWidth - 144.dp).coerceAtLeast(190.dp)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val leftIndex = (selected - 1 + albums.size) % albums.size
                        val rightIndex = (selected + 1) % albums.size
                        AlbumCover(
                            albums[leftIndex], false,
                            Modifier.width(62.dp).height(214.dp)
                                .graphicsLayer { rotationY = 25f; cameraDistance = 12f * density; alpha = .78f }
                                .clickable { selected = leftIndex }
                        )
                        Spacer(Modifier.width(5.dp))
                        AlbumCover(
                            album, true,
                            Modifier.width(centerCoverWidth).height(270.dp)
                                .graphicsLayer { shadowElevation = 24.dp.toPx() }
                        )
                        Spacer(Modifier.width(5.dp))
                        AlbumCover(
                            albums[rightIndex], false,
                            Modifier.width(62.dp).height(214.dp)
                                .graphicsLayer { rotationY = -25f; cameraDistance = 12f * density; alpha = .78f }
                                .clickable { selected = rightIndex }
                        )
                    }
                }
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(13.dp)
                        .shadow(12.dp, RoundedCornerShape(3.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF9A6037), Color(0xFF3B2114))))
                )

                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { selected = (selected - 1 + albums.size) % albums.size }) {
                        Icon(Icons.Default.ChevronLeft, null, tint = amber, modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(album.artist, color = cream, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                        Text(album.title, color = muted, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                    IconButton(onClick = { selected = (selected + 1) % albums.size }) {
                        Icon(Icons.Default.ChevronRight, null, tint = amber, modifier = Modifier.size(30.dp))
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { liked = !liked }) {
                        Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (liked) amber else cream)
                    }
                    IconButton(onClick = { }) { Icon(Icons.Default.AddBox, null, tint = cream) }
                    IconButton(onClick = { }) { Icon(Icons.Default.MoreHoriz, null, tint = cream) }
                }

                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("—:—", color = muted, fontSize = 11.sp)
                        Text("—:—", color = muted, fontSize = 11.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color(0xFF70503B))) {
                        Box(Modifier.fillMaxWidth(.34f).fillMaxHeight().background(amber))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shuffle, null, tint = muted)
                        Icon(Icons.Default.SkipPrevious, null, tint = cream, modifier = Modifier.size(29.dp))
                        Box(
                            Modifier.size(54.dp).clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(Color(0xFFFFD19A), amber)))
                                .clickable { playing = !playing },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color(0xFF21130D), modifier = Modifier.size(32.dp))
                        }
                        Icon(Icons.Default.SkipNext, null, tint = cream, modifier = Modifier.size(29.dp))
                        Icon(Icons.Default.Repeat, null, tint = muted)
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(Icons.Default.Home to "Accueil", Icons.Default.LibraryMusic to "Bibliothèque", Icons.Default.Search to "Recherche", Icons.Default.FavoriteBorder to "Favoris", Icons.Default.PlaylistPlay to "Playlists").forEach { (icon, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { }) {
                            Icon(icon, null, tint = if (label == "Accueil") amber else muted, modifier = Modifier.size(20.dp))
                            Text(label, color = if (label == "Accueil") cream else muted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCover(album: Album, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.shadow(if (active) 22.dp else 8.dp, RoundedCornerShape(5.dp))
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.linearGradient(album.colors))
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .68f)))))
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            Text(album.symbol, color = Color.White.copy(alpha = .82f), fontSize = if (active) 76.sp else 56.sp)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(album.artist.uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(album.title, color = Color.White.copy(alpha = .9f), fontSize = 8.sp, maxLines = 2)
        }
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = if (active) .45f else .18f), RoundedCornerShape(5.dp)))
    }
}
