package com.ausgetrunken

import android.content.Context
import android.content.SharedPreferences
import com.ausgetrunken.data.local.TokenStorage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

/**
 * Critical test to prevent session persistence regressions.
 * 
 * This test ensures that users remain logged in when they:
 * 1. Log in successfully
 * 2. Kill the application
 * 3. Restart the application
 * 
 * IMPORTANT: This test must ALWAYS pass. If it fails, session persistence is broken.
 */
@RunWith(MockitoJUnitRunner::class)
class SessionPersistenceTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock 
    private lateinit var mockPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    private lateinit var tokenStorage: TokenStorage
    
    // Test data
    private val testUserId = "test-user-id-12345"
    private val testAccessToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.test-access-token"
    private val testRefreshToken = "test-refresh-token-12345"
    private val testSessionId = "session-id-12345"
    
    @Before
    fun setup() {
        // Setup mock context and preferences
        `when`(mockContext.getSharedPreferences("ausgetrunken_auth", Context.MODE_PRIVATE))
            .thenReturn(mockPreferences)
        `when`(mockPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        
        tokenStorage = TokenStorage(mockContext)
    }
    
    @Test
    fun `test session persists after app restart - CRITICAL`() {
        // Simulate user logging in successfully
        val currentTime = System.currentTimeMillis()
        val expirationTime = currentTime + TimeUnit.DAYS.toMillis(30)
        
        // Mock that tokens are saved successfully
        setupValidTokensInPreferences(currentTime, expirationTime)
        
        // Step 1: User logs in - tokens are saved
        tokenStorage.saveLoginSession(testAccessToken, testRefreshToken, testUserId, testSessionId)
        
        // Verify tokens were attempted to be saved
        verify(mockEditor).putString("access_token", testAccessToken)
        verify(mockEditor).putString("refresh_token", testRefreshToken)
        verify(mockEditor).putString("user_id", testUserId)
        verify(mockEditor).putString("session_id", testSessionId)
        verify(mockEditor).putLong("expiration_time", anyLong())
        verify(mockEditor).putLong("login_time", anyLong())
        verify(mockEditor, atLeastOnce()).apply()
        
        // Step 2: App is killed and restarted - simulate by creating new TokenStorage
        val newTokenStorage = TokenStorage(mockContext)
        
        // Step 3: App tries to restore session
        val sessionInfo = newTokenStorage.getSessionInfo()
        
        // CRITICAL ASSERTION: Session must be restored successfully
        assertNotNull("Session must be restored after app restart", sessionInfo)
        assertEquals("Access token must match", testAccessToken, sessionInfo!!.accessToken)
        assertEquals("Refresh token must match", testRefreshToken, sessionInfo.refreshToken) 
        assertEquals("User ID must match", testUserId, sessionInfo.userId)
        assertEquals("Session ID must match", testSessionId, sessionInfo.sessionId)
        
        // Verify tokens are still valid
        assertTrue("Tokens must still be valid after restart", newTokenStorage.isTokenValid())
        
        // Verify individual getters work without clearing session
        assertEquals("Access token getter must work", testAccessToken, newTokenStorage.getAccessToken())
        assertEquals("Refresh token getter must work", testRefreshToken, newTokenStorage.getRefreshToken())
        assertEquals("User ID getter must work", testUserId, newTokenStorage.getUserId())
        
        // Verify session wasn't cleared accidentally
        verify(mockEditor, never()).remove(anyString())
    }
    
    @Test
    fun `test getters do not clear session when tokens exist`() {
        val currentTime = System.currentTimeMillis()
        val expirationTime = currentTime + TimeUnit.DAYS.toMillis(30)
        
        // Setup valid tokens
        setupValidTokensInPreferences(currentTime, expirationTime)
        
        // Test that getters work without clearing session
        val accessToken = tokenStorage.getAccessToken()
        val refreshToken = tokenStorage.getRefreshToken()
        val userId = tokenStorage.getUserId()
        
        // Assert tokens are returned
        assertEquals(testAccessToken, accessToken)
        assertEquals(testRefreshToken, refreshToken)
        assertEquals(testUserId, userId)
        
        // CRITICAL: Verify clearSession was never called
        verify(mockEditor, never()).remove(anyString())
    }
    
    @Test
    fun `test expired tokens do not auto-clear session`() {
        val currentTime = System.currentTimeMillis()
        val expiredTime = currentTime - TimeUnit.DAYS.toMillis(1) // Expired yesterday
        
        // Setup expired tokens
        setupValidTokensInPreferences(currentTime, expiredTime)
        
        // Try to get session info with expired tokens
        val sessionInfo = tokenStorage.getSessionInfo()
        
        // Should return null for expired tokens, but NOT clear the session
        assertNull("Expired tokens should return null", sessionInfo)
        
        // CRITICAL: Tokens should still exist for potential refresh
        assertEquals("Expired access token should still be accessible", testAccessToken, tokenStorage.getAccessToken())
        assertEquals("Expired refresh token should still be accessible", testRefreshToken, tokenStorage.getRefreshToken())
        assertEquals("User ID should still be accessible", testUserId, tokenStorage.getUserId())
        
        // Verify clearSession was never called automatically
        verify(mockEditor, never()).remove(anyString())
    }
    
    @Test
    fun `test manual clearSession works correctly`() {
        val currentTime = System.currentTimeMillis()
        val expirationTime = currentTime + TimeUnit.DAYS.toMillis(30)
        
        setupValidTokensInPreferences(currentTime, expirationTime)
        
        // Manually clear session
        tokenStorage.clearSession()
        
        // Verify all tokens are removed
        verify(mockEditor).remove("access_token")
        verify(mockEditor).remove("refresh_token") 
        verify(mockEditor).remove("user_id")
        verify(mockEditor).remove("expiration_time")
        verify(mockEditor).remove("login_time")
        verify(mockEditor).remove("session_id")
        verify(mockEditor, atLeastOnce()).apply()
    }
    
    private fun setupValidTokensInPreferences(currentTime: Long, expirationTime: Long) {
        `when`(mockPreferences.getString("access_token", null)).thenReturn(testAccessToken)
        `when`(mockPreferences.getString("refresh_token", null)).thenReturn(testRefreshToken)
        `when`(mockPreferences.getString("user_id", null)).thenReturn(testUserId)
        `when`(mockPreferences.getString("session_id", null)).thenReturn(testSessionId)
        `when`(mockPreferences.getLong("expiration_time", 0)).thenReturn(expirationTime)
        `when`(mockPreferences.getLong("login_time", 0)).thenReturn(currentTime)
    }
}