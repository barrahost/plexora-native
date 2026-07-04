package com.dinfras.plexora.data

// TiviMate n'affiche l'ecran de chargement qu'a la toute premiere ouverture
// d'un onglet : ensuite il montre instantanement le catalogue deja recupere
// pendant qu'il rafraichit discretement en arriere-plan (stale-while-
// revalidate). Chez nous, chaque retour sur Live/Films/Series relancait un
// appel reseau complet avec ecran de chargement — ce cache en memoire (le
// temps de la session app) evite ce rechargement visible.
object CatalogCache {
    data class LiveData(val categories: List<XtreamCategory>, val channels: List<XtreamChannel>)
    data class MovieData(val categories: List<XtreamCategory>, val movies: List<XtreamMovie>)
    data class SeriesData(val categories: List<XtreamCategory>, val series: List<XtreamSeries>)

    @Volatile private var live: LiveData? = null
    @Volatile private var movies: MovieData? = null
    @Volatile private var series: SeriesData? = null

    fun getLive() = live
    fun setLive(data: LiveData) { live = data }

    fun getMovies() = movies
    fun setMovies(data: MovieData) { movies = data }

    fun getSeries() = series
    fun setSeries(data: SeriesData) { series = data }

    fun clear() {
        live = null
        movies = null
        series = null
    }
}
