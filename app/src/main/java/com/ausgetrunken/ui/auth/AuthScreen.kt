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
import com.ausgetrunken.data.local.entities.UserType
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(
    onNavigateToWineyardList: () -> Unit,
    onNavigateToProfile: () -> Unit,
    initialEmail: String? = null,
    viewModel: AuthViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState.isLoginSuccessful, uiState.userType) {
        if (uiState.isLoginSuccessful && uiState.userType != null) {
            when (uiState.userType) {
                UserType.CUSTOMER -> onNavigateToWineyardList()
                UserType.WINEYARD_OWNER -> onNavigateToProfile()
                null -> {} // Handle null case
            }
        }
    }
    
    LaunchedEffect(initialEmail) {
        initialEmail?.let { email ->
            viewModel.setInitialEmail(email)
        }
    }
    
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
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(50.dp)
                        .align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Welcome to Ausgetrunken",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (uiState.mode == AuthMode.LOGIN) "Sign in to continue" else "Create your account",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Mode Toggle Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.switchMode(AuthMode.LOGIN) },
                        modifier = Modifier.weight(1f),
                        colors = if (uiState.mode == AuthMode.LOGIN) {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text("Sign In")
                    }
                    
                    OutlinedButton(
                        onClick = { viewModel.switchMode(AuthMode.REGISTER) },
                        modifier = Modifier.weight(1f),
                        colors = if (uiState.mode == AuthMode.REGISTER) {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text("Sign Up")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Email Field
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
                    label = { Text("Email") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = "Email"
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Password Field
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Password"
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible }
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff 
                                else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" 
                                else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None 
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                )
                
                // Confirm Password Field (only for registration)
                AnimatedVisibility(
                    visible = uiState.mode == AuthMode.REGISTER,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = uiState.confirmPassword,
                            onValueChange = viewModel::updateConfirmPassword,
                            label = { Text("Confirm Password") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Confirm Password"
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { confirmPasswordVisible = !confirmPasswordVisible }
                                ) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff 
                                        else Icons.Default.Visibility,
                                        contentDescription = if (confirmPasswordVisible) "Hide password" 
                                        else "Show password"
                                    )
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None 
                            else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
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
                                    text = "Account Type",
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
                                            text = "Customer",
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
                                            text = "Wineyard Owner",
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
                        if (uiState.mode == AuthMode.LOGIN) {
                            viewModel.login()
                        } else {
                            viewModel.register()
                        }
                    },
                    enabled = !uiState.isLoading && 
                            uiState.email.isNotBlank() && 
                            uiState.password.isNotBlank() &&
                            (uiState.mode == AuthMode.LOGIN || uiState.confirmPassword.isNotBlank()),
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
                            text = if (uiState.mode == AuthMode.LOGIN) "Sign In" else "Create Account",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}