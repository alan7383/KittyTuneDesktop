import java.io.File

fun main() {
    println("Testing setsid")
    val p = ProcessBuilder("setsid", "sleep", "10")
        .redirectErrorStream(true)
        .start()
    val output = p.inputStream.bufferedReader().readText()
    p.waitFor()
    println("setsid output: $output")
    println("setsid exit code: ${p.exitValue()}")
}

main()
