package com.ausgetrunken.ui.wines

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ausgetrunken.R
import com.ausgetrunken.data.local.entities.WineEntity
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageWinesScreen(
    wineyardId: String,
    onNavigateBack: () -> Unit,
    onNavigateToWineDetail: (String) -> Unit,
    onNavigateToAddWine: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageWinesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    LaunchedEffect(wineyardId) {
        viewModel.loadWines(wineyardId)
    }
    
    // Refresh wines when screen becomes visible (e.g., returning from AddWine)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshWines()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    LaunchedEffect(uiState.isRefreshing) {
        if (uiState.isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }
    
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            viewModel.refreshWines()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Wines") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.wines.size < 20) {
                FloatingActionButton(
                    onClick = { onNavigateToAddWine(wineyardId) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Wine")
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Fixed explanation section
            WineManagementExplanation(
                wineCount = uiState.wines.size,
                maxWines = 20
            )
            
            // Scrollable wine list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
            
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.wines.isEmpty()) {
                item {
                    EmptyWineList()
                }
            } else {
                items(
                    items = uiState.wines,
                    key = { wine -> wine.id }
                ) { wine ->
                    AnimatedVisibility(
                        visible = !uiState.deletingWineIds.contains(wine.id),
                        enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
                    ) {
                        WineItem(
                            wine = wine,
                            onWineClick = { onNavigateToWineDetail(wine.id) },
                            onDeleteClick = { viewModel.deleteWine(wine.id) },
                            isDeleting = uiState.deletingWineIds.contains(wine.id)
                        )
                    }
                    }
                }
            }
            }
            
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
    
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Show error snackbar
        }
    }
}

@Composable
private fun WineManagementExplanation(
    wineCount: Int,
    maxWines: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Wine Management",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Here you can manage all wines for this wineyard. You can add new wines, view details, edit existing ones, or remove wines you no longer offer.",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Current wines: $wineCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Maximum: $maxWines",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (wineCount >= maxWines) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            
            if (wineCount >= maxWines) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You have reached the maximum number of wines for this wineyard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun EmptyWineList() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No wines yet",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Add your first wine to start managing your collection",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WineItem(
    wine: WineEntity,
    onWineClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isDeleting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onWineClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = wine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "${wine.wineType} • ${wine.vintage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (wine.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = wine.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "€${wine.price}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (wine.discountedPrice != null && wine.discountedPrice < wine.price) {
                        Text(
                            text = "€${wine.discountedPrice}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stock: ${wine.stockQuantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (wine.stockQuantity <= wine.lowStockThreshold) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    TextButton(
                        onClick = onDeleteClick
                    ) {
                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}