package com.ausgetrunken.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ausgetrunken.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(Unit) {
        viewModel.loadSubscriptions()
    }
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_subscriptions)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
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
                        .align(Alignment.Center)
                )
            } else if (uiState.subscriptions.isEmpty()) {
                EmptySubscriptionsMessage(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.subscribed_to_wineyards, uiState.subscriptions.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    items(
                        items = uiState.subscriptions,
                        key = { it.id }
                    ) { subscription ->
                        SubscriptionCard(
                            subscription = subscription,
                            onUnsubscribe = { viewModel.unsubscribe(subscription.wineyardId) },
                            onUpdatePreferences = { lowStock, newRelease, specialOffer, general ->
                                viewModel.updateNotificationPreferences(
                                    subscription.wineyardId,
                                    lowStock,
                                    newRelease,
                                    specialOffer,
                                    general
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySubscriptionsMessage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.NotificationsOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.no_subscriptions_yet),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.subscribe_to_wineyards_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionCard(
    subscription: SubscriptionWithWineyard,
    onUnsubscribe: () -> Unit,
    onUpdatePreferences: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.wineyardName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = stringResource(R.string.subscribed_since, subscription.formattedDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row {
                    IconButton(
                        onClick = { showSettings = !showSettings }
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_notification_settings)
                        )
                    }
                }
            }
            
            if (showSettings) {
                Spacer(modifier = Modifier.height(16.dp))
                
                NotificationPreferences(
                    lowStock = subscription.lowStockNotifications,
                    newRelease = subscription.newReleaseNotifications,
                    specialOffer = subscription.specialOfferNotifications,
                    general = subscription.generalNotifications,
                    onPreferencesChanged = onUpdatePreferences
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onUnsubscribe,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.unsubscribe_from_wineyard, subscription.wineyardName))
                }
            }
        }
    }
}

@Composable
private fun NotificationPreferences(
    lowStock: Boolean,
    newRelease: Boolean,
    specialOffer: Boolean,
    general: Boolean,
    onPreferencesChanged: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var lowStockEnabled by remember { mutableStateOf(lowStock) }
    var newReleaseEnabled by remember { mutableStateOf(newRelease) }
    var specialOfferEnabled by remember { mutableStateOf(specialOffer) }
    var generalEnabled by remember { mutableStateOf(general) }
    
    LaunchedEffect(lowStockEnabled, newReleaseEnabled, specialOfferEnabled, generalEnabled) {
        onPreferencesChanged(lowStockEnabled, newReleaseEnabled, specialOfferEnabled, generalEnabled)
    }
    
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.notification_preferences_header),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        NotificationToggle(
            text = stringResource(R.string.low_stock_alerts),
            description = stringResource(R.string.low_stock_alerts_desc),
            checked = lowStockEnabled,
            onCheckedChange = { lowStockEnabled = it }
        )
        
        NotificationToggle(
            text = stringResource(R.string.new_releases),
            description = stringResource(R.string.new_releases_desc),
            checked = newReleaseEnabled,
            onCheckedChange = { newReleaseEnabled = it }
        )
        
        NotificationToggle(
            text = stringResource(R.string.special_offers),
            description = stringResource(R.string.special_offers_desc),
            checked = specialOfferEnabled,
            onCheckedChange = { specialOfferEnabled = it }
        )
        
        NotificationToggle(
            text = stringResource(R.string.general_updates),
            description = stringResource(R.string.general_updates_desc),
            checked = generalEnabled,
            onCheckedChange = { generalEnabled = it }
        )
    }
}

@Composable
private fun NotificationToggle(
    text: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}