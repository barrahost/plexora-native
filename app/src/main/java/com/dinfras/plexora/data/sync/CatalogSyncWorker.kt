package com.dinfras.plexora.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dinfras.plexora.data.CredentialsStore
import com.dinfras.plexora.data.DebugLog
import com.dinfras.plexora.data.XtreamClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

// Worker WorkManager (etape 3/6 du portage architecture StreamVault-IPTV,
// voir le plan) : synchronise le catalogue en arriere-plan via
// CatalogSyncManager, independamment du cycle de vie d'un ecran precis.
// Pas encore planifie/declenche nulle part a ce stade -- purement additif,
// la bascule reelle (remplacement de CatalogDownloadScreen/CatalogCache) se
// fera a l'etape 5 (re-cablage des ecrans).
@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: CatalogSyncManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val creds = CredentialsStore.load(applicationContext) ?: return Result.failure()
        val service = XtreamClient.create(creds.url)
        val result = syncManager.syncXtreamCatalog(creds, service)
        val anySucceeded = result.live.ok || result.vod.ok || result.series.ok
        DebugLog.event(
            "CatalogSyncWorker: live=${result.live.ok} vod=${result.vod.ok} series=${result.series.ok}",
        )
        return if (anySucceeded) Result.success() else Result.retry()
    }
}
