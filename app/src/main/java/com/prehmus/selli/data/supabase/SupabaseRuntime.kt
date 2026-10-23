package com.prehmus.selli.data.supabase

/** Ein Prozess, ein Auth-Speicher und ein Refresh-Token-Rotator – auch im Foreground-Service. */
object SupabaseRuntime {
    @Volatile
    var client: SelliSupabaseClient? = null

    fun requireClient(): SelliSupabaseClient =
        checkNotNull(client) { "SupabaseRuntime wurde noch nicht initialisiert." }
}
