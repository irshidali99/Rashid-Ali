package com.example.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Category
import com.example.model.DuplicateGroup
import com.example.model.FileItem
import com.example.ui.theme.*

enum class DuplicateSortOrder(val label: String) {
    SIZE_DESC("Largest Size"),
    SIZE_ASC("Smallest Size"),
    NAME_ASC("Name (A–Z)"),
    DATE_DESC("Newest First")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateListScreen(
    initialCategory: Category? = null,
    groups: List<DuplicateGroup>,
    selectedUris: Set<Uri>,
    isDeleting: Boolean = false,
    onToggleSelection: (Uri, List<Uri>) -> Unit,
    onBack: () -> Unit,
    onDeleteSelected: (List<Uri>) -> Unit
) {
    var currentCategoryFilter by remember { mutableStateOf<Category?>(initialCategory) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(DuplicateSortOrder.SIZE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Filter by Category first
    val categoryFilteredGroups = remember(groups, currentCategoryFilter) {
        if (currentCategoryFilter == null) {
            groups
        } else {
            groups.filter { it.category == currentCategoryFilter }
        }
    }

    // Filter by Search & Sort
    val filteredGroups = remember(categoryFilteredGroups, searchQuery, sortOrder) {
        val searchFiltered = if (searchQuery.isBlank()) {
            categoryFilteredGroups
        } else {
            categoryFilteredGroups.filter { group ->
                group.items.any { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }

        when (sortOrder) {
            DuplicateSortOrder.SIZE_DESC -> searchFiltered.sortedByDescending { it.sizePerItem }
            DuplicateSortOrder.SIZE_ASC -> searchFiltered.sortedBy { it.sizePerItem }
            DuplicateSortOrder.NAME_ASC -> searchFiltered.sortedBy { it.items.first().name.lowercase() }
            DuplicateSortOrder.DATE_DESC -> searchFiltered.sortedByDescending { it.items.first().dateModified }
        }
    }

    // Current selection within active view
    val visibleSelectedUris = remember(categoryFilteredGroups, selectedUris) {
        categoryFilteredGroups.flatMap { it.items }.map { it.uri }.filter { it in selectedUris }
    }
    val visibleSelectedSize = remember(categoryFilteredGroups, selectedUris) {
        categoryFilteredGroups.flatMap { it.items }.filter { it.uri in selectedUris }.sumOf { it.size }
    }

    // Category count counts for chips
    val photoCount = remember(groups) { groups.filter { it.category == Category.PHOTOS }.sumOf { it.items.size - 1 } }
    val audioCount = remember(groups) { groups.filter { it.category == Category.AUDIO }.sumOf { it.items.size - 1 } }
    val videoCount = remember(groups) { groups.filter { it.category == Category.VIDEOS }.sumOf { it.items.size - 1 } }
    val othersCount = remember(groups) { groups.filter { it.category == Category.FILES }.sumOf { it.items.size - 1 } }
    val totalDuplicatesCount = remember(groups) { groups.sumOf { it.items.size - 1 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search files...", fontSize = 15.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            val titleText = if (currentCategoryFilter == null) {
                                "All Duplicates"
                            } else {
                                "${currentCategoryFilter?.displayName} Duplicates"
                            }
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (categoryFilteredGroups.isNotEmpty()) {
                                Text(
                                    text = "${filteredGroups.size} group${if (filteredGroups.size == 1) "" else "s"} • ${sortOrder.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (isSearchActive) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        if (categoryFilteredGroups.isNotEmpty()) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }

                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DuplicateSortOrder.values().forEach { order ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = order.label,
                                                    fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (order == sortOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                sortOrder = order
                                                showSortMenu = false
                                            },
                                            leadingIcon = {
                                                if (order == sortOrder) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (visibleSelectedUris.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${visibleSelectedUris.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${formatSize(visibleSelectedSize)} recoverable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                showConfirmDialog = true
                            },
                            enabled = !isDeleting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DELETE SELECTED",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category Filter Chips Row (All-in-One Category Switcher)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentCategoryFilter == null,
                    onClick = { currentCategoryFilter = null },
                    label = { Text("All ($totalDuplicatesCount)") },
                    leadingIcon = {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = currentCategoryFilter == Category.PHOTOS,
                    onClick = { currentCategoryFilter = Category.PHOTOS },
                    label = { Text("Photos ($photoCount)") },
                    leadingIcon = {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = currentCategoryFilter == Category.AUDIO,
                    onClick = { currentCategoryFilter = Category.AUDIO },
                    label = { Text("Audio ($audioCount)") },
                    leadingIcon = {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = currentCategoryFilter == Category.VIDEOS,
                    onClick = { currentCategoryFilter = Category.VIDEOS },
                    label = { Text("Videos ($videoCount)") },
                    leadingIcon = {
                        Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = currentCategoryFilter == Category.FILES,
                    onClick = { currentCategoryFilter = Category.FILES },
                    label = { Text("Others ($othersCount)") },
                    leadingIcon = {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (categoryFilteredGroups.isEmpty()) {
                CategoryEmptyState(
                    category = currentCategoryFilter,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No duplicates match \"$searchQuery\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching with a different file name",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredGroups, key = { it.hash }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            selectedUris = selectedUris,
                            onToggleSelection = { uri -> onToggleSelection(uri, group.items.map { it.uri }) }
                        )
                    }
                }
            }
        }
    }

    // Safety Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Delete ${visibleSelectedUris.size} duplicate files?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = "${formatSize(visibleSelectedSize)} will be recovered from your device storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• The original file in each group is marked KEEP and will NOT be deleted.\n• Only the selected duplicate copies will be removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onDeleteSelected(visibleSelectedUris)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
fun DuplicateGroupCard(
    group: DuplicateGroup,
    selectedUris: Set<Uri>,
    onToggleSelection: (Uri) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(getCategoryColor(group.category).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(group.category),
                            contentDescription = null,
                            tint = getCategoryColor(group.category),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = group.items.first().name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = getCategoryColor(group.category).copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = group.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = getCategoryColor(group.category),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = "${group.items.size} copies",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Items List
            group.items.forEachIndexed { index, item ->
                val isSelected = selectedUris.contains(item.uri)
                val isKeptOriginal = !isSelected

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToggleSelection(item.uri) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelection(item.uri) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Thumbnail / Icon
                        ItemThumbnail(item = item, category = group.category)

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatSize(item.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!item.path.isNullOrBlank()) {
                                Text(
                                    text = item.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Badge: KEEP or DELETE
                    if (isKeptOriginal) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KeepBadgeBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "KEEP",
                                color = KeepBadgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DeleteBadgeBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "DELETE",
                                color = DeleteBadgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemThumbnail(item: FileItem, category: Category) {
    val context = LocalContext.current

    when (category) {
        Category.PHOTOS, Category.VIDEOS -> {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.uri)
                    .crossfade(true)
                    .size(120, 120)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Category.AUDIO -> {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AudioBadgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = AudioIconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Category.FILES -> {
            val ext = item.name.substringAfterLast('.', "").uppercase().take(4)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FilesBadgeBg),
                contentAlignment = Alignment.Center
            ) {
                if (ext.isNotBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = FilesIconColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = ext,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = FilesIconColor
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = FilesIconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryEmptyState(category: Category?, modifier: Modifier = Modifier) {
    val categoryTitle = when (category) {
        Category.PHOTOS -> "No duplicate photos found"
        Category.AUDIO -> "No duplicate audio found"
        Category.VIDEOS -> "No duplicate videos found"
        Category.FILES -> "No duplicate others found"
        null -> "No duplicate files found"
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(KeepBadgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = KeepBadgeText,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = categoryTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your storage is clean.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getCategoryIcon(category: Category): ImageVector {
    return when (category) {
        Category.PHOTOS -> Icons.Default.Image
        Category.AUDIO -> Icons.Default.MusicNote
        Category.VIDEOS -> Icons.Default.Movie
        Category.FILES -> Icons.Default.Description
    }
}

fun getCategoryColor(category: Category): Color {
    return when (category) {
        Category.PHOTOS -> PhotosIconColor
        Category.AUDIO -> AudioIconColor
        Category.VIDEOS -> VideosIconColor
        Category.FILES -> FilesIconColor
    }
}
