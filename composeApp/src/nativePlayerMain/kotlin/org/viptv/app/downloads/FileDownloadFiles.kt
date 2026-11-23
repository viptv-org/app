package org.viptv.app.downloads

import java.io.File
import java.io.FileOutputStream

internal class FileDownloadFiles(private val directory: File, private val bookFile: File = File(directory, "downloads.json")) : DownloadFiles {
    init { directory.mkdirs() }

    override fun size(name: String): Long = File(directory, name).takeIf { it.isFile }?.length() ?: 0L

    override fun open(name: String, append: Boolean): DownloadSink {
        val stream = FileOutputStream(File(directory, name), append)
        return object : DownloadSink {
            override fun write(bytes: ByteArray, count: Int) = stream.write(bytes, 0, count)
            override fun close() = stream.close()
        }
    }

    override fun write(name: String, bytes: ByteArray) = File(directory, name).writeBytes(bytes)

    override fun read(name: String): ByteArray? = File(directory, name).takeIf { it.isFile }?.readBytes()

    override fun delete(name: String) { File(directory, name).delete() }

    override fun path(name: String): String = File(directory, name).absolutePath

    override fun freeBytes(): Long = directory.usableSpace

    override fun readBook(): String? = bookFile.takeIf { it.isFile }?.readText()

    override fun writeBook(text: String) {
        val staged = File(bookFile.parentFile, bookFile.name + ".tmp")
        staged.writeText(text)
        if (!staged.renameTo(bookFile)) { bookFile.delete(); staged.renameTo(bookFile) }
    }
}
