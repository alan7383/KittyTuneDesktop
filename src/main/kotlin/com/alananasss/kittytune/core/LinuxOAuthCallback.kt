package com.alananasss.kittytune.core

internal fun linuxOAuthCallbackScript(
    port: Int,
    parameters: List<String>,
    curlExecutable: String = System.getenv("KITTYTUNE_CURL") ?: "curl"
): String {
    require(port in 1..65535)
    require(parameters.isNotEmpty() && parameters.all { it.matches(Regex("[a-zA-Z0-9_]+")) })
    val pattern = parameters.joinToString("|") { "*$it=*" }
    val curl = "'" + curlExecutable.replace("'", "'\\''") + "'"
    return """
        #!/bin/sh
        case "${'$'}{1-}" in
          $pattern)
            $curl -s -X POST 'http://127.0.0.1:$port/callback' -d "${'$'}1"
            ;;
        esac
    """.trimIndent() + "\n"
}
