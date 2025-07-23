package com.ausgetrunken.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ausgetrunken.data.local.entities.UserType
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun SplashScreen(
    onNavigateToLogin: (String?) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToWineyardList: () -> Unit,
    viewModel: SplashViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Animation states
    val wineProgress by animateFloatAsState(
        targetValue = if (uiState.isLoading) 1f else 0f,
        animationSpec = tween(
            durationMillis = 2500,
            easing = EaseInOutCubic
        ),
        label = "wine_fill"
    )
    
    val glassAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 800,
            delayMillis = 200,
            easing = EaseOut
        ),
        label = "glass_alpha"
    )
    
    val textAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 1000,
            delayMillis = 600,
            easing = EaseOut
        ),
        label = "text_alpha"
    )
    
    val shimmerOffset by animateFloatAsState(
        targetValue = if (uiState.isLoading) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    // Only trigger navigation when loading is complete
    LaunchedEffect(uiState.isLoading, uiState.isAuthenticated, uiState.userType) {
        if (!uiState.isLoading) {
            println("🔍 SplashScreen: LaunchedEffect triggered - Loading complete")
            println("🔍 SplashScreen: isAuthenticated = ${uiState.isAuthenticated}, userType = ${uiState.userType}")
            
            delay(500) // Small delay to complete animation
            
            if (uiState.isAuthenticated) {
                when (uiState.userType) {
                    UserType.CUSTOMER -> {
                        println("🔍 SplashScreen: Navigating to WineyardList for CUSTOMER")
                        onNavigateToWineyardList()
                    }
                    UserType.WINEYARD_OWNER -> {
                        println("🔍 SplashScreen: Navigating to Profile for WINEYARD_OWNER")
                        onNavigateToProfile()
                    }
                    null -> {
                        println("🔍 SplashScreen: UserType is null, navigating to login")
                        onNavigateToLogin(null)
                    }
                }
            } else {
                println("🔍 SplashScreen: Not authenticated, navigating to login with error: ${uiState.errorMessage}")
                onNavigateToLogin(uiState.errorMessage)
            }
        } else {
            println("🔍 SplashScreen: Still loading, not triggering navigation")
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A1A),
                        Color(0xFF0D0D0D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Wine Glass Animation
            WineGlassAnimation(
                progress = wineProgress,
                glassAlpha = glassAlpha,
                shimmerOffset = shimmerOffset,
                modifier = Modifier.size(200.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // App Title
            Text(
                text = "Ausgetrunken",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD4AF37), // Gold color
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tagline
            Text(
                text = "Sip. Savor. Celebrate.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFFB8860B).copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Loading text
            if (uiState.isLoading) {
                Text(
                    text = "Preparing your cellar...",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.alpha(textAlpha)
                )
            }
            
            // No error messages on splash screen - they should only appear on login screen
        }
    }
}

@Composable
private fun WineGlassAnimation(
    progress: Float,
    glassAlpha: Float,
    shimmerOffset: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val glassPath = createWineGlassPath(size)
        val wineHeight = size.height * 0.6f * progress // Wine fills 60% of glass max
        
        // Draw wine fill
        if (progress > 0f) {
            drawWineFill(glassPath, wineHeight, shimmerOffset)
        }
        
        // Draw glass outline
        drawGlassOutline(glassPath, glassAlpha)
        
        // Draw glass shine
        drawGlassShine(glassPath, glassAlpha)
    }
}

private fun DrawScope.createWineGlassPath(size: Size): Path {
    val path = Path()
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    
    // Wine glass bowl - elegant curve
    path.moveTo(centerX - size.width * 0.25f, centerY - size.height * 0.15f)
    path.quadraticBezierTo(
        centerX - size.width * 0.3f, centerY + size.height * 0.1f,
        centerX - size.width * 0.2f, centerY + size.height * 0.25f
    )
    
    // Bottom of bowl to stem
    path.lineTo(centerX - size.width * 0.05f, centerY + size.height * 0.3f)
    path.lineTo(centerX - size.width * 0.05f, centerY + size.height * 0.4f)
    
    // Base
    path.lineTo(centerX - size.width * 0.15f, centerY + size.height * 0.4f)
    path.lineTo(centerX + size.width * 0.15f, centerY + size.height * 0.4f)
    
    // Right side stem
    path.lineTo(centerX + size.width * 0.05f, centerY + size.height * 0.4f)
    path.lineTo(centerX + size.width * 0.05f, centerY + size.height * 0.3f)
    
    // Right side of bowl
    path.lineTo(centerX + size.width * 0.2f, centerY + size.height * 0.25f)
    path.quadraticBezierTo(
        centerX + size.width * 0.3f, centerY + size.height * 0.1f,
        centerX + size.width * 0.25f, centerY - size.height * 0.15f
    )
    
    // Top rim
    path.quadraticBezierTo(
        centerX, centerY - size.height * 0.2f,
        centerX - size.width * 0.25f, centerY - size.height * 0.15f
    )
    
    path.close()
    return path
}

private fun DrawScope.drawWineFill(glassPath: Path, wineHeight: Float, shimmerOffset: Float) {
    val wineRect = android.graphics.RectF(
        0f,
        size.height - wineHeight,
        size.width,
        size.height
    )
    
    clipPath(glassPath) {
        // Wine gradient
        val wineGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF8B0000), // Dark red
                Color(0xFFDC143C), // Crimson
                Color(0xFF8B0000)  // Dark red
            ),
            startY = size.height - wineHeight,
            endY = size.height
        )
        
        drawRect(
            brush = wineGradient,
            topLeft = Offset(0f, size.height - wineHeight),
            size = Size(size.width, wineHeight)
        )
        
        // Wine surface shimmer
        val shimmerGradient = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0x40FFFFFF),
                Color.Transparent
            ),
            startX = size.width * (shimmerOffset - 0.5f),
            endX = size.width * (shimmerOffset + 0.5f)
        )
        
        drawRect(
            brush = shimmerGradient,
            topLeft = Offset(0f, size.height - wineHeight),
            size = Size(size.width, 20f)
        )
    }
}

private fun DrawScope.drawGlassOutline(glassPath: Path, alpha: Float) {
    drawPath(
        path = glassPath,
        color = Color(0xFFE6E6E6).copy(alpha = alpha * 0.8f),
        style = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawGlassShine(glassPath: Path, alpha: Float) {
    // Glass reflection shine
    val shinePath = Path()
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    
    shinePath.moveTo(centerX - size.width * 0.15f, centerY - size.height * 0.1f)
    shinePath.quadraticBezierTo(
        centerX - size.width * 0.1f, centerY,
        centerX - size.width * 0.12f, centerY + size.height * 0.15f
    )
    
    clipPath(glassPath) {
        drawPath(
            path = shinePath,
            color = Color.White.copy(alpha = alpha * 0.3f),
            style = Stroke(
                width = 8.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}