package com.ausgetrunken.config

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.functions.Functions

object SupabaseConfig {
    
    // Supabase project credentials
    private const val SUPABASE_URL = "https://xjlbypzhixeqvksxnilk.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhqbGJ5cHpoaXhlcXZrc3huaWxrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTI2OTQ4MjEsImV4cCI6MjA2ODI3MDgyMX0.PrcrF1pA4KB30VlOJm2MYkOLlgf3e3SPn2Uo_eiDKfc"
    
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
        install(Functions)
    }
}