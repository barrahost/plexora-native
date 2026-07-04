package com.dinfras.plexora.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinfras.plexora.data.*
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.theme.PlexoraOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
@Composable
fun MoviesScreen(creds: XtreamCredentials) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var movies by remember { mutableStateOf<List<XtreamMovie>>(emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<XtreamMovie?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(creds) {
        runCatching {
            categories = service.getVodCategories(creds.username, creds.password)
            movies = service.getVodStreams(creds.username, creds.password).filter { it.streamId > 0 }
        }.onFailure { error = it.message ?: it.toString() }
        loading = false
    }

    val filtered = remember(movies, selectedCat) {
        if (selectedCat == null) movies else movies.filter { it.categoryId == selectedCat }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Erreur de chargement :\n$error", color = Color.Red) }
        return
    }

    val movie = selected
    if (movie != null) {
        MovieDetail(creds = creds, service = service, movie = movie, onBack = { selected = null })
        return
    }

    var focused by remember { mutableStateOf(filtered.firstOrNull()) }
    var focusedInfo by remember { mutableStateOf<VodInfo?>(null) }
    LaunchedEffect(focused) {
        focusedInfo = null
        val f = focused ?: return@LaunchedEffect
        delay(250)
        focusedInfo = runCatching { service.getVodInfo(creds.username, creds.password, f.streamId).info }.getOrNull()
    }

    // La colonne categories se replie des qu'on en valide une (OK), pour
    // laisser toute la largeur a la grille — comme sur TiviMate. Fleche
    // GAUCHE depuis la grille, ou Retour, la refait reapparaitre.
    var categoriesCollapsed by remember { mutableStateOf(false) }
    var categoryFocus by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = categoriesCollapsed) { categoriesCollapsed = false }

    val firstCategoryFocusRequester = remember { FocusRequester() }
    // Sans ceci, le systeme place le curseur D-pad n'importe ou (ou nulle
    // part) a l'ouverture de l'ecran, et il est invisible tant qu'on n'a
    // pas appuye sur une touche — on force le focus sur la 1ere categorie.
    LaunchedEffect(categoriesCollapsed) {
        if (!categoriesCollapsed) firstCategoryFocusRequester.requestFocus()
    }

    Row(Modifier.fillMaxSize()) {
        if (!categoriesCollapsed) {
            LazyColumn(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF111827))) {
                item {
                    CategoryEntryRow(
                        label = "Tous les films",
                        active = selectedCat == null,
                        focused = categoryFocus == "__all__",
                        onFocus = { categoryFocus = "__all__" },
                        onClick = { selectedCat = null; categoriesCollapsed = true },
                        modifier = Modifier.focusRequester(firstCategoryFocusRequester),
                    )
                }
                items(categories) { cat ->
                    CategoryEntryRow(
                        label = cat.categoryName,
                        active = selectedCat == cat.categoryId,
                        focused = categoryFocus == cat.categoryId,
                        onFocus = { categoryFocus = cat.categoryId },
                        onClick = { selectedCat = cat.categoryId; categoriesCollapsed = true },
                    )
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF030712))) {
            val fm = focused
            if (fm != null) {
                MediaPreviewInfo(
                    title = fm.name,
                    rating = fm.rating5based,
                    releaseDate = focusedInfo?.releaseDate,
                    genre = focusedInfo?.genre,
                    plot = focusedInfo?.plot,
                    cast = focusedInfo?.cast,
                    director = focusedInfo?.director,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered) { m ->
                    Column(
                        Modifier
                            .clickable { selected = m }
                            .focusable()
                            .onFocusChanged { if (it.isFocused) focused = m },
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F2937))) {
                            val poster = m.streamIcon ?: m.cover
                            if (!poster.isNullOrBlank()) {
                                AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(m.name, maxLines = 2, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                }
            }
        }
    }
}

// Meme logique de double surbrillance que la sidebar : blanc = curseur
// D-pad, orange = categorie reellement selectionnee.
@Composable
fun CategoryEntryRow(
    label: String,
    active: Boolean,
    focused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        focused -> Color.White
        active -> PlexoraOrange.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val fg = if (focused) Color.Black else Color.White
    Text(
        label,
        modifier = modifier.fillMaxWidth()
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(onClick = onClick)
            .background(bg)
            .padding(16.dp, 12.dp),
        color = fg,
        fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Normal,
    )
}

// Fiche film en plein largeur (banniere + synopsis + actions), meme
// disposition que la fiche serie — remplace l'ancienne affiche fixe + apercu.
@androidx.media3.common.util.UnstableApi
@Composable
fun MovieDetail(creds: XtreamCredentials, service: XtreamService, movie: XtreamMovie, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<VodInfo?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    BackHandler(enabled = fullscreen) { fullscreen = false }
    BackHandler(enabled = !fullscreen) { onBack() }

    LaunchedEffect(movie) {
        saved = MyListStore.isSavedMovie(context, movie.streamId)
        info = runCatching { service.getVodInfo(creds.username, creds.password, movie.streamId).info }.getOrNull()
    }

    val streamUrl = remember(movie) {
        XtreamClient.vodStreamUrl(creds.url, creds.username, creds.password, movie.streamId, movie.containerExtension ?: "mp4")
    }

    if (fullscreen) {
        FullscreenPlayer(streamUrl = streamUrl, title = movie.name, onClose = { fullscreen = false })
        return
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF030712)).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            val backdrop = info?.backdropPath?.firstOrNull() ?: movie.streamIcon ?: movie.cover
            if (!backdrop.isNullOrBlank()) {
                AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF030712), Color(0xFF030712).copy(alpha = 0.4f), Color.Transparent))))
            Column(Modifier.align(Alignment.BottomStart).padding(24.dp).widthIn(max = 720.dp)) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("< Retour", color = Color.White) }
                Spacer(Modifier.height(4.dp))
                val year = info?.releaseDate?.take(4)
                Text(
                    if (!year.isNullOrBlank()) "${movie.name} ($year)" else movie.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rating = movie.rating5based
                    if (rating != null && rating > 0) {
                        Text(
                            String.format("%.1f", rating),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.background(PlexoraOrange, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    val meta = listOfNotNull(year, info?.genre).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, color = Color.Gray, fontSize = 13.sp)
                }
                info?.cast?.let { if (it.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("Acteurs : $it", color = Color(0xFF9CA3AF), fontSize = 13.sp, maxLines = 1) } }
                info?.director?.let { if (it.isNotBlank()) { Text("Réalisateur : $it", color = Color(0xFF9CA3AF), fontSize = 13.sp, maxLines = 1) } }
                info?.plot?.let { if (it.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(it, color = Color.LightGray, fontSize = 13.sp, maxLines = 5) } }
            }
        }

        Row(Modifier.fillMaxWidth().padding(24.dp, 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(icon = Icons.Filled.PlayArrow, label = "Regarder", primary = true) { fullscreen = true }
            ActionButton(icon = Icons.Filled.OpenInNew, label = "Ouvrir dans un lecteur externe") {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(streamUrl), "video/*")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            }
            val trailer = info?.youtubeTrailer
            if (!trailer.isNullOrBlank()) {
                ActionButton(icon = Icons.Filled.Movie, label = "Bande annonce") {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$trailer"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
            ActionButton(icon = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, label = "Ajouter à ma liste") {
                scope.launch { saved = MyListStore.toggleMovie(context, movie.streamId) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
