package org.viptv.app.connection

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Security.*

private const val SESSION_SERVICE = "viptv.remote.session"
private const val SESSION_ACCOUNT = "viptv.session"
private const val LEGACY_DEFAULTS_KEY = "viptv.remote.session"

// Mirrors the browser's localStorage-backed BrowserRemoteSessionStore shape.
private object IOSRemoteSessionStore : RemoteSessionStore {
    override suspend fun read(): SavedRemoteSession? {
        val keychain = keychainRead()
        if (keychain != null) return keychain.toSession()
        val legacy = NSUserDefaults.standardUserDefaults.stringForKey(LEGACY_DEFAULTS_KEY) ?: return null
        keychainWrite(legacy)
        NSUserDefaults.standardUserDefaults.removeObjectForKey(LEGACY_DEFAULTS_KEY)
        return legacy.toSession()
    }

    override suspend fun save(session: SavedRemoteSession) {
        keychainWrite("${session.origin}\n${session.refreshToken}")
        NSUserDefaults.standardUserDefaults.removeObjectForKey(LEGACY_DEFAULTS_KEY)
    }

    override suspend fun clear() {
        keychainDelete()
        NSUserDefaults.standardUserDefaults.removeObjectForKey(LEGACY_DEFAULTS_KEY)
    }
}

private fun String.toSession(): SavedRemoteSession? {
    val parts = split('\n', limit = 2)
    if (parts.size != 2) return null
    return SavedRemoteSession(origin = parts[0], refreshToken = parts[1])
}

@OptIn(ExperimentalForeignApi::class)
private fun cfString(text: String): CFStringRef? =
    CFStringCreateWithCString(kCFAllocatorDefault, text, kCFStringEncodingUTF8)

@OptIn(ExperimentalForeignApi::class)
private fun keychainQuery(data: CFDataRef? = null, returnData: Boolean = false): CFMutableDictionaryRef? {
    val service = cfString(SESSION_SERVICE)
    val account = cfString(SESSION_ACCOUNT)
    val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null)
    CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
    CFDictionaryAddValue(query, kSecAttrService, service)
    CFDictionaryAddValue(query, kSecAttrAccount, account)
    if (data != null) CFDictionaryAddValue(query, kSecValueData, data)
    if (returnData) {
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
    }
    CFRelease(service)
    CFRelease(account)
    return query
}

@OptIn(ExperimentalForeignApi::class)
private fun keychainWrite(raw: String) {
    val bytes = raw.encodeToByteArray()
    val data = bytes.usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret<UByteVar>(), bytes.size.toLong())
    }
    val deleteQuery = keychainQuery()
    SecItemDelete(deleteQuery)
    CFRelease(deleteQuery)
    val addQuery = keychainQuery(data = data)
    CFDictionaryAddValue(addQuery, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
    SecItemAdd(addQuery, null)
    CFRelease(addQuery)
    CFRelease(data)
}

@OptIn(ExperimentalForeignApi::class)
private fun keychainRead(): String? = memScoped {
    val result = alloc<CFTypeRefVar>()
    val query = keychainQuery(returnData = true)
    val status = SecItemCopyMatching(query, result.ptr)
    CFRelease(query)
    if (status != errSecSuccess) return@memScoped null
    val data = result.value ?: return@memScoped null
    (CFBridgingRelease(data) as? NSData)?.let { stored ->
        val length = stored.length.toInt()
        val text = if (length == 0) ByteArray(0) else stored.bytes?.readBytes(length)
        text?.decodeToString()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun keychainDelete() {
    val query = keychainQuery()
    SecItemDelete(query)
    CFRelease(query)
}

actual fun remoteSessionStore(): RemoteSessionStore = IOSRemoteSessionStore
