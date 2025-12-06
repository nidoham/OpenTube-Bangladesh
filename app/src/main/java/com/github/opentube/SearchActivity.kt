package com.github.opentube

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.extractor.stream.StreamInfoItem as LocalStreamInfoItem
import com.github.opentube.player.queue.PlayQueue
import com.github.opentube.player.queue.PlayQueueItem
import com.github.opentube.screens.items.VideoItemCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

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
        private set

    // Search results use lightweight local StreamInfoItem (card data)
    var searchResults by mutableStateOf<List<LocalStreamInfoItem>>(emptyList())
        private set

    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var showSuggestions by mutableStateOf(false)
        private set

    private var suggestionJob: Job? = null
    private val serviceId = ServiceList.YouTube.serviceId

    // Pagination
    private var currentExtractor: SearchExtractor? = null
    private var nextPageInfo: Page? = null

    fun updateSearchQuery(query: String) {
        searchQuery = query
        showSuggestions = query.isNotBlank()

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

        currentExtractor = null
        nextPageInfo = null

        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                try {
                    val service = NewPipe.getService(serviceId)
                    val extractor = service.getSearchExtractor(query)
                    extractor.fetchPage()

                    currentExtractor = extractor
                    nextPageInfo = extractor.initialPage.nextPage

                    val items = extractor.initialPage.items

                    items.filterIsInstance<StreamInfoItem>()
                        .map { item ->
                            LocalStreamInfoItem.from(item)
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList<LocalStreamInfoItem>()
                }
            }

            searchResults = results
            isLoading = false
        }
    }

    fun loadNextPage() {
        if (isLoadingMore || nextPageInfo == null || currentExtractor == null) return

        isLoadingMore = true

        viewModelScope.launch {
            val newResults = withContext(Dispatchers.IO) {
                try {
                    val page = currentExtractor!!.getPage(nextPageInfo)
                    nextPageInfo = page.nextPage

                    val items = page.items
                    items.filterIsInstance<StreamInfoItem>()
                        .map { item ->
                            LocalStreamInfoItem.from(item)
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList<LocalStreamInfoItem>()
                }
            }

            if (newResults.isNotEmpty()) {
                searchResults = searchResults + newResults
            }
            isLoadingMore = false
        }
    }

    fun clearSearch() {
        searchQuery = ""
        searchResults = emptyList()
        searchSuggestions = emptyList()
        showSuggestions = false
        currentExtractor = null
        nextPageInfo = null
        isLoading = false
        isLoadingMore = false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SearchScreen(
    onBackPressed: () -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

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
            if (viewModel.searchResults.isNotEmpty()) {
                SearchResultsList(
                    results = viewModel.searchResults,
                    isLoadingMore = viewModel.isLoadingMore,
                    onVideoClick = { stream ->
                        val item = PlayQueueItem(
                            title = stream.name,
                            channelName = stream.uploaderName,
                            thumbnailUrl = stream.thumbnails,
                            videoUrl = stream.url,
                            duration = stream.duration
                        )

                        val queue = PlayQueue.fromSingleItem(item)

                        val intent = Intent(context, PlayerActivity::class.java).apply {
                            putExtra("play_queue", queue)
                        }
                        context.startActivity(intent)
                    },
                    onLoadMore = {
                        viewModel.loadNextPage()
                    }
                )
            }

            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (viewModel.showSuggestions && viewModel.searchSuggestions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
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
    results: List<LocalStreamInfoItem>,
    isLoadingMore: Boolean,
    onVideoClick: (LocalStreamInfoItem) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.background(Color.Black),
        contentPadding = PaddingValues(
            top = 8.dp,
            start = 8.dp,
            end = 8.dp,
            bottom = 24.dp
        )
    ) {
        itemsIndexed(results) { index, video ->
            VideoItemCard(
                streamInfo = video,
                onClick = { onVideoClick(video) }
            )

            if (index == results.lastIndex && !isLoadingMore) {
                LaunchedEffect(key1 = index) {
                    onLoadMore()
                }
            }
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }
}

fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
