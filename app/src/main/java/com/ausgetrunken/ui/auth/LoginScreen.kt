package com.ausgetrunken.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun LoginScreen(
    onNavigateToWineyardList: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToRegister: (String) -> Unit,
    initialEmail: String? = null,
    viewModel: LoginViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState.isLoginSuccessful, uiState.userType) {
        println("🔍 LoginScreen: LaunchedEffect triggered - isLoginSuccessful: ${uiState.isLoginSuccessful}, userType: ${uiState.userType}")
        if (uiState.isLoginSuccessful && uiState.userType != null) {
            println("🚀 LoginScreen: Navigating based on user type: ${uiState.userType}")
            when (uiState.userType) {
                UserType.CUSTOMER -> {
                    println("🚀 LoginScreen: Navigating to WineyardList (CUSTOMER)")
                    onNavigateToWineyardList()
                }
                UserType.WINEYARD_OWNER -> {
                    println("🚀 LoginScreen: Navigating to Profile (WINEYARD_OWNER)")
                    onNavigateToProfile()
                }
                null -> {
                    println("⚠️ LoginScreen: Null user type, no navigation")
                } // Handle null case
            }
        } else {
            println("⚠️ LoginScreen: No navigation - isLoginSuccessful: ${uiState.isLoginSuccessful}, userType: ${uiState.userType}")
        }
    }
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(initialEmail) {
        initialEmail?.let { email ->
            if (email.isNotBlank()) {
                viewModel.updateEmail(email)
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
                Text(
                    text = stringResource(R.string.welcome_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.sign_in_subtitle),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Supabase configuration message
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.supabase_backend_message),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = { newValue ->
                        // Filter out newlines, tabs, and trim spaces
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
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
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
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = viewModel::login,
                    enabled = !uiState.isLoading && uiState.email.isNotBlank() && uiState.password.isNotBlank(),
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
                            text = stringResource(R.string.sign_in),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = { onNavigateToRegister(uiState.email) },
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = stringResource(R.string.dont_have_account),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
    }
}