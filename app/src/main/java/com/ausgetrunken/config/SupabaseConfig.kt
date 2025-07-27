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
    private const val SUPABASE_SERVICE_ROLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhqbGJ5cHpoaXhlcXZrc3huaWxrIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc1MjY5NDgyMSwiZXhwIjoyMDY4MjcwODIxfQ.s3a9J-qrCmK6iZcceGC4JMXoQgQU31fpPGQC5z3up5A"
    
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
    
    // Service role client for operations that need elevated permissions (like storage uploads and session restoration)
    val serviceRoleClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_SERVICE_ROLE_KEY
    ) {
        install(Storage)
        install(Postgrest)
    }
}