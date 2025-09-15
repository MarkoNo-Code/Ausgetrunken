package com.ausgetrunken.ui.wines

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.ausgetrunken.data.local.entities.WineType
import com.ausgetrunken.ui.theme.WineGlassIcon
import org.koin.androidx.compose.koinViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWineScreen(
    wineyardId: String,
    onNavigateBack: () -> Unit,
    onNavigateBackWithSuccess: (String) -> Unit,
    onNavigateToWineDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddWineViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    
    LaunchedEffect(wineyardId) {
        viewModel.resetState()
        viewModel.setWineyardId(wineyardId)
    }
    
    // Channel-based navigation event handling
    LaunchedEffect(Unit) {
        viewModel.navigationEvents
            .flowWithLifecycle(lifecycle = lifecycle)
            .collect { event ->
                when (event) {
                    is NavigationEvent.NavigateBack -> {
                        println("🔥 AddWineScreen: Channel navigation event received, calling onNavigateBack")
                        onNavigateBack()
                    }
                    is NavigationEvent.NavigateBackWithWineId -> {
                        println("🔥 AddWineScreen: Wine added successfully, calling onNavigateBackWithSuccess with wineId: ${event.wineId}")
                        onNavigateBackWithSuccess(event.wineId)
                    }
                    is NavigationEvent.NavigateToWineDetail -> {
                        println("🔥 AddWineScreen: Navigating to wine detail for wine ID: ${event.wineId}")
                        onNavigateToWineDetail(event.wineId)
                    }
                }
            }
    }
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Add Wine") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                if (uiState.isLoading) {
                    LoadingProgressBar()
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Wine Name
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("Wine Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { { Text(it) } }
            )
            
            // Description
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
            
            // Wine Type
            var wineTypeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = wineTypeExpanded,
                onExpandedChange = { wineTypeExpanded = !wineTypeExpanded }
            ) {
                OutlinedTextField(
                    value = uiState.wineType?.name?.lowercase()?.replaceFirstChar { it.uppercaseChar() } ?: "",
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Wine Type") },
                    leadingIcon = {
                        uiState.wineType?.let { wineType ->
                            WineGlassIcon(
                                wineType = wineType,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wineTypeExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    isError = uiState.wineTypeError != null,
                    supportingText = uiState.wineTypeError?.let { { Text(it) } }
                )
                
                ExposedDropdownMenu(
                    expanded = wineTypeExpanded,
                    onDismissRequest = { wineTypeExpanded = false }
                ) {
                    WineType.values().forEach { wineType ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    WineGlassIcon(
                                        wineType = wineType,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(wineType.name.lowercase().replaceFirstChar { it.uppercaseChar() })
                                }
                            },
                            onClick = {
                                viewModel.updateWineType(wineType)
                                wineTypeExpanded = false
                            }
                        )
                    }
                }
            }
            
            // Vintage
            OutlinedTextField(
                value = uiState.vintage,
                onValueChange = viewModel::updateVintage,
                label = { Text("Vintage Year") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.vintageError != null,
                supportingText = uiState.vintageError?.let { { Text(it) } }
            )
            
            // Price
            OutlinedTextField(
                value = uiState.price,
                onValueChange = viewModel::updatePrice,
                label = { Text("Price (€)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.priceError != null,
                supportingText = uiState.priceError?.let { { Text(it) } }
            )
            
            // Discounted Price field removed - not in current database schema
            
            // Stock Quantity
            OutlinedTextField(
                value = uiState.stockQuantity,
                onValueChange = viewModel::updateStockQuantity,
                label = { Text("Stock Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.stockQuantityError != null,
                supportingText = uiState.stockQuantityError?.let { { Text(it) } }
            )
            
            // Low Stock Threshold field removed - not in current database schema
            
            // Submit Button
            Button(
                onClick = { 
                    println("🔥 AddWineScreen: Button clicked!")
                    viewModel.createWine()
                    // Test navigation immediately
                    println("🔥 AddWineScreen: Testing immediate navigation")
                    // onNavigateBackWithSuccess()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canSubmit && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Add Wine")
                }
            }
            
            // Error Message
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingProgressBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )
    
    val glowColor by animateColorAsState(
        targetValue = Color(0xFF00FF00), // Bright green
        animationSpec = tween(200),
        label = "glow"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.Transparent)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 8.dp,
                    spotColor = glowColor.copy(alpha = 0.8f)
                )
        ) {
            drawProgressBar(
                progress = progress,
                startColor = Color(0xFF32CD32), // Lime green
                endColor = Color(0xFF00FF00)    // Bright green
            )
        }
    }
}

private fun DrawScope.drawProgressBar(
    progress: Float,
    startColor: Color,
    endColor: Color
) {
    val width = size.width
    val height = size.height
    val progressWidth = width * progress
    
    // Draw the progress bar
    drawLine(
        color = startColor,
        start = Offset(0f, height / 2),
        end = Offset(progressWidth, height / 2),
        strokeWidth = height
    )
    
    // Draw the bright tip
    if (progress > 0.1f) {
        drawLine(
            color = endColor,
            start = Offset(progressWidth - (width * 0.1f), height / 2),
            end = Offset(progressWidth, height / 2),
            strokeWidth = height
        )
    }
}