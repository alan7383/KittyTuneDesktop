package com.alananasss.kittytune.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.ui.common.QrCode

@Composable
internal fun TogetherInviteQrButton(code: String) {
    var showQr by remember(code) { mutableStateOf(false) }
    val label = str("together_invite_qr")
    FilledTonalIconButton(
        onClick = { showQr = true },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(Icons.Rounded.QrCode2, contentDescription = label)
    }

    if (showQr) {
        val inviteUrl = remember(code) { TogetherLinks.inviteUrl(code) }
        EscapableAlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text(label) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp)) {
                        QrCode(
                            content = inviteUrl,
                            modifier = Modifier.size(212.dp).semantics { contentDescription = label },
                        )
                    }
                    Text(
                        str("together_invite_qr_instructions"),
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text(str("btn_close")) } },
        )
    }
}
