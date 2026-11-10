    package com.alananasss.kittytune.ui.common
    
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material3.MaterialTheme
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.composed
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.draw.drawBehind
    import androidx.compose.ui.geometry.Offset
    import androidx.compose.ui.graphics.Brush
    import androidx.compose.ui.graphics.Shape
    import androidx.compose.ui.unit.dp
    
    /**
     * The magic gradient that moves across a placeholder.
     *
     * On the shared ambient pulse and at half its rate (see AmbientClock): a one-second sheen is indistinguishable
     * at fifteen steps, and, unlike the `rememberInfiniteTransition` this was, it stops while the window is hidden
     * instead of keeping every skeleton on screen redrawing for nobody.
     *
     * The value is read while drawing, so a step repaints one shape rather than recomposing every placeholder.
     */
    fun Modifier.shimmerBackground(shape: Shape): Modifier = composed {
        val shimmerColors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
        val travel = rememberAmbientLoop(
            periodMs = SHIMMER_PERIOD_MS,
            from = 0f,
            to = SHIMMER_TRAVEL,
            slow = true,
        )
        this
            .clip(shape)
            .drawBehind {
                val x = travel.value
                drawRect(Brush.linearGradient(colors = shimmerColors, start = Offset.Zero, end = Offset(x, x)))
            }
    }

    private const val SHIMMER_PERIOD_MS = 1000
    private const val SHIMMER_TRAVEL = 1000f
    
    // --- basic building blocks ---
    
    // just looks like a line of text
    @Composable
    fun ShimmerLine(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .height(16.dp)
                .shimmerBackground(RoundedCornerShape(8.dp))
        )
    }
    
    // generic box placeholder
    @Composable
    fun ShimmerBox(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.shimmerBackground(RoundedCornerShape(12.dp))
        )
    }
    
    
    // --- specific skeletons ---
    
    // pretend this is a song row in a list
    @Composable
    fun TrackListItemShimmer(modifier: Modifier = Modifier) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShimmerLine(modifier = Modifier.fillMaxWidth(0.7f))
                ShimmerLine(modifier = Modifier.fillMaxWidth(0.4f))
            }
        }
    }
    
    // square card placeholder (playlists/albums)
    @Composable
    fun SquareCardShimmer(modifier: Modifier = Modifier) {
        Column(modifier = modifier.width(140.dp)) {
            ShimmerBox(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(8.dp))
            ShimmerLine(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp))
            Spacer(Modifier.height(4.dp))
            ShimmerLine(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp))
        }
    }
    
    // round placeholder for artist profiles
    @Composable
    fun ArtistCircleShimmer(modifier: Modifier = Modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.width(120.dp)
        ) {
            ShimmerBox(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(8.dp))
            ShimmerLine(modifier = Modifier.fillMaxWidth(0.7f))
        }
    }


