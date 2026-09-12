package com.innly.hotelbooking.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun RoomImage(
    roomImageUrl: String,
    hotelImageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var currentImageSource by remember(roomImageUrl, hotelImageUrl) {
        mutableStateOf<String?>(roomImageUrl.takeIf { it.isNotBlank() } ?: hotelImageUrl?.takeIf { it.isNotBlank() })
    }
    var hasError by remember(roomImageUrl, hotelImageUrl) { mutableStateOf(false) }

    val activeSource = currentImageSource
    if (!hasError && !activeSource.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(activeSource)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            onError = {
                if (activeSource == roomImageUrl && !hotelImageUrl.isNullOrBlank() && hotelImageUrl != roomImageUrl) {
                    currentImageSource = hotelImageUrl
                } else {
                    hasError = true
                }
            },
        )
    } else {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Bed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Photo unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
