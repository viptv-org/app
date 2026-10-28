package org.viptv.app

import java.io.File
import kotlin.test.*

/**
 * App source may not reference a private dev host (app#113). The private
 * hostnames themselves are kept out of the repo, so this guard can't grep for
 * them directly: instead it scans source and fails on any hostname that isn't
 * on an allowlist of known public/placeholder hosts. URL origins
 * (`scheme://host`) are read from the whole file; bare `host/path` references
 * are read only from string literals so property access like `cards.size / 2`
 * is not mistaken for a hostname. Single labels and IP addresses are not
 * qualified hostnames and are allowed.
 */
class NoPrivateHostsTest {
    private val scannedExtensions = setOf("kt", "kts", "xml", "js", "html")
    private val ipv4 = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
    private val urlOrigin = Regex("""(?:https?|wss?)://([A-Za-z0-9.-]+)""", RegexOption.IGNORE_CASE)
    private val quotedString = Regex(""""[^"\n]*"""")
    private val bareHostPath = Regex("""(?<![A-Za-z0-9./@-])([A-Za-z0-9-]+(?:\.[A-Za-z0-9-]*[A-Za-z][A-Za-z0-9-]*)+)/""", RegexOption.IGNORE_CASE)

    /** Reserved (RFC 2606) and placeholder top-level labels used by fixtures. */
    private val reservedTlds = setOf("example", "invalid", "test", "localhost", "local")
    private val reservedDomains = setOf("example.com", "example.org", "example.net")

    /** Real external services the app legitimately talks to. */
    private val publicDomains = setOf(
        "tmdb.org", "fanart.tv", "thetvdb.com", "imgur.com",
        "metahub.space", "simkl.in", "media-amazon.com", "mozilla.net",
        "mux.dev", "strem.io", "wsrv.nl", "android.com", "apache.org", "slf4j.org",
    )

    @Test fun sourceOnlyReferencesAllowedHosts() {
        // Gradle runs desktop tests from the composeApp module directory.
        val sources = File("src")
        assertTrue(sources.resolve("commonMain").isDirectory, "run from composeApp: ${sources.absolutePath}")
        val offenders = sources.walkTopDown()
            .filter { it.isFile && it.extension in scannedExtensions && !it.path.contains("${File.separator}desktopTest${File.separator}") }
            .flatMap { hostnames(it).asSequence() }
            .filter { !allowed(it) }
            .toSortedSet()
        assertEquals(emptyList<String>(), offenders.toList(), "non-allowlisted hostname referenced in app source")
    }

    private fun hostnames(file: File): Set<String> {
        val text = file.readText()
        val hosts = urlOrigin.findAll(text).map { it.groupValues[1] }.toMutableList()
        for (literal in quotedString.findAll(text)) {
            hosts += bareHostPath.findAll(literal.value).map { it.groupValues[1] }
        }
        return hosts.map { it.lowercase() }.toSet()
    }

    private fun allowed(host: String): Boolean {
        if (!host.contains('.')) return true // single label: localhost, your-server, simkl, ...
        if (ipv4.matches(host)) return true // IP address, not a hostname
        if (reservedTlds.any { host.endsWith(".$it") }) return true
        if (reservedDomains.any { host == it || host.endsWith(".$it") }) return true
        if (publicDomains.any { host == it || host.endsWith(".$it") }) return true
        return false
    }
}