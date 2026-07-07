plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Etape 1 du portage architecture StreamVault-IPTV : fondations
    // Hilt (injection de dependances) + KSP (compilateur d'annotations,
    // requis par Hilt et par Room a l'etape suivante).
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}
