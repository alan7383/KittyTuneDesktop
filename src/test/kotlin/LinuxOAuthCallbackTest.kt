package com.alananasss.kittytune.core

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.*

class LinuxOAuthCallbackTest {
    @Test fun `callbacks use a portable interpreter`() {
        for (parameters in listOf(listOf("code", "error"), listOf("data64"))) {
            val script = linuxOAuthCallbackScript(12345, parameters)
            assertEquals("#!/bin/sh", script.lineSequence().first())
            assertFalse(script.contains("[["), "The callback must also run with dash")
        }
    }

    @Test fun `account login forwards success and denial but ignores probes`() {
        withCallback(listOf("code", "error")) { run, requests ->
            for (uri in listOf("sc://auth?code=a%2Bb&state=test", "sc://auth?error=access_denied")) {
                run(uri)
                assertEquals(uri, requests.poll(2, TimeUnit.SECONDS))
            }
            run("sc://auth")
            run(null)
            assertNull(requests.poll())
        }
    }

    @Test fun `music import forwards encoded data without shell expansion`() {
        withCallback(listOf("data64")) { run, requests ->
            val uri = "soundcloud://musicapi/auth?data64=a+b/c==&value=%22%27&literal=\$HOME;\$(id)"
            run(uri)
            assertEquals(uri, requests.poll(2, TimeUnit.SECONDS))
            run("soundcloud://auth?code=unrelated")
            run("soundcloud://musicapi/auth")
            assertNull(requests.poll())
        }
    }

    @Test fun `desktop callbacks can use curl outside the browser PATH`() {
        val curlName = if (System.getProperty("os.name").startsWith("Windows")) "curl.exe" else "curl"
        val curl = System.getProperty("kittytune.test.curlExecutable") ?: System.getenv("PATH")
            .split(java.io.File.pathSeparator)
            .map { java.io.File(it, curlName) }
            .first { it.isFile && it.canExecute() }.absolutePath
        withCallback(listOf("code", "error"), curl, minimalPath = true) { run, requests ->
            val uri = "sc://auth?code=from-desktop"
            run(uri)
            assertEquals(uri, requests.poll(2, TimeUnit.SECONDS))
        }
    }

    private fun withCallback(
        parameters: List<String>,
        curlExecutable: String = "curl",
        minimalPath: Boolean = false,
        test: ((String?) -> Unit, LinkedBlockingQueue<String>) -> Unit
    ) {
        val windows = System.getProperty("os.name").startsWith("Windows")
        val shell = System.getProperty("kittytune.test.posixShell")
        assumeTrue("POSIX script execution needs sh", !windows || shell != null)
        val requests = LinkedBlockingQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/callback") { exchange ->
            exchange.use {
                assertEquals("POST", it.requestMethod)
                requests.add(it.requestBody.readBytes().toString(Charsets.UTF_8))
                it.sendResponseHeaders(204, -1)
            }
        }
        val dir = Files.createTempDirectory("oauth callback ")
        val script = dir.resolve("handler.sh").toFile()
        script.writeText(linuxOAuthCallbackScript(server.address.port, parameters, curlExecutable))
        script.setExecutable(true)
        server.start()
        try {
            test({ uri ->
                val command = mutableListOf<String>()
                if (shell != null) command.add(shell)
                command.add(script.absolutePath)
                if (uri != null) command.add(uri)
                val builder = ProcessBuilder(command).redirectErrorStream(true)
                if (minimalPath) builder.environment()["PATH"] = dir.toString()
                val process = builder.start()
                try {
                    assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Callback did not finish")
                    assertEquals(0, process.exitValue(), process.inputStream.bufferedReader().readText())
                } finally {
                    process.destroyForcibly()
                }
            }, requests)
        } finally {
            server.stop(0)
            script.delete()
            dir.toFile().delete()
        }
    }
}
