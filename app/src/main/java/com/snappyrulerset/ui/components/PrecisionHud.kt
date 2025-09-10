package com.snappyrulerset.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


@Composable
fun PrecisionHud(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        modifier = modifier
            .background(Color(0xAA000000))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}