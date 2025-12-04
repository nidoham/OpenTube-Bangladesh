package com.github.opentube

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.opentube.ui.theme.OpenTubeTheme
import com.github.opentube.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenTubeTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current   // বাংলা কমেন্ট: context উপরে নেয়া হলো

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.youtube_title),
                        contentDescription = "YouTube Logo",
                        modifier = Modifier.size(width = 120.dp, height = 60.dp),
                        contentScale = ContentScale.Crop
                    )
                },

                actions = {
                    IconButton(
                        onClick = {
                            // বাংলা কমেন্ট: Activity ওপেন
                            context.startActivity(
                                Intent(context, SearchActivity::class.java)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { /* TODO */ }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White
                        )
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },

        bottomBar = {
            NavigationBar(containerColor = Color.Black, contentColor = Color.White) {
                val items = listOf(
                    Icons.Default.Home to "Home",
                    Icons.Default.Subscriptions to "Subscription",
                    Icons.Default.Download to "Download",
                    Icons.Default.VideoLibrary to "Library"
                )

                items.forEachIndexed { index, pair ->
                    NavigationBarItem(
                        icon = { Icon(pair.first, contentDescription = pair.second) },
                        label = { Text(pair.second) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0xFF282828)
                        )
                    )
                }
            }
        },

        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            when (selectedTab) {
                0 -> HomeScreenCompose()
                1 -> SubscriptionScreen()
                2 -> DownloadScreen()
                3 -> LibraryScreen()
            }
        }
    }
}