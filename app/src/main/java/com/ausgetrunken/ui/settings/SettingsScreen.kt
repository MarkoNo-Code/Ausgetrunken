package com.ausgetrunken.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ausgetrunken.R
import com.ausgetrunken.ui.common.DeleteAccountDialog
import com.ausgetrunken.ui.profile.OwnerProfileViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToNotificationManagement: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onLogoutSuccess: () -> Unit,
    navController: NavController
) {
    val viewModel: OwnerProfileViewModel = koinInject()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(uiState.logoutSuccess) {
        if (uiState.logoutSuccess) {
            onLogoutSuccess()
        }
    }
    
    LaunchedEffect(uiState.deleteAccountSuccess) {
        if (uiState.deleteAccountSuccess) {
            onLogoutSuccess()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Edit Account Name (simplified)
                EditAccountNameCard(
                    userName = uiState.userName,
                    onSaveClick = { newName -> viewModel.updateUserName(newName) },
                    isUpdating = uiState.isUpdatingName,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                // Edit Email Address
                EditEmailCard(
                    userEmail = uiState.userEmail,
                    onSaveClick = { newEmail -> viewModel.updateUserEmail(newEmail) },
                    isUpdating = uiState.isUpdatingEmail,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                // Logout Button
                Button(
                    onClick = { viewModel.logout() },
                    enabled = !uiState.isLoggingOut,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    if (uiState.isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.signing_out))
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.logout),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.logout))
                    }
                }
            }
            
            item {
                // Danger Zone Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Danger Zone",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.danger_zone),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        Text(
                            text = stringResource(R.string.once_you_delete_account),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        OutlinedButton(
                            onClick = { viewModel.showDeleteAccountDialog() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            enabled = !uiState.isDeletingAccount
                        ) {
                            if (uiState.isDeletingAccount) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.flagging_account))
                            } else {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_delete_account),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.flag_account_for_deletion))
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Delete Account Dialog
    if (uiState.showDeleteAccountDialog) {
        DeleteAccountDialog(
            onDismiss = { viewModel.hideDeleteAccountDialog() },
            onConfirm = { viewModel.deleteAccount() }
        )
    }
}

@Composable
private fun EditAccountNameCard(
    userName: String,
    onSaveClick: (String) -> Unit,
    isUpdating: Boolean,
    modifier: Modifier = Modifier
) {
    var editedName by remember(userName) { mutableStateOf(userName) }
    val hasChanges = editedName.trim() != userName.trim() && editedName.isNotBlank()

    Column(
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.account_name),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = editedName,
                onValueChange = { newValue ->
                    editedName = newValue.replace("\n", "").take(50) // Limit length
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                singleLine = true,
                enabled = !isUpdating,
                shape = RoundedCornerShape(8.dp)
            )

            Button(
                onClick = {
                    if (editedName.isNotBlank() && editedName.trim() != userName.trim()) {
                        onSaveClick(editedName.trim())
                    }
                },
                enabled = hasChanges && !isUpdating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(56.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.save),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EditEmailCard(
    userEmail: String,
    onSaveClick: (String) -> Unit,
    isUpdating: Boolean,
    modifier: Modifier = Modifier
) {
    var editedEmail by remember(userEmail) { mutableStateOf(userEmail) }
    val hasChanges = editedEmail.trim() != userEmail.trim() && editedEmail.isNotBlank()

    Column(
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.email_address),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = editedEmail,
                onValueChange = { newValue ->
                    editedEmail = newValue.replace("\n", "").trim()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                singleLine = true,
                enabled = !isUpdating,
                shape = RoundedCornerShape(8.dp)
            )

            Button(
                onClick = {
                    if (editedEmail.isNotBlank() && editedEmail.trim() != userEmail.trim()) {
                        onSaveClick(editedEmail.trim())
                    }
                },
                enabled = hasChanges && !isUpdating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(56.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.save),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}