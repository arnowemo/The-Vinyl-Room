package com.thevinylroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlin.math.abs

private val amber = Color(0xFFFFB66D)
private val cream = Color(0xFFFFE8CE)
private val muted = Color(0xFFC5A58B)
private val roomDark = Color(0xFF160B07)

private data class Album(val artist: String, val title: String, val colors: List<Color>, val mark: String)
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
    var selected by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("Albums") }
    var activeNav by remember { mutableStateOf("Ma bibliothèque") }
    val album = albums[selected]

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(roomDark)) {
            RoomBackdrop(Modifier.fillMaxSize())
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
                        Icon(Icons.Default.GraphicEq, null, tint = amber, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("THE VINYL ROOM", color = cream, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.3.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsNone, null, tint = cream, modifier = Modifier.size(25.dp))
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
                        Text("218 albums   ·   ", color = muted, fontSize = 12.sp)
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF1DB954)))
                        Text("  Connecté à Spotify", color = cream.copy(alpha = .82f), fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.weight(.20f))
                BoxWithConstraints(
                    Modifier.fillMaxWidth().weight(3.1f).pointerInput(selected) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, drag -> total += drag },
                            onDragEnd = {
                                if (abs(total) > 35f) selected = if (total < 0) (selected + 1) % albums.size else (selected - 1 + albums.size) % albums.size
                                total = 0f
                            }
                        )
                    },
                    contentAlignment = Alignment.Center
                ) {
                    val sideW = (maxWidth * .19f).coerceAtLeast(54.dp)
                    val centerW = (maxWidth * .64f).coerceAtMost(290.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        val left = (selected - 1 + albums.size) % albums.size
                        val right = (selected + 1) % albums.size
                        AlbumSleeve(albums[left], false, Modifier.width(sideW).heightIn(min = 130.dp, max = 185.dp).graphicsLayer { rotationY = 22f; alpha = .88f }.clickable { selected = left })
                        Spacer(Modifier.width(5.dp))
                        AlbumSleeve(album, true, Modifier.width(centerW).aspectRatio(1f).shadow(22.dp, RoundedCornerShape(5.dp)))
                        Spacer(Modifier.width(5.dp))
                        AlbumSleeve(albums[right], false, Modifier.width(sideW).heightIn(min = 130.dp, max = 185.dp).graphicsLayer { rotationY = -22f; alpha = .88f }.clickable { selected = right })
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { selected = (selected - 1 + albums.size) % albums.size }) { Icon(Icons.Default.ChevronLeft, null, tint = amber, modifier = Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(album.artist, color = cream, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                        Text(album.title, color = muted, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                    IconButton(onClick = { selected = (selected + 1) % albums.size }) { Icon(Icons.Default.ChevronRight, null, tint = amber, modifier = Modifier.size(30.dp)) }
                }

                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { liked = !liked }) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (liked) amber else cream) }
                    IconButton(onClick = { }) { Icon(Icons.Default.AddBox, null, tint = cream) }
                    IconButton(onClick = { }) { Icon(Icons.Default.MoreHoriz, null, tint = cream) }
                }

                Spacer(Modifier.weight(.12f))
                Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("2:41", color = cream, fontSize = 11.sp)
                        Text("8:17", color = cream, fontSize = 11.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0xFF73513B))) {
                        Box(Modifier.fillMaxWidth(.34f).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFFFFD09A), amber))))
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shuffle, null, tint = muted)
                        Icon(Icons.Default.SkipPrevious, null, tint = cream, modifier = Modifier.size(29.dp))
                        Box(Modifier.size(62.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFFFD49D), amber))).clickable { playing = !playing }, contentAlignment = Alignment.Center) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color(0xFF21130D), modifier = Modifier.size(35.dp))
                        }
                        Icon(Icons.Default.SkipNext, null, tint = cream, modifier = Modifier.size(29.dp))
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { activeNav = label }) {
                            Icon(icon, null, tint = if (activeNav == label) amber else muted, modifier = Modifier.size(22.dp))
                            Text(label, color = if (activeNav == label) cream else muted, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF100805), Color(0xFF32190D), Color(0xFF100805))))
        // Plaster wall and warm pools of light
        drawRect(Brush.radialGradient(listOf(Color(0xFF684027).copy(alpha=.48f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(w*.18f,h*.28f), radius=w*.52f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF4A2A1B).copy(alpha=.38f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(w*.88f,h*.42f), radius=w*.45f))
        // Left wooden record shelves
        val shelfW = w*.39f
        drawRect(Color(0xFF1B0E08), androidx.compose.ui.geometry.Offset(0f,h*.13f), androidx.compose.ui.geometry.Size(shelfW,h*.57f))
        for (row in 0..2) {
            val y = h*(.25f + row*.15f)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8A512B),Color(0xFF32190D))), androidx.compose.ui.geometry.Offset(0f,y), androidx.compose.ui.geometry.Size(shelfW,y*.012f+h*.008f))
            for (i in 0..17) {
                val x = i*shelfW/18f + 3f
                val bw = shelfW/23f
                val bh = h*(.09f + ((i*7+row*3)%5)*.009f)
                drawRect(listOf(Color(0xFF4B3024),Color(0xFF80604A),Color(0xFF241D1B),Color(0xFF9A7656))[i%4], androidx.compose.ui.geometry.Offset(x,y-bh), androidx.compose.ui.geometry.Size(bw,bh))
            }
        }
        // Right rain-lit window
        val wx=w*.79f; val wy=h*.13f; val ww=w*.21f; val wh=h*.43f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF101A28),Color(0xFF34251F))), androidx.compose.ui.geometry.Offset(wx,wy), androidx.compose.ui.geometry.Size(ww,wh))
        for (i in 1..3) drawLine(Color(0xFF6B4933), androidx.compose.ui.geometry.Offset(wx+ww*i/4f,wy), androidx.compose.ui.geometry.Offset(wx+ww*i/4f,wy+wh), 3f)
        drawLine(Color(0xFF6B4933), androidx.compose.ui.geometry.Offset(wx,wy+wh*.48f), androidx.compose.ui.geometry.Offset(w,wy+wh*.48f), 4f)
        for(i in 0..36) { val x=wx+(i*37%100)/100f*ww; val y=wy+(i*61%100)/100f*wh; drawCircle(Color(0xFFFFA45E).copy(alpha=.15f+(i%4)*.08f), radius=2f+(i%3)*1.5f, center=androidx.compose.ui.geometry.Offset(x,y)) }
        // Wooden desktop
        drawRect(Brush.verticalGradient(listOf(Color(0xFF6A3A1D),Color(0xFF241108))), androidx.compose.ui.geometry.Offset(0f,h*.73f), androidx.compose.ui.geometry.Size(w,h*.27f))
        for(i in 0..10) drawLine(Color(0xFFB16C37).copy(alpha=.16f), androidx.compose.ui.geometry.Offset(0f,h*(.76f+i*.02f)), androidx.compose.ui.geometry.Offset(w,h*(.76f+i*.02f)), 1.5f)
        // Turntable on the right side of desk
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF9B572B),Color(0xFF29150D))), androidx.compose.ui.geometry.Offset(w*.61f,h*.80f), androidx.compose.ui.geometry.Size(w*.42f,h*.105f), androidx.compose.ui.geometry.CornerRadius(14f))
        drawCircle(Color(0xFF08090B), radius=w*.105f, center=androidx.compose.ui.geometry.Offset(w*.81f,h*.805f))
        drawCircle(Color(0xFF34231B), radius=w*.075f, center=androidx.compose.ui.geometry.Offset(w*.81f,h*.805f))
        drawCircle(amber.copy(alpha=.7f), radius=w*.018f, center=androidx.compose.ui.geometry.Offset(w*.81f,h*.805f))
        // Salt lamp
        drawCircle(Color(0xFFFFA344).copy(alpha=.25f), radius=w*.11f, center=androidx.compose.ui.geometry.Offset(w*.68f,h*.75f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD58A),Color(0xFFB94D19))), radius=w*.047f, center=androidx.compose.ui.geometry.Offset(w*.68f,h*.75f))
        // Mug and steam on the left
        drawRoundRect(Color(0xFFB78B69), androidx.compose.ui.geometry.Offset(w*.07f,h*.80f), androidx.compose.ui.geometry.Size(w*.16f,h*.09f), androidx.compose.ui.geometry.CornerRadius(10f))
        drawCircle(Color.Transparent, radius=w*.04f, center=androidx.compose.ui.geometry.Offset(w*.23f,h*.84f), style=androidx.compose.ui.graphics.drawscope.Stroke(width=5f), color=Color(0xFFB78B69))
        for(i in 0..2) drawLine(Color(0xFFFFD4A0).copy(alpha=.18f), androidx.compose.ui.geometry.Offset(w*(.11f+i*.035f),h*.78f), androidx.compose.ui.geometry.Offset(w*(.13f+i*.035f),h*.74f), 2f)
    }
}

@Composable
private fun AlbumSleeve(album: Album, active: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(Brush.linearGradient(album.colors))) {
        if (active) {
            Canvas(Modifier.fillMaxSize()) {
                for(i in 0..35) {
                    val x=size.width*(i%6)/6f
                    val y=size.height*(i/6)/6f
                    drawLine(Color(0xFFFFC58D).copy(alpha=.10f), androidx.compose.ui.geometry.Offset(x,y), androidx.compose.ui.geometry.Offset(x+size.width*.18f,y-size.height*.12f), 1.2f)
                }
                drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.65f))))
            }
        } else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha=.7f)))))
        Text(album.mark, color = Color.White.copy(alpha=.88f), fontSize = if (active) 78.sp else 42.sp, modifier = Modifier.align(Alignment.Center))
        if (active) Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(album.artist.uppercase(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
            Text(album.title, color = Color.White.copy(alpha=.9f), fontSize = 7.sp, maxLines = 2)
        }
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha=if(active).42f else .2f), RoundedCornerShape(5.dp)))
    }
}
