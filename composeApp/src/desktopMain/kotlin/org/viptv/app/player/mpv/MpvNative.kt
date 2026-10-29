package org.viptv.app.player.mpv

import com.sun.jna.*
import com.sun.jna.ptr.PointerByReference
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.DigestInputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.*
import java.nio.file.StandardCopyOption.*
import java.nio.file.attribute.PosixFilePermissions
import java.io.OutputStream

internal interface MpvNative : Library {
    fun mpv_client_api_version(): Long
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int
    fun mpv_set_option(handle: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
    fun mpv_get_property(handle: Pointer, name: String, format: Int, value: Pointer): Int
    fun mpv_get_property_string(handle: Pointer, name: String): Pointer?
    fun mpv_command(handle: Pointer, args: StringArray): Int
    fun mpv_wait_event(handle: Pointer, seconds: Double): Pointer
    fun mpv_free_node_contents(node: Pointer)
    fun mpv_free(value: Pointer)
    fun mpv_terminate_destroy(handle: Pointer)
    fun mpv_render_context_create(result: PointerByReference, handle: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(context: Pointer, callback: UpdateCallback?, data: Pointer?)
    fun mpv_render_context_update(context: Pointer): Long
    fun mpv_render_context_render(context: Pointer, params: Pointer): Int
    fun mpv_render_context_report_swap(context: Pointer)
    fun mpv_render_context_free(context: Pointer)

    interface UpdateCallback : Callback { fun invoke(data: Pointer?) }
    companion object {
        val api: MpvNative by lazy {
            require(Native.POINTER_SIZE == 8) { "mpv desktop requires a 64-bit JVM" }
            val path = MpvRuntime.library()
            Native.load(path, MpvNative::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")).also {
                require(it.mpv_client_api_version() ushr 16 == 2L) { "Unsupported libmpv ABI" }
            }
        }
    }
}

// One pinned, checksum-verified native runtime per platform; system libraries are QA-only.
internal object MpvRuntime {
    private var cached: String? = null
    private interface WindowsLoader : Library {
        fun LoadLibraryExW(path: WString, reserved: Pointer?, flags: Int): Pointer?
    }
    @Synchronized fun library(): String {
        cached?.let { return it }
        System.getenv("VIPTV_MPV_LIBRARY")?.let {
            require(Path.of(it).isAbsolute && Files.isRegularFile(Path.of(it)))
            cached = it
            return it
        }
        val os = when { Platform.isWindows() -> "win32"; Platform.isMac() -> "darwin"; else -> "linux" }
        val arch = if (Platform.isARM()) "aarch64" else "x86-64"
        val prefix = "/mpv-runtime/$os-$arch/"
        val raw = MpvRuntime::class.java.getResourceAsStream(prefix + "manifest.json")?.use { it.readBytes() }
            ?: error("Packaged mpv runtime unavailable for $os/$arch")
        val manifest = Json.parseToJsonElement(raw.decodeToString()).jsonObject
        require(manifest.getValue("platform").jsonPrimitive.content == "$os-$arch")
        require(manifest.getValue("abi_major").jsonPrimitive.int == 2)
        val files = manifest.getValue("files").jsonObject
        val library = manifest.getValue("library").jsonPrimitive.content
        require(library in files)
        val home = Path.of(System.getProperty("user.home"))
        val cacheRoot = when {
            Platform.isWindows() -> (System.getenv("LOCALAPPDATA")?.let(Path::of) ?: home.resolve("AppData/Local")).resolve("VIPTV/cache/mpv")
            Platform.isMac() -> home.resolve("Library/Caches/VIPTV/mpv")
            else -> (System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let(Path::of) ?: home.resolve(".cache")).resolve("viptv/mpv")
        }
        val identity = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
        val target = Files.createDirectories(cacheRoot.resolve(identity))
        if (!Platform.isWindows()) Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwx------"))
        // Reuse immutable native bytes: Windows cannot delete a loaded DLL at exit.
        FileChannel.open(target.resolve(".extract.lock"), CREATE, WRITE).use { channel ->
            val deadline = System.nanoTime() + 10_000_000_000L
            var lock = channel.tryLock()
            while (lock == null) {
                check(System.nanoTime() < deadline) { "mpv runtime extraction timed out" }
                Thread.sleep(10)
                lock = channel.tryLock()
            }
            lock.use {
                for ((name, hash) in files) {
                    val relative = Path.of(name)
                    require(!relative.isAbsolute && relative.nameCount == 1 && name !in setOf(".", "..") && '/' !in name && '\\' !in name)
                    val output = target.resolve(relative)
                    fun digestFile(): String {
                        val digest = MessageDigest.getInstance("SHA-256")
                        DigestInputStream(Files.newInputStream(output), digest).use { it.copyTo(OutputStream.nullOutputStream()) }
                        return digest.digest().joinToString("") { "%02x".format(it) }
                    }
                    if (Files.isRegularFile(output) && digestFile() == hash.jsonPrimitive.content) continue
                    val temporary = Files.createTempFile(target, ".extract-", ".tmp")
                    try {
                        val digest = MessageDigest.getInstance("SHA-256")
                        val input = MpvRuntime::class.java.getResourceAsStream(prefix + name) ?: error("Packaged mpv file missing")
                        DigestInputStream(input, digest).use { source -> Files.newOutputStream(temporary).use { source.copyTo(it) } }
                        val actual = digest.digest().joinToString("") { "%02x".format(it) }
                        check(actual == hash.jsonPrimitive.content) { "Packaged mpv checksum mismatch" }
                        Files.move(temporary, output, REPLACE_EXISTING, ATOMIC_MOVE)
                    } finally { Files.deleteIfExists(temporary) }
                }
            }
        }
        val path = target.resolve(library).toString()
        if (Platform.isWindows()) {
            // Search dependent DLLs beside this verified library, never in the working directory.
            val loader = Native.load("kernel32", WindowsLoader::class.java)
            check(loader.LoadLibraryExW(WString(path), null, 0x00000100 or 0x00001000) != null) { "Could not load packaged mpv dependencies" }
        }
        cached = path
        return path
    }
}

internal fun mpvCheck(code: Int) { check(code >= 0) { "mpv error $code" } }

// ABI layout from mpv/client.h; decode borrowed nodes before their owner advances.
internal fun mpvNode(pointer: Pointer): JsonElement = when (pointer.getInt(8)) {
    1 -> JsonPrimitive(pointer.getPointer(0)?.getString(0, "UTF-8") ?: "")
    3 -> JsonPrimitive(pointer.getInt(0) != 0)
    4 -> JsonPrimitive(pointer.getLong(0))
    5 -> pointer.getDouble(0).takeIf { it.isFinite() }?.let(::JsonPrimitive) ?: JsonNull
    7, 8 -> {
        val list = pointer.getPointer(0)
        val count = list.getInt(0).also { require(it in 0..100000) }
        val values = list.getPointer(8)
        if (pointer.getInt(8) == 7) JsonArray((0 until count).map { mpvNode(values.share(it * 16L)) })
        else {
            val keys = list.getPointer(16)
            JsonObject((0 until count).associate { i -> keys.getPointer(i * 8L).getString(0, "UTF-8") to mpvNode(values.share(i * 16L)) })
        }
    }
    else -> JsonNull
}

internal class MpvParams : AutoCloseable {
    private val owned = mutableListOf<Memory>()
    private val params = mutableListOf<Pair<Int, Pointer>>()
    fun memory(bytes: Long) = Memory(bytes).also { it.clear(); owned.add(it) }
    fun text(value: String): Pointer = memory(value.toByteArray(Charsets.UTF_8).size + 1L).also { it.setString(0, value, "UTF-8") }
    fun int(value: Int): Pointer = memory(4).also { it.setInt(0, value) }
    fun long(value: Long): Pointer = memory(8).also { it.setLong(0, value) }
    fun add(type: Int, value: Pointer) { params.add(type to value) }
    fun pointer(): Pointer = memory((params.size + 1L) * 16).also { result ->
        params.forEachIndexed { i, (type, data) -> result.setInt(i * 16L, type); result.setPointer(i * 16L + 8, data) }
    }
    override fun close() { owned.asReversed().forEach { it.close() } }
}
