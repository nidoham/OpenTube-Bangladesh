package com.github.opentube

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.github.extractor.stream.StreamInfo
import com.github.opentube.player.queue.PlayQueue
import com.github.opentube.player.queue.PlayQueueItem
import com.github.opentube.screens.items.VideoItemCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

// Dark color scheme with pure black background
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFF3700B3),
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF1C1C1C),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB3B3B3)
)

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = DarkColorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SearchScreen(
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }
}

class SearchViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<StreamInfo>>(emptyList())
    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
    var isLoading by mutableStateOf(false)
    var showSuggestions by mutableStateOf(false)

    private var suggestionJob: Job? = null
    private val serviceId = ServiceList.YouTube.serviceId

    fun updateSearchQuery(query: String) {
        searchQuery = query
        showSuggestions = query.isNotBlank()

        // Debounced suggestion fetching
        suggestionJob?.cancel()
        if (query.isNotBlank()) {
            suggestionJob = viewModelScope.launch {
                delay(300)
                fetchSuggestions(query)
            }
        } else {
            searchSuggestions = emptyList()
        }
    }

    private suspend fun fetchSuggestions(query: String) {
        withContext(Dispatchers.IO) {
            try {
                val extractor = NewPipe.getService(serviceId).suggestionExtractor
                val suggestions = extractor.suggestionList(query)

                withContext(Dispatchers.Main) {
                    searchSuggestions = suggestions.take(8)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    searchSuggestions = emptyList()
                }
            }
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) return

        searchQuery = query
        showSuggestions = false
        isLoading = true
        searchResults = emptyList()

        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                try {
                    val service = NewPipe.getService(serviceId)
                    val extractor = service.getSearchExtractor(query)
                    extractor.fetchPage()

                    val items = extractor.initialPage.items

                    items.filterIsInstance<StreamInfoItem>()
                        .map { item ->
                            StreamInfo.from(item)
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList<StreamInfo>()
                }
            }

            searchResults = results
            isLoading = false
        }
    }

    fun clearSearch() {
        searchQuery = ""
        searchResults = emptyList()
        searchSuggestions = emptyList()
        showSuggestions = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackPressed: () -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            SearchTopBar(
                searchQuery = viewModel.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSearch = {
                    viewModel.performSearch(it)
                    focusManager.clearFocus()
                },
                onClear = { viewModel.clearSearch() },
                onBackPressed = onBackPressed,
                focusRequester = focusRequester
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
        ) {
            // Search Results
            if (viewModel.searchResults.isNotEmpty()) {
                val context = LocalContext.current
                SearchResultsList(
                    results = viewModel.searchResults,
                    onVideoClick = { stream ->
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

            // Loading Indicator
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Search Suggestions Overlay
            if (viewModel.showSuggestions && viewModel.searchSuggestions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .zIndex(1f)
                ) {
                    SearchSuggestionsList(
                        suggestions = viewModel.searchSuggestions,
                        onSuggestionClick = { suggestion ->
                            viewModel.performSearch(suggestion)
                            focusManager.clearFocus()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onBackPressed: () -> Unit,
    focusRequester: FocusRequester
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black,
            navigationIconContentColor = Color.White,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        title = {
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        "Search",
                        color = Color(0xFF888888)
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color.White
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch(searchQuery) }
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color.White
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Search filters") },
                    onClick = { showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = { showMenu = false }
                )
            }
        }
    )
}

@Composable
fun SearchSuggestionsList(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        items(suggestions) { suggestion ->
            SuggestionItem(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun SuggestionItem(
    suggestion: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color(0xFFB3B3B3),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}

@Composable
fun SearchResultsList(
    results: List<StreamInfo>,
    onVideoClick: (StreamInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.background(Color.Black),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(results) { video ->
            VideoItemCard(
                streamInfo = video,
                onClick = { onVideoClick(video) }
            )
        }
    }
}

fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}

fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}