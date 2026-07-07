package com.dinfras.plexora.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dinfras.plexora.data.CatalogRepository
import com.dinfras.plexora.data.M3uCatalogSync
import com.dinfras.plexora.data.XtreamCategory
import com.dinfras.plexora.data.XtreamCredentials
import com.dinfras.plexora.data.XtreamMovie
import com.dinfras.plexora.data.XtreamService
import com.dinfras.plexora.data.friendlyNetworkError
import com.dinfras.plexora.data.isM3u
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// Premier ecran re-cable en ViewModel Hilt (etape 5/6 du portage
// architecture StreamVault-IPTV, voir le plan) : remplace la lecture directe
// de CatalogCache.kt dans MoviesScreen.kt par le repository Room. Seule la
// PLOMBERIE de chargement change ici -- le rendu (grille, fiche detail) dans
// MoviesScreen.kt reste identique, juste branche sur cet etat au lieu de
// variables locales.
//
// Les comptes M3U (creds.isM3u()) continuent d'utiliser M3uCatalogSync +
// CatalogCache comme avant : CatalogSyncManager (etape 3) ne gere que
// l'API Xtream pour l'instant, porter le flux M3U vers Room serait une
// etape separee.
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {
    var categories by mutableStateOf<List<XtreamCategory>>(emptyList())
        private set
    var movies by mutableStateOf<List<XtreamMovie>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var loadedFor: XtreamCredentials? = null

    fun loadIfNeeded(context: Context, creds: XtreamCredentials, service: XtreamService) {
        if (loadedFor == creds) return
        viewModelScope.launch {
            if (creds.isM3u()) {
                loadM3u(context, creds)
                return@launch
            }
            val cachedMovies = repository.movies()
            val cachedCategories = repository.movieCategories()
            val haveData = cachedMovies.isNotEmpty()
            if (haveData) {
                movies = cachedMovies
                categories = cachedCategories
                loading = false
            }
            // Le catalogue est deja recupere une fois en entier juste apres
            // la connexion (CatalogDownloadScreen). On ne relance la
            // synchronisation ici que si Room est encore vide pour ce type
            // de contenu -- le rafraichissement periodique en arriere-plan
            // (WorkManager) sera branche a une etape ulterieure.
            if (!haveData) {
                val result = runCatching { repository.syncXtream(creds, service) }
                if (result.isFailure) {
                    error = friendlyNetworkError(result.exceptionOrNull()!!)
                } else if (!result.getOrThrow().vod.ok) {
                    error = result.getOrThrow().vod.error
                }
                movies = repository.movies()
                categories = repository.movieCategories()
            }
            loading = false
            loadedFor = creds
        }
    }

    private suspend fun loadM3u(context: Context, creds: XtreamCredentials) {
        val cached = com.dinfras.plexora.data.CatalogCache.getMovies()
            ?: com.dinfras.plexora.data.CatalogCache.loadMoviesFromDisk(context)
        if (cached != null) {
            movies = cached.movies
            categories = cached.categories
            loading = false
        }
        if (cached == null) {
            val catalog = M3uCatalogSync.refreshAll(context, creds)
            if (catalog != null) {
                movies = catalog.movies
                categories = catalog.movieCategories
            } else {
                error = "Impossible de récupérer la playlist M3U."
            }
        }
        loading = false
        loadedFor = creds
    }
}
