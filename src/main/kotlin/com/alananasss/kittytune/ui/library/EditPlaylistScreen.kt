package com.alananasss.kittytune.ui.library

import androidx.compose.material3.IconButtonDefaults

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.TagSuggestion
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistScreen(
    initialTitle: String,
    initialDescription: String?,
    initialSharing: String?,
    initialTagList: String?,
    initialGenre: String?,
    initialSetType: String?,
    initialReleaseDate: String?,
    initialPermalink: String?,
    playlistUser: User?,
    onDismissRequest: () -> Unit,
    onSave: (title: String,description: String?,sharing: String,tagList: String?,genre: String?,setType: String?,releaseDate: String?,permalink: String?) -> Unit
) {
    
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription ?: "") }
    var sharing by remember { mutableStateOf(initialSharing ?: "public") }
    
    var genre by remember { mutableStateOf(initialGenre ?: "") }
    var setType by remember { mutableStateOf(initialSetType ?: "") }
    var releaseDate by remember { mutableStateOf(initialReleaseDate ?: "") }
    var permalink by remember { mutableStateOf(initialPermalink ?: "") }
    
    var showGenreDropdown by remember { mutableStateOf(false) }
    var showSetTypeDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val parseTags = { tagListStr: String ->
        val regex = """"([^"]*)"|(\S+)""".toRegex()
        regex.findAll(tagListStr).mapNotNull { it.groupValues[1].takeIf { it.isNotEmpty() } ?: it.groupValues[2].takeIf { it.isNotEmpty() } }.toList()
    }

    var tags by remember { 
        mutableStateOf(
            parseTags(initialTagList ?: "")
        ) 
    }
    var tagInput by remember { mutableStateOf("") }
    
    var tagSuggestions by remember { mutableStateOf<List<TagSuggestion>>(emptyList()) }
    var isSearchingTags by remember { mutableStateOf(false) }

    LaunchedEffect(tagInput) {
        if (tagInput.length >= 2) {
            isSearchingTags = true
            delay(300) // debounce
            try {
                val api = RetrofitClient.create()
                val response = api.searchTags(query = tagInput,limit = 5)
                tagSuggestions = response.suggestions ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSearchingTags = false
            }
        } else {
            tagSuggestions = emptyList()
            isSearchingTags = false
        }
    }

    BackHandler(onBack = onDismissRequest)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.width(600.dp).fillMaxHeight(0.9f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(24.dp)
        ) {
            Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(str("edit_playlist_title")) },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                                onSave(
                                    title,
                                    description,
                                    sharing,
                                    tags.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it },
                                    genre,
                                    setType,
                                    releaseDate,
                                    permalink
                                )
                            },
                            enabled = title.isNotBlank() && (setType == "" || releaseDate.isNotBlank()) && permalink.matches(Regex("^[a-z0-9_-]+$"))
                        ) {
                            Text(str("btn_save"))
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(str("upload_field_title")) },
                        modifier = Modifier.fillMaxWidth().trackTextInput(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }

                item {
                    val isValidPermalink = permalink.matches(Regex("^[a-z0-9_-]+$"))
                    OutlinedTextField(
                        value = permalink,
                        onValueChange = { permalink = it },
                        label = { Text(str("edit_playlist_permalink")) },
                        modifier = Modifier.fillMaxWidth().trackTextInput(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        isError = !isValidPermalink,
                        prefix = { Text("soundcloud.com/${playlistUser?.permalink ?: "user"}/sets/") },
                        supportingText = {
                            if (!isValidPermalink) {
                                Text(str("edit_playlist_permalink_hint"))
                            }
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(str("upload_field_description")) },
                        modifier = Modifier.fillMaxWidth().height(120.dp).trackTextInput(),
                        maxLines = 5
                    )
                }

                item {
                    Text(str("edit_playlist_privacy"),style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sharing == "public",
                            onClick = { sharing = "public" }
                        )
                        Text(str("lib_playlist_public"),modifier = Modifier.clickable { sharing = "public" })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sharing == "private",
                            onClick = { sharing = "private" }
                        )
                        Text(str("edit_playlist_private"),modifier = Modifier.clickable { sharing = "private" })
                    }
                }

                item {
                    Text(str("edit_playlist_type"),style = MaterialTheme.typography.titleMedium)
                    ExposedDropdownMenuBox(
                        expanded = showSetTypeDropdown,
                        onExpandedChange = { showSetTypeDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = when(setType) {
                                "" -> str("edit_playlist_type_playlist")
                                "album" -> str("edit_playlist_type_album")
                                "ep" -> "EP"
                                "single" -> str("edit_playlist_type_single")
                                "compilation" -> str("edit_playlist_type_compilation")
                                else -> str("edit_playlist_type_playlist")
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSetTypeDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor().trackTextInput()
                        )
                        ExposedDropdownMenu(
                            expanded = showSetTypeDropdown,
                            onDismissRequest = { showSetTypeDropdown = false }
                        ) {
                            // The left side is what SoundCloud stores and must stay in English; only the label is shown.
                            listOf(
                                "" to str("edit_playlist_type_playlist"),
                                "album" to str("edit_playlist_type_album"),
                                "ep" to "EP",
                                "single" to str("edit_playlist_type_single"),
                                "compilation" to str("edit_playlist_type_compilation"),
                            ).forEach { (value,label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { 
                                        setType = value
                                        showSetTypeDropdown = false 
                                    }
                                )
                            }
                        }
                    }
                }
                
                if (setType != "") {
                    item {
                        OutlinedTextField(
                            value = releaseDate,
                            onValueChange = { releaseDate = it },
                            label = { Text(str("upload_field_release_date")) },
                            modifier = Modifier.fillMaxWidth().trackTextInput(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            trailingIcon = {
                                TextButton(onClick = { showDatePicker = true }) {
                                    Text(str("btn_pick"))
                                }
                            }
                        )
                    }
                }

                item {
                    Text(str("detail_genre"),style = MaterialTheme.typography.titleMedium)
                    val predefinedGenres = listOf("None","Custom","Alternative Rock","Ambient","Classical","Country","Dance & EDM","Dancehall","Deep House","Disco","Drum & Bass","Dubstep","Electronic","Folk & Singer-Songwriter","Hip-hop & Rap","House","Indie","Jazz & Blues","Latin","Metal","Piano","Pop","R&B & Soul","Reggae","Reggaeton","Rock","Soundtrack","Techno","Trance","Trap","Triphop","World","Audiobooks","Business","Comedy","Entertainment","Learning","News & Politics","Religion & Spirituality","Science","Sports","Storytelling","Technology")
                    var selectedGenreCategory by remember { mutableStateOf(if (genre in predefinedGenres || genre == "") genre else "Custom") }

                    // Genre names stay as SoundCloud spells them, because that is what gets sent and
                    // what the rest of the app already shows. Only the two entries that are not genres
                    // are translated.
                    val genreLabel: (String) -> String = { value ->
                        when (value) {
                            "None", "" -> str("edit_playlist_genre_none")
                            "Custom" -> str("edit_playlist_genre_custom")
                            else -> value
                        }
                    }
                    
                    ExposedDropdownMenuBox(
                        expanded = showGenreDropdown,
                        onExpandedChange = { showGenreDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = genreLabel(selectedGenreCategory),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showGenreDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor().trackTextInput()
                        )
                        ExposedDropdownMenu(
                            expanded = showGenreDropdown,
                            onDismissRequest = { showGenreDropdown = false }
                        ) {
                            predefinedGenres.forEach { label ->
                                DropdownMenuItem(
                                    text = { Text(genreLabel(label)) },
                                    onClick = { 
                                        val value = if (label == "None") "" else label
                                        selectedGenreCategory = value
                                        if (value != "Custom") genre = value
                                        showGenreDropdown = false 
                                    }
                                )
                            }
                        }
                    }
                    if (selectedGenreCategory == "Custom") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text(str("edit_playlist_custom_genre")) },
                            modifier = Modifier.fillMaxWidth().trackTextInput(),
                            singleLine = true
                        )
                    }
                }

                item {
                    Text(str("detail_tags"),style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text(str("edit_playlist_add_tags")) },
                        modifier = Modifier.fillMaxWidth().trackTextInput(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        trailingIcon = {
                            if (tagInput.isNotBlank()) {
                                IconButton(onClick = { 
                                    if (!tags.contains(tagInput.trim())) {
                                        tags = tags + tagInput.trim()
                                    }
                                    tagInput = "" 
                                }) {
                                    Icon(Icons.Rounded.Close,contentDescription = str("edit_playlist_add_tags"))
                                }
                            }
                        }
                    )
                }
                
                if (tagSuggestions.isNotEmpty()) {
                    items(tagSuggestions) { suggestion ->
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (!tags.contains(suggestion.id)) {
                                    tags = tags + suggestion.id
                                }
                                tagInput = ""
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                        ListItem(
                            headlineContent = { Text(suggestion.id) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                        }
                    }
                }

                if (tags.isNotEmpty()) {
                    item {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                androidx.compose.material3.Button(
                                    onClick = { tags = tags - tag },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(tag)
                                        Icon(Icons.Rounded.Close, contentDescription = str("menu_remove"), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
        } // end Card
    } // end Dialog

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val calendar = java.util.Calendar.getInstance()
                        calendar.timeInMillis = millis
                        val y = calendar.get(java.util.Calendar.YEAR)
                        val m = calendar.get(java.util.Calendar.MONTH) + 1
                        val d = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                        releaseDate = "$y-${m.toString().padStart(2,'0')}-${d.toString().padStart(2,'0')}"
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(str("btn_cancel")) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

fun parseTags(tagList: String): List<String> {
    val tags = mutableListOf<String>()
    var inQuotes = false
    val currentTag = java.lang.StringBuilder()
    for (char in tagList) {
        if (char == '"') {
            inQuotes = !inQuotes
        } else if (char == ' ' && !inQuotes) {
            if (currentTag.isNotEmpty()) {
                tags.add(currentTag.toString())
                currentTag.clear()
            }
        } else {
            currentTag.append(char)
        }
    }
    if (currentTag.isNotEmpty()) {
        tags.add(currentTag.toString())
    }
    return tags
}
