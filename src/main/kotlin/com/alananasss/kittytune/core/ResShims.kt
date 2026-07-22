package com.alananasss.kittytune.core

import androidx.compose.runtime.Composable
import com.alananasss.kittytune.core.Strings

/**
 * Desktop shims replacing Android-only Compose APIs so screens port verbatim.
 *
 * On Android:  import androidx.compose.ui.res.stringResource  + R.string.x (Int)
 * On desktop:  import com.alananasss.kittytune.core.stringResource + R.string.x (String key)
 * (Ported files only need their import line rewritten.)
 */
@Composable
fun stringResource(id: String): String = Strings.get(id)

@Composable
fun stringResource(id: String, vararg formatArgs: Any?): String = Strings.get(id, *formatArgs)
