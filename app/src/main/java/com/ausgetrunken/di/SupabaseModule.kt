package com.ausgetrunken.di

import com.ausgetrunken.config.SupabaseConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.functions.Functions
import org.koin.dsl.module

val supabaseModule = module {
    single<SupabaseClient> { SupabaseConfig.client }
    single<Auth> { get<SupabaseClient>().pluginManager.getPluginOrNull(Auth) ?: error("Auth plugin not installed") }
    single<Postgrest> { get<SupabaseClient>().pluginManager.getPluginOrNull(Postgrest) ?: error("Postgrest plugin not installed") }
    single<Storage> { get<SupabaseClient>().pluginManager.getPluginOrNull(Storage) ?: error("Storage plugin not installed") }
    single<Realtime> { get<SupabaseClient>().pluginManager.getPluginOrNull(Realtime) ?: error("Realtime plugin not installed") }
    single<Functions> { get<SupabaseClient>().pluginManager.getPluginOrNull(Functions) ?: error("Functions plugin not installed") }
    
    // Service role storage for uploads (bypasses RLS)
    single<Storage>(qualifier = org.koin.core.qualifier.named("serviceRole")) { 
        SupabaseConfig.serviceRoleClient.pluginManager.getPluginOrNull(Storage) ?: error("Service role Storage plugin not installed") 
    }
    
    // Service role postgrest for session restoration queries (bypasses RLS)
    single<Postgrest>(qualifier = org.koin.core.qualifier.named("serviceRole")) { 
        SupabaseConfig.serviceRoleClient.pluginManager.getPluginOrNull(Postgrest) ?: error("Service role Postgrest plugin not installed") 
    }
}