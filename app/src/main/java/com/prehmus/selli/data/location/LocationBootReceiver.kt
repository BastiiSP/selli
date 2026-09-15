package com.prehmus.selli.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prehmus.selli.ui.location.hasForegroundLocationPermission

/**
 * Bekannte Robustheitslücke (siehe Vault-Historie, 15.09.2026): der Standort-Dienst startete
 * bisher ausschließlich, wenn [com.prehmus.selli.ui.location.LocationScreen] geöffnet wurde —
 * nach einem Geräte-Neustart blieb die Standortfreigabe damit inaktiv, bis jemand den
 * Standort-Tab wieder von Hand öffnet. Es gibt bewusst keinen Ein/Aus-Schalter für dieses
 * Feature (siehe [SelliLocationService]), ein Neustart nach Boot ist also immer das richtige
 * Verhalten, sofern die Vordergrund-Berechtigung vorliegt — [SelliLocationService] selbst
 * bricht sauber ab (`stopSelf()`), falls Supabase nicht konfiguriert oder niemand angemeldet
 * ist, ein zusätzlicher Konfigurations-Check ist hier deshalb nicht nötig.
 */
class LocationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!context.hasForegroundLocationPermission()) return
        SelliLocationService.start(context)
    }
}
