package com.ausgetrunken.ui.wines

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ausgetrunken.data.local.entities.WineType
import com.ausgetrunken.ui.theme.WineGlassIcon
import org.koin.androidx.compose.koinViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWineScreen(
    wineId: String,
    onNavigateBack: () -> Unit,
    onNavigateBackWithSuccess: (String) -> Unit,
    onNavigateToWineDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditWineViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(wineId) {
        viewModel.loadWine(wineId)
    }
    
    
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.NavigateBack -> {
                    onNavigateBack()
                }
                is NavigationEvent.NavigateBackWithWineId -> {
                    onNavigateBackWithSuccess(event.wineId)
                }
                is NavigationEvent.NavigateToWineDetail -> {
                    onNavigateToWineDetail(event.wineId)
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Wine") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // View as Customer button
                    IconButton(
                        onClick = { onNavigateToWineDetail(wineId) }
                    ) {
                        Icon(
                            Icons.Default.Visibility, 
                            contentDescription = "View as Customer",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Clean floating Update Wine button
            Button(
                onClick = {
                    println("🔥 EditWineScreen: Update Wine button clicked!")
                    viewModel.updateWine()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = uiState.canSubmit && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Update Wine")
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (uiState.isLoading && !uiState.isDataLoaded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Wine Photos Section (at the top)
                val winePhotos by viewModel.winePhotos.collectAsState()
                WinePhotosManagementSection(
                    photos = winePhotos,
                    canEdit = true,
                    onAddPhoto = viewModel::addPhoto,
                    onRemovePhoto = viewModel::removePhoto
                )

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
                
                // Discounted Price
                OutlinedTextField(
                    value = uiState.discountedPrice,
                    onValueChange = viewModel::updateDiscountedPrice,
                    label = { Text("Discounted Price (€) - Optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.discountedPriceError != null,
                    supportingText = uiState.discountedPriceError?.let { { Text(it) } }
                )
                
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
                
                // Low Stock Threshold
                OutlinedTextField(
                    value = uiState.lowStockThreshold,
                    onValueChange = viewModel::updateLowStockThreshold,
                    label = { Text("Low Stock Threshold") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.lowStockThresholdError != null,
                    supportingText = uiState.lowStockThresholdError?.let { { Text(it) } }
                )

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
}