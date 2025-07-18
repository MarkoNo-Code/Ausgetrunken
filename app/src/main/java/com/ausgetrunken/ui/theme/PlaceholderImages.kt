package com.ausgetrunken.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ausgetrunken.data.local.entities.WineType

@Composable
fun UserPlaceholderIcon(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size * 0.7f)
        ) {
            drawUserIconSVG(
                size = this.size,
                color = Color(0xFF666666) // Use a fixed color instead of theme color
            )
        }
    }
}

private fun DrawScope.drawUserIconSVG(size: Size, color: androidx.compose.ui.graphics.Color) {
    // Simplified version of your SVG user icon
    val paint = Paint().apply {
        this.color = color
        style = PaintingStyle.Fill
    }
    
    val scaleFactor = minOf(size.width / 1024f, size.height / 1024f)
    
    // Draw the head (circle)
    val headRadius = 120f * scaleFactor
    val headCenter = Offset(size.width / 2f, size.height * 0.35f)
    
    drawCircle(
        color = color,
        radius = headRadius,
        center = headCenter
    )
    
    // Draw the body (rounded rectangle)
    val bodyWidth = 200f * scaleFactor
    val bodyHeight = 180f * scaleFactor
    val bodyTop = size.height * 0.55f
    
    drawRoundRect(
        color = color,
        topLeft = Offset(
            (size.width - bodyWidth) / 2f,
            bodyTop
        ),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f * scaleFactor)
    )
}

@Composable
fun WineyardPlaceholderImage(
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f
) {
    Card(
        modifier = modifier.aspectRatio(aspectRatio),
        shape = RoundedCornerShape(12.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF87CEEB), // Sky blue
                            Color(0xFFDDA0DD), // Plum for sunset
                            Color(0xFF228B22)  // Forest green
                        )
                    )
                )
        ) {
            drawWineyardScene(size)
        }
    }
}

private fun DrawScope.drawWineyardScene(size: Size) {
    // Draw rolling hills
    val hillPath = Path()
    hillPath.moveTo(0f, size.height * 0.6f)
    hillPath.quadraticBezierTo(
        size.width * 0.3f, size.height * 0.5f,
        size.width * 0.6f, size.height * 0.55f
    )
    hillPath.quadraticBezierTo(
        size.width * 0.8f, size.height * 0.6f,
        size.width, size.height * 0.65f
    )
    hillPath.lineTo(size.width, size.height)
    hillPath.lineTo(0f, size.height)
    hillPath.close()
    
    drawPath(
        path = hillPath,
        color = Color(0xFF32CD32) // Lime green
    )
    
    // Draw vineyard rows
    for (i in 0..4) {
        val y = size.height * (0.65f + i * 0.05f)
        val startX = size.width * 0.1f
        val endX = size.width * 0.9f
        
        drawLine(
            color = Color(0xFF228B22),
            start = Offset(startX, y),
            end = Offset(endX, y),
            strokeWidth = 3.dp.toPx()
        )
        
        // Draw grape vines
        for (j in 0..8) {
            val x = startX + (endX - startX) * j / 8f
            drawCircle(
                color = Color(0xFF800080), // Purple grapes
                radius = 2.dp.toPx(),
                center = Offset(x, y - 8.dp.toPx())
            )
        }
    }
    
    // Draw a simple winery building
    val buildingWidth = size.width * 0.15f
    val buildingHeight = size.height * 0.2f
    val buildingX = size.width * 0.75f
    val buildingY = size.height * 0.45f
    
    drawRect(
        color = Color(0xFF8B4513), // Saddle brown
        topLeft = Offset(buildingX, buildingY),
        size = Size(buildingWidth, buildingHeight)
    )
    
    // Roof
    val roofPath = Path()
    roofPath.moveTo(buildingX - buildingWidth * 0.1f, buildingY)
    roofPath.lineTo(buildingX + buildingWidth * 0.5f, buildingY - buildingHeight * 0.3f)
    roofPath.lineTo(buildingX + buildingWidth * 1.1f, buildingY)
    roofPath.close()
    
    drawPath(
        path = roofPath,
        color = Color(0xFFDC143C) // Crimson roof
    )
}

