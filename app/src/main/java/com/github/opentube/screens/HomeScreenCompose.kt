package com.github.opentube.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.opentube.screens.items.VideoRecommendationItem

@Composable
fun HomeScreenCompose() {
    // ক্যাটেগরি লিস্ট
    val categories = listOf("Explore", "All", "Gaming", "Sports", "Music", "News")
    var selectedCategory by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // পুরো ব্যাকগ্রাউন্ড কালো
    ) {
        // Horizontal scrollable chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                val isSelected = selectedCategory == category

                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    label = {
                        Text(
                            text = category,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    },
                    shape = RoundedCornerShape(50), // pill shape
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (isSelected) Color.White else Color(0xFF272727),
                        labelColor = if (isSelected) Color.Black else Color.White
                    ),
                    border = null
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Video list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // ভিডিওগুলোর মধ্যে স্পেস
        ) {
            items(20) { index ->
                VideoRecommendationItem(
                    title = "Video #$index title",
                    channelInfo = "Channel Name • 1M views • 1 day ago"
                )
            }
        }
    }
}
