package com.ausgetrunken.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val pullToRefreshState = rememberPullToRefreshState()
    
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            
            // Content with pull-to-refresh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
            ) {
                when (uiState.currentTab) {
                    CustomerTab.WINEYARDS -> {
                        WineyardsList(
                            wineyards = uiState.wineyards,
                            isLoading = uiState.isLoading,
                            hasMore = uiState.hasMoreWineyards,
                            onWineyardClick = onWineyardClick,
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
                
                PullToRefreshContainer(
                    state = pullToRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
    
    // Handle pull-to-refresh
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            viewModel.refreshData()
        }
    }
    
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && pullToRefreshState.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }
}

@Composable
private fun WineyardsList(
    wineyards: List<com.ausgetrunken.data.local.entities.WineyardEntity>,
    isLoading: Boolean,
    @Suppress("UNUSED_PARAMETER") hasMore: Boolean,
    onWineyardClick: (String) -> Unit,
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
                onWineyardClick = onWineyardClick
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