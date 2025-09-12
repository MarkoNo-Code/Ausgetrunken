package com.ausgetrunken.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ausgetrunken.R
import com.ausgetrunken.data.local.entities.UserType
import org.koin.androidx.compose.koinViewModel
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path

@Composable
fun AuthScreen(
    onNavigateToWineyardList: () -> Unit,
    onNavigateToProfile: () -> Unit,
    initialEmail: String? = null,
    resetToken: String? = null,
    viewModel: AuthViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    LaunchedEffect(uiState.isLoginSuccessful, uiState.userType) {
        println("🔍 AuthScreen: LaunchedEffect triggered - isLoginSuccessful: ${uiState.isLoginSuccessful}, userType: ${uiState.userType}")
        if (uiState.isLoginSuccessful && uiState.userType != null) {
            println("🚀 AuthScreen: Navigating based on user type: ${uiState.userType}")
            when (uiState.userType) {
                UserType.CUSTOMER -> {
                    println("🚀 AuthScreen: Navigating to WineyardList (CUSTOMER)")
                    onNavigateToWineyardList()
                }
                UserType.WINEYARD_OWNER -> {
                    println("🚀 AuthScreen: Navigating to Profile (WINEYARD_OWNER)")
                    onNavigateToProfile()
                }
                null -> {
                    println("⚠️ AuthScreen: Null user type, no navigation")
                } // Handle null case
            }
        } else {
            println("⚠️ AuthScreen: No navigation - isLoginSuccessful: ${uiState.isLoginSuccessful}, userType: ${uiState.userType}")
        }
    }
    
    LaunchedEffect(initialEmail) {
        initialEmail?.let { email ->
            viewModel.setInitialEmail(email)
        }
    }
    
    LaunchedEffect(resetToken) {
        resetToken?.let { token ->
            // Show a reset password form or handle the token appropriately
            // For now, we'll show a success message that the user can set a new password
            println("🔑 AuthScreen: Reset token received: $token")
            // Switch to a password reset confirmation mode
            viewModel.setResetToken(token)
        }
    }
    
    // Remove old flaggedAccountMessage LaunchedEffect - now handled in ViewModel
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { success ->
            snackbarHostState.showSnackbar(success)
            viewModel.clearSuccess()
        }
    }
    
    // REMOVED: Don't show splash loading screen in AuthScreen anymore
    // Session checking is done in SplashScreen - AuthScreen should always show login form
    // if (uiState.isCheckingSession) {
    //     SplashLoadingScreen()
    //     return
    // }
    
    // Flagged Account Dialog
    if (uiState.showFlaggedAccountDialog && uiState.flaggedAccountMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFlaggedAccountDialog() },
            title = {
                Text(
                    text = stringResource(R.string.account_flagged_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = uiState.flaggedAccountMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Open email app to contact support
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:ausgetrunken@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Account Flagged for Deletion - Support Request")
                            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.account_flagged_message))
                        }
                        try {
                            context.startActivity(emailIntent)
                        } catch (e: Exception) {
                            // If no email app is available, could show a toast or fallback
                        }
                        viewModel.dismissFlaggedAccountDialog()
                    }
                ) {
                    Text(stringResource(R.string.contact_support))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissFlaggedAccountDialog() }
                ) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.welcome_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = when (uiState.mode) {
                        AuthMode.LOGIN -> stringResource(R.string.sign_in_subtitle)
                        AuthMode.REGISTER -> stringResource(R.string.sign_up_subtitle)
                        AuthMode.FORGOT_PASSWORD -> stringResource(R.string.forgot_password_subtitle)
                        AuthMode.RESET_PASSWORD_CONFIRM -> "Enter your new password"
                    },
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Mode Toggle Buttons - Only show for LOGIN and REGISTER modes
                if (uiState.mode == AuthMode.LOGIN || uiState.mode == AuthMode.REGISTER) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.switchMode(AuthMode.LOGIN) },
                            modifier = Modifier.weight(1f),
                            colors = if (uiState.mode == AuthMode.LOGIN) {
                                androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(stringResource(R.string.sign_in))
                        }
                        
                        OutlinedButton(
                            onClick = { viewModel.switchMode(AuthMode.REGISTER) },
                            modifier = Modifier.weight(1f),
                            colors = if (uiState.mode == AuthMode.REGISTER) {
                                androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(stringResource(R.string.sign_up))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Email Field - Only show for LOGIN, REGISTER, and FORGOT_PASSWORD modes
                if (uiState.mode != AuthMode.RESET_PASSWORD_CONFIRM) {
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { newValue ->
                            // Filter out newlines, tabs, and spaces for email
                            val cleanEmail = newValue
                                .replace("\n", "")
                                .replace("\t", "")
                                .replace(" ", "")
                            viewModel.updateEmail(cleanEmail)
                        },
                        label = { Text(stringResource(R.string.email)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = stringResource(R.string.email)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentType = ContentType.EmailAddress
                            },
                        enabled = !uiState.isLoading,
                        singleLine = true
                    )
                }
                
                // Password Field - Show for LOGIN, REGISTER, and RESET_PASSWORD_CONFIRM modes
                if (uiState.mode == AuthMode.LOGIN || uiState.mode == AuthMode.REGISTER || uiState.mode == AuthMode.RESET_PASSWORD_CONFIRM) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { newValue ->
                        // Filter out newlines and tabs from password
                        val cleanPassword = newValue
                            .replace("\n", "")
                            .replace("\t", "")
                        viewModel.updatePassword(cleanPassword)
                    },
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = stringResource(R.string.password)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible }
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff 
                                else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) stringResource(R.string.cd_hide_password) 
                                else stringResource(R.string.cd_show_password)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None 
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentType = ContentType.Password
                        },
                    enabled = !uiState.isLoading,
                    singleLine = true
                    )
                }
                
                // Confirm Password Field (for registration and password reset confirmation)
                AnimatedVisibility(
                    visible = uiState.mode == AuthMode.REGISTER || uiState.mode == AuthMode.RESET_PASSWORD_CONFIRM,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = uiState.confirmPassword,
                            onValueChange = { newValue ->
                                // Filter out newlines and tabs from confirm password
                                val cleanPassword = newValue
                                    .replace("\n", "")
                                    .replace("\t", "")
                                viewModel.updateConfirmPassword(cleanPassword)
                            },
                            label = { Text(stringResource(R.string.confirm_password)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = stringResource(R.string.confirm_password)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { confirmPasswordVisible = !confirmPasswordVisible }
                                ) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff 
                                        else Icons.Default.Visibility,
                                        contentDescription = if (confirmPasswordVisible) stringResource(R.string.cd_hide_password) 
                                        else stringResource(R.string.cd_show_password)
                                    )
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None 
                            else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentType = ContentType.NewPassword
                                },
                            enabled = !uiState.isLoading,
                            singleLine = true
                        )
                    }
                }
                
                // User Type Selection (only for registration)
                AnimatedVisibility(
                    visible = uiState.mode == AuthMode.REGISTER,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.account_type),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = uiState.selectedUserType == UserType.CUSTOMER,
                                            onClick = { viewModel.updateUserType(UserType.CUSTOMER) },
                                            enabled = !uiState.isLoading
                                        )
                                        Text(
                                            text = stringResource(R.string.customer),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = uiState.selectedUserType == UserType.WINEYARD_OWNER,
                                            onClick = { viewModel.updateUserType(UserType.WINEYARD_OWNER) },
                                            enabled = !uiState.isLoading
                                        )
                                        Text(
                                            text = stringResource(R.string.wineyard_owner),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Main Action Button
                Button(
                    onClick = {
                        when (uiState.mode) {
                            AuthMode.LOGIN -> viewModel.login()
                            AuthMode.REGISTER -> viewModel.register()
                            AuthMode.FORGOT_PASSWORD -> viewModel.resetPassword()
                            AuthMode.RESET_PASSWORD_CONFIRM -> viewModel.confirmPasswordReset()
                        }
                    },
                    enabled = !uiState.isLoading && 
                            when (uiState.mode) {
                                AuthMode.FORGOT_PASSWORD -> uiState.email.isNotBlank()
                                AuthMode.RESET_PASSWORD_CONFIRM -> uiState.password.isNotBlank() && uiState.confirmPassword.isNotBlank()
                                AuthMode.LOGIN -> uiState.email.isNotBlank() && uiState.password.isNotBlank()
                                AuthMode.REGISTER -> uiState.email.isNotBlank() && uiState.password.isNotBlank() && uiState.confirmPassword.isNotBlank()
                            },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = when (uiState.mode) {
                                AuthMode.LOGIN -> stringResource(R.string.sign_in)
                                AuthMode.REGISTER -> stringResource(R.string.create_account)
                                AuthMode.FORGOT_PASSWORD -> stringResource(R.string.reset_password)
                                AuthMode.RESET_PASSWORD_CONFIRM -> "Update Password"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Forgot Password Link (only show in LOGIN mode)
                if (uiState.mode == AuthMode.LOGIN) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = { viewModel.switchMode(AuthMode.FORGOT_PASSWORD) }
                    ) {
                        Text(
                            text = stringResource(R.string.forgot_password),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Back to Login Button (only show in FORGOT_PASSWORD mode)
                if (uiState.mode == AuthMode.FORGOT_PASSWORD) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = { viewModel.switchMode(AuthMode.LOGIN) }
                    ) {
                        Text(
                            text = stringResource(R.string.back_to_login),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashLoadingScreen() {
    // Animation states - trigger wine filling animation on load
    var startAnimation by remember { mutableStateOf(false) }
    
    val wineProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 2500,
            easing = EaseInOutCubic
        ),
        label = "wine_fill"
    )
    
    // Start the wine filling animation when component loads
    LaunchedEffect(Unit) {
        startAnimation = true
    }
    
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
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
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
            // Wine Glass Animation (restored!)
            WineGlassAnimation(
                progress = wineProgress,
                glassAlpha = glassAlpha,
                shimmerOffset = shimmerOffset,
                modifier = Modifier.size(200.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // App Title
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD4AF37), // Gold color
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tagline
            Text(
                text = stringResource(R.string.app_tagline),
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFFB8860B).copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Loading text
            Text(
                text = stringResource(R.string.loading_message),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.alpha(textAlpha)
            )
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