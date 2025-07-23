package com.ausgetrunken.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.min
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ausgetrunken.ui.customer.components.CustomerWineyardCard
import com.ausgetrunken.ui.customer.components.CustomerWineCard
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLandingScreen(
    onWineyardClick: (String) -> Unit,
    onWineClick: (String) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CustomerLandingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    // PullToRefreshBox handles its own state internally
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Handle manual refresh trigger
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refreshData()
        }
    }
    
    // End refresh when loading completes
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && isRefreshing) {
            delay(500) // Small delay to show refresh completed
            isRefreshing = false
        }
    }
    
    // Handle loading completion with finished state and smooth dismissal
    var showFinished by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && !showFinished) {
            showFinished = true
            delay(1050) // Show "Finished" for 1.05 seconds
            isDismissing = true // Start smooth dismissal
            delay(400) // Wait for dismissal animation
            showFinished = false
            isDismissing = false
            // Refresh animation handled above
        }
    }
    
    // Reset states when starting new refresh
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            showFinished = false
            isDismissing = false
        }
    }
    
    // Handle error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    // Handle pagination
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val totalItems = when (uiState.currentTab) {
                    CustomerTab.WINEYARDS -> uiState.wineyards.size
                    CustomerTab.WINES -> uiState.wines.size
                }
                
                // Load more when we're near the end
                if (lastVisibleIndex != null && lastVisibleIndex >= totalItems - 2) {
                    viewModel.loadNextPage()
                }
            }
    }
    
    // Refresh subscriptions when screen resumes (e.g., returning from detail screen)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshSubscriptions()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Discover Wines",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = uiState.currentTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Tab(
                    selected = uiState.currentTab == CustomerTab.WINEYARDS,
                    onClick = { viewModel.switchTab(CustomerTab.WINEYARDS) },
                    text = { 
                        Text(
                            text = "Wineyards",
                            fontWeight = if (uiState.currentTab == CustomerTab.WINEYARDS) FontWeight.Bold else FontWeight.Normal
                        ) 
                    }
                )
                Tab(
                    selected = uiState.currentTab == CustomerTab.WINES,
                    onClick = { viewModel.switchTab(CustomerTab.WINES) },
                    text = { 
                        Text(
                            text = "Wines",
                            fontWeight = if (uiState.currentTab == CustomerTab.WINES) FontWeight.Bold else FontWeight.Normal
                        ) 
                    }
                )
            }
            
            // Custom pull-to-refresh accordion
            PullToRefreshAccordion(
                isLoading = uiState.isLoading,
                showFinished = showFinished,
                isDismissing = isDismissing,
                currentTab = uiState.currentTab,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Content with pull-to-refresh (hide default indicator)
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                indicator = { /* Hide default indicator */ }
            ) {
                when (uiState.currentTab) {
                    CustomerTab.WINEYARDS -> {
                        WineyardsList(
                            wineyards = uiState.wineyards,
                            isLoading = uiState.isLoading,
                            hasMore = uiState.hasMoreWineyards,
                            onWineyardClick = onWineyardClick,
                            subscribedWineyardIds = uiState.subscribedWineyardIds,
                            subscriptionLoadingIds = uiState.subscriptionLoadingIds,
                            onSubscriptionToggle = viewModel::toggleWineyardSubscription,
                            listState = listState
                        )
                    }
                    CustomerTab.WINES -> {
                        WinesList(
                            wines = uiState.wines,
                            isLoading = uiState.isLoading,
                            hasMore = uiState.hasMoreWines,
                            onWineClick = onWineClick,
                            listState = listState
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WineyardsList(
    wineyards: List<com.ausgetrunken.data.local.entities.WineyardEntity>,
    isLoading: Boolean,
    @Suppress("UNUSED_PARAMETER") hasMore: Boolean,
    onWineyardClick: (String) -> Unit,
    subscribedWineyardIds: Set<String>,
    subscriptionLoadingIds: Set<String>,
    onSubscriptionToggle: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(wineyards) { wineyard ->
            CustomerWineyardCard(
                wineyard = wineyard,
                onWineyardClick = onWineyardClick,
                isSubscribed = subscribedWineyardIds.contains(wineyard.id),
                isLoading = subscriptionLoadingIds.contains(wineyard.id),
                onSubscriptionToggle = onSubscriptionToggle
            )
        }
        
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        
        if (wineyards.isEmpty() && !isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No wineyards found",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WinesList(
    wines: List<com.ausgetrunken.data.local.entities.WineEntity>,
    isLoading: Boolean,
    @Suppress("UNUSED_PARAMETER") hasMore: Boolean,
    onWineClick: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(wines) { wine ->
            CustomerWineCard(
                wine = wine,
                onWineClick = onWineClick
            )
        }
        
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        
        if (wines.isEmpty() && !isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No wines found",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefreshAccordion(
    isLoading: Boolean,
    showFinished: Boolean,
    isDismissing: Boolean,
    currentTab: CustomerTab,
    modifier: Modifier = Modifier
) {
    // Calculate the accordion height based on state
    val maxHeight = 60.dp
    
    val targetHeight = when {
        isDismissing -> 0.dp // Smooth dismissal to 0
        isLoading || showFinished -> maxHeight
        else -> 0.dp
    }
    
    // Animate height changes smoothly
    val accordionHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(
            durationMillis = if (isDismissing) 500 else 300,
            delayMillis = 0
        ),
        label = "accordionHeight"
    )
    
    // Only show if there's some height
    if (accordionHeight.value > 0) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(accordionHeight)
                .clipToBounds(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    showFinished -> Color(0xFF4CAF50) // Green when finished
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    showFinished -> {
                        // Show finished state
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (currentTab) {
                                    CustomerTab.WINEYARDS -> "Wineyards refreshed"
                                    CustomerTab.WINES -> "Wines refreshed"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                    isLoading -> {
                        // Show loading indicator
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (currentTab) {
                                    CustomerTab.WINEYARDS -> "Refreshing wineyards..."
                                    CustomerTab.WINES -> "Refreshing wines..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingAccordion() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}