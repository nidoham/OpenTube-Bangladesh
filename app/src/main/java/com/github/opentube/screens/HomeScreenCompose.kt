package com.github.opentube.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.extractor.stream.StreamInfo
import com.github.opentube.PlayerActivity
import com.github.opentube.player.queue.PlayQueue
import com.github.opentube.player.queue.PlayQueueItem
import com.github.opentube.screens.items.VideoRecommendationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class FetchResult(
    val videos: List<StreamInfo>,
    val nextPage: Page?
)

@Composable
fun HomeScreenCompose() {
    val context = LocalContext.current
    val categories = listOf("All", "Gaming", "Sports", "Music", "News", "Coding")
    var selectedCategory by remember { mutableStateOf("All") }

    var videoList by remember { mutableStateOf<List<StreamInfo>>(emptyList()) }
    var nextPageToken by remember { mutableStateOf<Page?>(null) }

    var isInitialLoading by remember { mutableStateOf(false) }
    var isAppendLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun loadData(category: String, page: Page?, isAppend: Boolean) {
        if (isAppend && page == null) return

        scope.launch {
            if (isAppend) isAppendLoading = true else isInitialLoading = true
            errorMessage = null

            try {
                val result = fetchVideos(category, page)

                if (isAppend) {
                    videoList = videoList + result.videos
                } else {
                    videoList = result.videos
                }
                nextPageToken = result.nextPage
            } catch (e: Exception) {
                errorMessage = e.localizedMessage
                e.printStackTrace()
            } finally {
                isInitialLoading = false
                isAppendLoading = false
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        videoList = emptyList()
        nextPageToken = null
        listState.scrollToItem(0)
        loadData(selectedCategory, null, isAppend = false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    label = { Text(text = category, color = if (isSelected) Color.Black else Color.White) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (isSelected) Color.White else Color(0xFF272727),
                        labelColor = if (isSelected) Color.Black else Color.White
                    ),
                    border = null
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isInitialLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null && videoList.isEmpty()) {
                Text(
                    text = "Error: $errorMessage",
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(videoList) { stream ->
                        VideoRecommendationItem(
                            streamInfo = stream,
                            onVideoClick = {
                                val item = PlayQueueItem(
                                    title = stream.title,
                                    channelName = stream.uploaderName,
                                    thumbnailUrl = stream.thumbnails,
                                    videoUrl = stream.videoUrl
                                )

                                val queue = PlayQueue.fromSingleItem(item)

                                val intent = Intent(context, PlayerActivity::class.java).apply {
                                    putExtra("play_queue", queue)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    if (nextPageToken != null || isAppendLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isAppendLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                                } else {
                                    SideEffect { loadData(selectedCategory, nextPageToken, isAppend = true) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatViews(views: Long): String {
    return when {
        views >= 1_000_000 -> String.format("%.1fM", views / 1_000_000.0)
        views >= 1_000 -> String.format("%.1fK", views / 1_000.0)
        else -> views.toString()
    }
}

suspend fun fetchVideos(category: String, page: Page?): FetchResult = withContext(Dispatchers.IO) {
    val service = ServiceList.YouTube

    try {
        val extractor: ListExtractor<*> = if (category == "All" || category == "Explore") {
            val kioskList = service.kioskList
            val kioskId = kioskList.defaultKioskId ?: "Trending"
            kioskList.getExtractorById(kioskId, null)
        } else {
            service.getSearchExtractor(category)
        }

        if (page == null) {
            extractor.fetchPage()
        }

        val resultPage = if (page == null) extractor.initialPage else extractor.getPage(page)

        val mappedVideos = resultPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                val thumbList: List<Image> = item.thumbnails ?: emptyList()
                val avatarList: List<Image> = item.uploaderAvatars ?: emptyList()

                StreamInfo.from(item)
            }

        return@withContext FetchResult(mappedVideos, resultPage.nextPage)

    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }
}