package com.alananasss.kittytune.ui.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.upload.SOUNDCLOUD_AUDIO_GENRES
import com.alananasss.kittytune.data.upload.SOUNDCLOUD_MUSIC_GENRES
import com.alananasss.kittytune.data.upload.getGenreStringKey

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenrePickerDialog(
    selectedGenre: String,
    onGenreSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredMusicGenres = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            SOUNDCLOUD_MUSIC_GENRES
        } else {
            SOUNDCLOUD_MUSIC_GENRES.filter {
                it.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    val filteredAudioGenres = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            SOUNDCLOUD_AUDIO_GENRES
        } else {
            SOUNDCLOUD_AUDIO_GENRES.filter {
                it.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .width(520.dp)
                .heightIn(max = 680.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = str("upload_field_genre_pick"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedGenre.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    onGenreSelected("")
                                    onDismiss()
                                },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text(
                                    str("upload_genre_clear"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        IconButton(
                            onClick = onDismiss,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            str("upload_genre_search_placeholder"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (filteredMusicGenres.isNotEmpty()) {
                        item {
                            GenreCategoryHeader(title = str("upload_genre_category_music"))
                        }
                        items(filteredMusicGenres, key = { "music_$it" }) { genre ->
                            val key = getGenreStringKey(genre)
                            val displayName = if (key != null) str(key) else genre
                            GenreItemRow(
                                genre = genre,
                                displayName = displayName,
                                isSelected = genre.equals(selectedGenre, ignoreCase = true),
                                onClick = {
                                    onGenreSelected(genre)
                                    onDismiss()
                                }
                            )
                        }
                    }

                    if (filteredAudioGenres.isNotEmpty()) {
                        item {
                            GenreCategoryHeader(title = str("upload_genre_category_audio"))
                        }
                        items(filteredAudioGenres, key = { "audio_$it" }) { genre ->
                            val key = getGenreStringKey(genre)
                            val displayName = if (key != null) str(key) else genre
                            GenreItemRow(
                                genre = genre,
                                displayName = displayName,
                                isSelected = genre.equals(selectedGenre, ignoreCase = true),
                                onClick = {
                                    onGenreSelected(genre)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreCategoryHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp)
        )
    }
}

@Composable
private fun GenreItemRow(
    genre: String,
    displayName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp
        )

        if (isSelected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}
