package com.ausgetrunken.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ausgetrunken.R
import com.ausgetrunken.data.local.entities.NotificationType
import com.ausgetrunken.data.local.entities.WineEntity
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationManagementScreen(
    ownerId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationManagementViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCustomNotificationDialog by remember { mutableStateOf(false) }
    var selectedNotificationType by remember { mutableStateOf(NotificationType.GENERAL) }

    LaunchedEffect(ownerId) {
        viewModel.loadOwnerData(ownerId)
    }

    // Show success/error messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notification_management)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Actions Section
            item {
                QuickActionsSection(
                    lowStockCount = uiState.lowStockWines.size,
                    criticalStockCount = uiState.criticalStockWines.size,
                    onSendLowStockNotifications = { viewModel.sendAllLowStockNotifications() },
                    onSendCriticalStockNotifications = { viewModel.sendAllCriticalStockNotifications() },
                    onSendCustomNotification = { 
                        selectedNotificationType = NotificationType.GENERAL
                        showCustomNotificationDialog = true 
                    },
                    isLoading = uiState.isLoading
                )
            }

            // Low Stock Wines Section
            if (uiState.lowStockWines.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.low_stock_wines),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(uiState.lowStockWines) { wine ->
                    LowStockWineCard(
                        wine = wine,
                        onSendNotification = { viewModel.sendSpecificWineNotification(wine) }
                    )
                }
            }

            // Critical Stock Wines Section
            if (uiState.criticalStockWines.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.critical_stock_wines),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(uiState.criticalStockWines) { wine ->
                    CriticalStockWineCard(
                        wine = wine,
                        onSendNotification = { viewModel.sendCriticalWineNotification(wine) }
                    )
                }
            }

            // Subscriber Info Section
            item {
                SubscriberInfoCard(
                    wineyardSubscriberInfo = uiState.wineyardSubscriberInfo,
                    totalSubscribers = uiState.subscriberCount,
                    totalLowStockSubscribers = uiState.lowStockSubscribers,
                    totalGeneralSubscribers = uiState.generalSubscribers
                )
            }
        }

        // Custom Notification Dialog
        if (showCustomNotificationDialog) {
            CustomNotificationDialog(
                notificationType = selectedNotificationType,
                onNotificationTypeChange = { selectedNotificationType = it },
                onSend = { title, message, type ->
                    viewModel.sendCustomNotification(title, message, type)
                    showCustomNotificationDialog = false
                },
                onDismiss = { showCustomNotificationDialog = false }
            )
        }

        // Loading overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    lowStockCount: Int,
    criticalStockCount: Int,
    onSendLowStockNotifications: () -> Unit,
    onSendCriticalStockNotifications: () -> Unit,
    onSendCustomNotification: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_actions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Send Low Stock Notifications
                Button(
                    onClick = onSendLowStockNotifications,
                    enabled = !isLoading && lowStockCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Text(stringResource(R.string.low_stock_count, lowStockCount), textAlign = TextAlign.Center)
                    }
                }

                // Send Critical Stock Notifications
                Button(
                    onClick = onSendCriticalStockNotifications,
                    enabled = !isLoading && criticalStockCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Text(stringResource(R.string.critical_count, criticalStockCount), textAlign = TextAlign.Center)
                    }
                }
            }

            // Send Custom Notification
            OutlinedButton(
                onClick = onSendCustomNotification,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.send_custom_notification))
            }
        }
    }
}

@Composable
private fun LowStockWineCard(
    wine: WineEntity,
    onSendNotification: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${wine.stockQuantity} bottles remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Threshold: ${wine.lowStockThreshold}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onSendNotification
            ) {
                Text(stringResource(R.string.notify))
            }
        }
    }
}

@Composable
private fun CriticalStockWineCard(
    wine: WineEntity,
    onSendNotification: () -> Unit
) {
    val percentageRemaining = if (wine.fullStockQuantity > 0) {
        (wine.stockQuantity.toFloat() / wine.fullStockQuantity.toFloat() * 100).toInt()
    } else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "${wine.stockQuantity} bottles remaining ($percentageRemaining%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.critical_stock_warning),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onSendNotification,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.alert_now))
            }
        }
    }
}

@Composable
private fun SubscriberInfoCard(
    wineyardSubscriberInfo: List<WineyardSubscriberInfo>,
    totalSubscribers: Int,
    totalLowStockSubscribers: Int,
    totalGeneralSubscribers: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.subscriber_information),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Per-wineyard breakdown
            if (wineyardSubscriberInfo.isNotEmpty()) {
                Text(
                    text = "Subscribers by Wineyard:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                
                wineyardSubscriberInfo.forEach { wineyard ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = wineyard.wineyardName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${wineyard.totalSubscribers}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Low Stock", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${wineyard.lowStockSubscribers}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("General", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${wineyard.generalSubscribers}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // Total summary (only show if owner has multiple wineyards)
                if (wineyardSubscriberInfo.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Total Across All Wineyards:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Subscribers")
                        Text(
                            text = totalSubscribers.toString(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Low Stock Notifications")
                        Text(
                            text = totalLowStockSubscribers.toString(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("General Notifications")
                        Text(
                            text = totalGeneralSubscribers.toString(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // No wineyards with low stock wines
                Text(
                    text = "No low stock wines found, so no subscriber information to display.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun CustomNotificationDialog(
    notificationType: NotificationType,
    onNotificationTypeChange: (NotificationType) -> Unit,
    onSend: (String, String, NotificationType) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.send_custom_notification)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Notification Type Selection
                Text(
                    text = stringResource(R.string.notification_type),
                    style = MaterialTheme.typography.labelMedium
                )
                
                NotificationType.values().forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNotificationTypeChange(type) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = notificationType == type,
                            onClick = { onNotificationTypeChange(type) }
                        )
                        Text(
                            text = type.name.replace('_', ' '),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Message Input
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.message_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(title, message, notificationType) },
                enabled = title.isNotBlank() && message.isNotBlank()
            ) {
                Text(stringResource(R.string.send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}