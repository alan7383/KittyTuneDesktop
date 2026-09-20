package com.alananasss.kittytune.ui.player.lyrics.accompanist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine

@Composable
fun SyncedLineText(
    line: SyncedLine,
    isLineRtl: Boolean,
    isRightAligned: Boolean = false,
    isCenterAligned: Boolean = false,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true
) {
    val hAlign = when {
        isRightAligned -> Alignment.End
        isCenterAligned -> Alignment.CenterHorizontally
        isLineRtl -> Alignment.End
        else -> Alignment.Start
    }
    val tAlign = when {
        isRightAligned -> TextAlign.End
        isCenterAligned -> TextAlign.Center
        isLineRtl -> TextAlign.End
        else -> TextAlign.Start
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = hAlign
    ) {
        Text(
            text = line.content,
            style = textStyle,
            color = textColor,
            textAlign = tAlign
        )
        if (showTranslation) {
            line.translation?.let {
                Text(
                    text = it,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = tAlign
                )
            }
        }
    }
}
