package com.ausgetrunken.ui.wineyard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ausgetrunken.data.local.entities.WineType
import com.ausgetrunken.ui.wineyard.WineFormData

@Composable
fun WineFormCard(
    wine: WineFormData,
    onWineChanged: (WineFormData) -> Unit,
    onRemoveWine: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (wine.name.isNotBlank()) wine.name else "New Wine",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onRemoveWine,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove Wine",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Basic Info (Always Visible)
            if (!isExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = wine.name,
                        onValueChange = { onWineChanged(wine.copy(name = it)) },
                        label = { Text("Wine Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            // Expanded Form
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Basic Information
                    OutlinedTextField(
                        value = wine.name,
                        onValueChange = { onWineChanged(wine.copy(name = it)) },
                        label = { Text("Wine Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = wine.description,
                        onValueChange = { onWineChanged(wine.copy(description = it)) },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )

                    // Wine Type Dropdown
                    WineTypeDropdown(
                        selectedType = wine.wineType,
                        onTypeSelected = { onWineChanged(wine.copy(wineType = it)) }
                    )

                    // Vintage and Price Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = wine.vintage,
                            onValueChange = { onWineChanged(wine.copy(vintage = it)) },
                            label = { Text("Vintage") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = wine.price,
                            onValueChange = { onWineChanged(wine.copy(price = it)) },
                            label = { Text("Price (€)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    // Discounted Price and Stock Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = wine.discountedPrice,
                            onValueChange = { onWineChanged(wine.copy(discountedPrice = it)) },
                            label = { Text("Sale Price (€)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = wine.stockQuantity,
                            onValueChange = { onWineChanged(wine.copy(stockQuantity = it)) },
                            label = { Text("Stock Qty") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    // Low Stock Threshold
                    OutlinedTextField(
                        value = wine.lowStockThreshold,
                        onValueChange = { onWineChanged(wine.copy(lowStockThreshold = it)) },
                        label = { Text("Low Stock Alert") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        supportingText = { Text("Get notified when stock falls below this number") }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WineTypeDropdown(
    selectedType: WineType,
    onTypeSelected: (WineType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedType.name.lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = { },
            readOnly = true,
            label = { Text("Wine Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            WineType.values().forEach { wineType ->
                DropdownMenuItem(
                    text = {
                        Text(wineType.name.lowercase().replaceFirstChar { it.uppercase() })
                    },
                    onClick = {
                        onTypeSelected(wineType)
                        expanded = false
                    }
                )
            }
        }
    }
}