package com.ausgetrunken.ui.customer.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.ui.components.WineItemCard

/**
 * Customer-specific wine card wrapper
 *
 * Provides a consistent interface while using the reusable WineItemCard component
 */
@Composable
fun CustomerWineCard(
    wine: WineEntity,
    onWineClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    WineItemCard(
        wineName = wine.name,
        wineType = wine.wineType,
        vintage = wine.vintage,
        price = wine.price,
        discountedPrice = wine.discountedPrice,
        description = wine.description.takeIf { it.isNotBlank() },
        stockQuantity = wine.stockQuantity,
        lowStockThreshold = wine.lowStockThreshold,
        onClick = { onWineClick(wine.id) },
        modifier = modifier
    )
}