package com.ausgetrunken.data.repository

import com.ausgetrunken.data.local.dao.UserDao
import com.ausgetrunken.data.local.entities.UserEntity
import com.ausgetrunken.data.remote.model.UserProfile
import com.ausgetrunken.domain.util.NetworkConnectivityManager
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class UserRepository(
    private val userDao: UserDao,
    private val postgrest: Postgrest,
    private val networkManager: NetworkConnectivityManager
) {
    fun getUserById(userId: String): Flow<UserEntity?> = userDao.getUserByIdFlow(userId)

    suspend fun syncUserFromSupabase(userId: String): Result<UserEntity> {
        return try {
            val response = postgrest.from("user_profiles")
                .select {
                    filter {
                        eq("id", userId)
                        eq("flagged_for_deletion", false)
                    }
                }
                .decodeSingle<UserProfile>()
                
            val user = UserEntity(
                id = userId,
                email = response.email,
                userType = com.ausgetrunken.data.local.entities.UserType.valueOf(response.userType),
                profileCompleted = response.profileCompleted,
                createdAt = response.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                fullName = response.fullName
            )
            userDao.insertUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUser(user: UserEntity): Result<Unit> {
        return try {
            userDao.updateUser(user)
            
            postgrest.from("user_profiles")
                .update(
                    buildJsonObject {
                        put("email", user.email)
                        put("user_type", user.userType.name)
                        put("profile_completed", user.profileCompleted)
                        put("full_name", user.fullName)
                        put("updated_at", Instant.now().toString())
                    }
                ) {
                    filter {
                        eq("id", user.id)
                        eq("flagged_for_deletion", false)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUserByIdRemoteFirst(userId: String): UserEntity? {
        return networkManager.executeRemoteFirst(
            operationName = "getUserById",
            remoteOperation = {
                val response = postgrest.from("user_profiles")
                    .select {
                        filter {
                            eq("id", userId)
                            eq("flagged_for_deletion", false)
                        }
                    }
                    .decodeSingle<UserProfile>()
                    
                val user = UserEntity(
                    id = userId,
                    email = response.email,
                    userType = com.ausgetrunken.data.local.entities.UserType.valueOf(response.userType),
                    profileCompleted = response.profileCompleted,
                    createdAt = response.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    fullName = response.fullName
                )
                Result.success(user)
            },
            localFallback = {
                userDao.getUserById(userId)
            },
            cacheUpdate = { user ->
                user?.let { userDao.insertUser(it) }
            }
        )
    }
    
    suspend fun updateUserName(userId: String, fullName: String): Result<Unit> {
        return try {
            println("🔄 UserRepository: Updating user name for $userId to: $fullName")
            
            // REMOTE-FIRST: Update Supabase first
            postgrest.from("user_profiles")
                .update(
                    buildJsonObject {
                        put("full_name", fullName)
                        put("updated_at", Instant.now().toString())
                    }
                ) {
                    filter {
                        eq("id", userId)
                        eq("flagged_for_deletion", false)
                    }
                }
            
            println("✅ UserRepository: Supabase name update successful")
            
            // LOCAL SECOND: Update local database after successful remote update
            val currentUser = userDao.getUserById(userId)
            if (currentUser != null) {
                val updatedUser = currentUser.copy(fullName = fullName)
                userDao.updateUser(updatedUser)
                println("✅ UserRepository: Local database name updated")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ UserRepository: Name update failed: ${e.message}")
            Result.failure(e)
        }
    }
}