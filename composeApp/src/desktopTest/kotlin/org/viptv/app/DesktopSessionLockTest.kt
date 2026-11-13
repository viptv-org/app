package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class DesktopSessionLockTest {
    @Test fun separateProcessesWithDifferentHomesShareTheCredentialLock() = runBlocking {
        val directory = Files.createTempDirectory("viptv-session-lock-test")
        val locations = listOf(javaClass, RemoteSessionStore::class.java, Unit::class.java, Job::class.java)
            .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }
        val classpath = (locations + System.getProperty("java.class.path").split(java.io.File.pathSeparator) +
            generateSequence(javaClass.classLoader) { it.parent }.filterIsInstance<URLClassLoader>()
                .flatMap { it.urLs.asSequence() }.map { Path.of(it.toURI()).toString() }.toList())
            .distinct().joinToString(java.io.File.pathSeparator)
        fun start(name: String) = ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Djava.io.tmpdir=$directory", "-Duser.home=${directory.resolve(name)}", "-cp", classpath,
            "org.viptv.app.DesktopSessionLockProbe", directory.toString(), name,
        ).redirectErrorStream(true).redirectOutput(directory.resolve("$name.log").toFile()).start()
        var first: Process? = null
        var second: Process? = null
        suspend fun awaitFile(name: String) { withTimeout(5000) { while (!directory.resolve(name).exists()) delay(10) } }
        try {
            first = start("first")
            awaitFile("first.entered")
            val unused = object : RemoteSessionStore {
                override suspend fun read(): SavedRemoteSession? = error("No real credential access")
                override suspend fun save(session: SavedRemoteSession) = error("No real credential access")
                override suspend fun clear() = error("No real credential access")
            }
            var entered = false
            val waiter = async {
                DesktopRemoteSessionStore(unused, Path.of(directory.resolve("first.path").readText())).withSessionLock { entered = true }
            }
            assertFailsWith<TimeoutCancellationException> { withTimeout(200) { waiter.await() } }
            withTimeout(2000) { waiter.cancelAndJoin() }
            assertFalse(entered, "Cancelled lock acquisition must not enter the credential transaction")
            second = start("second")
            awaitFile("second.attempted")
            assertEquals(directory.resolve("first.path").readText(), directory.resolve("second.path").readText())
            assertFailsWith<TimeoutCancellationException> { withTimeout(200) { awaitFile("second.entered") } }
            directory.resolve("first.release").writeText("release")
            awaitFile("second.entered")
            directory.resolve("second.release").writeText("release")
            assertTrue(first.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(second.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(0, first.exitValue(), directory.resolve("first.log").readText())
            assertEquals(0, second.exitValue(), directory.resolve("second.log").readText())
        } catch (failure: Throwable) {
            for (name in listOf("first", "second")) directory.resolve("$name.log").takeIf { it.exists() }?.let { println(it.readText()) }
            throw failure
        } finally {
            first?.destroyForcibly(); second?.destroyForcibly()
            directory.toFile().deleteRecursively()
        }
    }
}

object DesktopSessionLockProbe {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val directory = Path.of(args[0]); val name = args[1]
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = error("No real credential access")
            override suspend fun save(session: SavedRemoteSession) = error("No real credential access")
            override suspend fun clear() = error("No real credential access")
        }
        directory.resolve("$name.path").writeText(desktopSessionLockPath().toString())
        directory.resolve("$name.attempted").writeText("attempted")
        DesktopRemoteSessionStore(store).withSessionLock {
            directory.resolve("$name.entered").writeText("entered")
            withTimeout(10000) { while (!directory.resolve("$name.release").exists()) delay(10) }
        }
    }
}
