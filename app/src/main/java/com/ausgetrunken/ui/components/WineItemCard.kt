package com.ausgetrunken.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ausgetrunken.data.local.entities.WineType
import com.ausgetrunken.ui.theme.WineGlassIcon
import java.text.NumberFormat
import java.util.Locale

/**
 * Reusable wine item card component used across all wine lists
 *
 * Based on the design from discover wines screen, this provides consistent
 * wine display across customer and owner interfaces
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WineItemCard(
    wineName: String,
    wineType: WineType,
    vintage: Int,
    price: Double,
    discountedPrice: Double? = null,
    description: String? = null,
    stockQuantity: Int,
    lowStockThreshold: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Optional management actions for owner screens
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    showManagementActions: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Wine glass icon with brand consistency
            WineGlassIcon(
                wineType = wineType,
                modifier = Modifier.size(80.dp)
            )

            // Wine details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Wine name
                    Text(
                        text = wineName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Wine type and vintage
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = wineType.name.lowercase().replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            text = vintage.toString(),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Description (if provided)
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Price and stock info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Price
                    Column {
                        discountedPrice?.let { discountPrice ->
                            Text(
                                text = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(price),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough
                            )
                            Text(
                                text = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(discountPrice),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        } ?: run {
                            Text(
                                text = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(price),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Stock indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val stockColor = when {
                            stockQuantity <= lowStockThreshold -> MaterialTheme.colorScheme.error
                            stockQuantity <= lowStockThreshold * 2 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Stock",
                            tint = stockColor,
                            modifier = Modifier.size(16.dp)
                        )

                        Text(
                            text = "$stockQuantity in stock",
                            fontSize = 12.sp,
                            color = stockColor
                        )
                    }
                }

                // Management actions for owner screens
                if (showManagementActions) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        onEditClick?.let { editClick ->
                            TextButton(onClick = editClick) {
                                Text("Edit")
                            }
                        }
                        onDeleteClick?.let { deleteClick ->
                            TextButton(onClick = deleteClick) {
                                Text(
                                    "Delete",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}