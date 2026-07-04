package com.dinfras.plexora

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dinfras.plexora.data.PlayerPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Lance Plexora au demarrage d'Android (ou reveil TV) si l'utilisateur a
// active le reglage correspondant — pratique sur les box/TV qui doivent
// rouvrir l'appli automatiquement apres une coupure de courant.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            if (PlayerPrefs.getAutoStartOnBoot(appContext)) {
                val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(launchIntent)
            }
        }
    }
}