@Composable
fun WineBottlePlaceholder(
    wineType: WineType,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    val bottleColor = when (wineType) {
        WineType.RED -> RedWineBottle
        WineType.WHITE -> WhiteWineBottle
        WineType.ROSE -> RoseWineBottle
        WineType.SPARKLING -> SparklingWineBottle
        WineType.DESSERT -> Color(0xFFDAA520) // Goldenrod
        WineType.FORTIFIED -> Color(0xFF8B4513) // Saddle brown
    }
    
    val wineColor = when (wineType) {
        WineType.RED -> Color(0xFF8B0000)
        WineType.WHITE -> Color(0xFFFFFFE0)
        WineType.ROSE -> Color(0xFFFFB6C1)
        WineType.SPARKLING -> Color(0xFFF0F8FF)
        WineType.DESSERT -> Color(0xFFFFD700)
        WineType.FORTIFIED -> Color(0xFFB8860B)
    }
    
    Canvas(
        modifier = modifier.size(size)
    ) {
        drawWineBottle(
            size = this.size,
            bottleColor = bottleColor,
            wineColor = wineColor,
            wineType = wineType
        )
    }
}

private fun DrawScope.drawWineBottle(
    size: Size,
    bottleColor: androidx.compose.ui.graphics.Color,
    wineColor: androidx.compose.ui.graphics.Color,
    wineType: WineType
) {
    val bottleWidth = size.width * 0.6f
    val bottleHeight = size.height * 0.85f
    val centerX = size.width / 2f
    
    // Bottle body
    val bottlePath = Path()
    bottlePath.moveTo(centerX - bottleWidth / 2f, size.height * 0.9f)
    bottlePath.lineTo(centerX - bottleWidth / 2f, size.height * 0.3f)
    bottlePath.quadraticBezierTo(
        centerX - bottleWidth / 2f, size.height * 0.25f,
        centerX - bottleWidth / 3f, size.height * 0.25f
    )
    bottlePath.lineTo(centerX - bottleWidth / 6f, size.height * 0.15f)
    bottlePath.lineTo(centerX + bottleWidth / 6f, size.height * 0.15f)
    bottlePath.lineTo(centerX + bottleWidth / 3f, size.height * 0.25f)
    bottlePath.quadraticBezierTo(
        centerX + bottleWidth / 2f, size.height * 0.25f,
        centerX + bottleWidth / 2f, size.height * 0.3f
    )
    bottlePath.lineTo(centerX + bottleWidth / 2f, size.height * 0.9f)
    bottlePath.close()
    
    // Draw bottle
    drawPath(
        path = bottlePath,
        color = bottleColor
    )
    
    // Draw wine inside bottle
    val winePath = Path()
    val wineLevel = size.height * 0.75f
    winePath.moveTo(centerX - bottleWidth / 2f + 4.dp.toPx(), size.height * 0.9f)
    winePath.lineTo(centerX - bottleWidth / 2f + 4.dp.toPx(), wineLevel)
    winePath.lineTo(centerX + bottleWidth / 2f - 4.dp.toPx(), wineLevel)
    winePath.lineTo(centerX + bottleWidth / 2f - 4.dp.toPx(), size.height * 0.9f)
    winePath.close()
    
    drawPath(
        path = winePath,
        color = wineColor
    )
    
    // Draw bottle neck
    drawRect(
        color = bottleColor,
        topLeft = Offset(centerX - bottleWidth / 6f, size.height * 0.05f),
        size = Size(bottleWidth / 3f, size.height * 0.1f)
    )
    
    // Draw cork/cap
    val capColor = if (wineType == WineType.SPARKLING) {
        Color(0xFFFFD700) // Gold foil for sparkling
    } else {
        Color(0xFF8B4513) // Cork brown
    }
    
    drawRect(
        color = capColor,
        topLeft = Offset(centerX - bottleWidth / 8f, size.height * 0.02f),
        size = Size(bottleWidth / 4f, size.height * 0.06f)
    )
    
    // Add label
    drawRoundRect(
        color = Color.White.copy(alpha = 0.9f),
        topLeft = Offset(centerX - bottleWidth / 3f, size.height * 0.4f),
        size = Size(bottleWidth * 2f / 3f, size.height * 0.2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    )
    
    // Label outline
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.3f),
        topLeft = Offset(centerX - bottleWidth / 3f, size.height * 0.4f),
        size = Size(bottleWidth * 2f / 3f, size.height * 0.2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        style = Stroke(width = 1.dp.toPx())
    )
    
    // Add sparkles for sparkling wine
    if (wineType == WineType.SPARKLING) {
        for (i in 0..5) {
            val sparkleX = centerX - bottleWidth / 4f + (bottleWidth / 2f * i / 5f)
            val sparkleY = wineLevel - (size.height * 0.1f * (i % 3))
            
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 2.dp.toPx(),
                center = Offset(sparkleX, sparkleY)
            )
        }
    }
}