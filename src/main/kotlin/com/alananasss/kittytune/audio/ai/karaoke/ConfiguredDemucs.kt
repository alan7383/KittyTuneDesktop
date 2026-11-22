package com.alananasss.kittytune.audio.ai.karaoke

import java.io.File

internal fun configuredDemucsBinary(path: String?): File? = path?.takeIf { it.isNotBlank() }
    ?.let(::File)?.takeIf { it.isAbsolute && it.isFile && it.canExecute() }
