plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Etape 1 du portage architecture StreamVault-IPTV (Hilt/Room/WorkManager) :
    // fondations seulement, rien n'est encore migre a ce stade.
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.dinfras.plexora"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dinfras.plexora"
        minSdk = 24
        targetSdk = 36
        // L'ancienne version Capacitor/WebView (meme applicationId) etait en
        // versionCode 7 : Android refuse d'installer un code inferieur par
        // dessus, faisant echouer silencieusement les mises a jour via
        // Downloader sur les TV qui avaient encore l'ancienne app. Doit
        // rester superieur a 7 et etre incremente a chaque APK publie.
        versionCode = 60
        versionName = "2.52"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Lecteur video natif — gere HEVC/Dolby nativement, contrairement au WebView
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")

    // Reseau : appel direct Xtream API + Supabase REST (provisioning par device id)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Fondations du portage architecture StreamVault-IPTV (etape 1/6, voir
    // le plan) : Hilt (injection de dependances), Room (base SQLite locale,
    // remplacera CatalogCache/LocalEpgStore), WorkManager (synchronisation de
    // catalogue en arriere-plan). Rien n'est encore migre a ce stade — ces
    // dependances ne sont pas encore utilisees ailleurs dans le code.
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
