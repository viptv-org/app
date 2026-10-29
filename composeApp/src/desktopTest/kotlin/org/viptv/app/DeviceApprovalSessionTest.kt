package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.DeviceApprovalController
import kotlin.test.*

class DeviceApprovalSessionTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private suspend fun DeviceApprovalController.idle() = withTimeout(2_000) { while (isBusy()) yield() }

    @Test fun restoresAnExpiredAccessCookieUsingTheRefreshCookieWithoutMediaRequests() = runBlocking<Unit> {
        val paths = mutableListOf<String>(); var refreshed = false; var statuses = 0
        val controller = DeviceApprovalController(this) {
            val client = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath; paths += path
                if (path in setOf("/api/auth/status", "/api/auth/me")) assertEquals(HttpMethod.Get, request.method)
                if (path == "/api/auth/refresh") assertEquals(HttpMethod.Post, request.method)
                val (body, status) = when (path) {
                    "/api/auth/status" -> """{"authenticated":true,"csrf_token":"status${++statuses}"}""" to HttpStatusCode.OK
                    "/api/auth/me" -> if (!refreshed) "{}" to HttpStatusCode.Unauthorized
                        else """{"account":{"username":"member"}}""" to HttpStatusCode.OK
                    "/api/auth/refresh" -> {
                        assertEquals("status2", request.headers["x-csrf-token"])
                        refreshed = true
                        """{"csrf_token":"rotated"}""" to HttpStatusCode.OK
                    }
                    else -> error("Unexpected non-account request $path")
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            RemoteConnection("https://watch.example", store, client, browserMode = true)
        }
        try {
            val done = CompletableDeferred<Unit>(); controller.restore { done.complete(Unit) }
            withTimeout(2_000) { done.await() }; controller.idle()
            assertEquals("member", controller.username)
            assertEquals(listOf("/api/auth/status", "/api/auth/me", "/api/auth/status", "/api/auth/refresh", "/api/auth/me"), paths)
        } finally { controller.reset() }
    }

    @Test fun approvalAfterExpiryRefreshesOnceAndUsesTheNewCsrf() = runBlocking<Unit> {
        var expired = false; var refreshed = false; var approvals = 0
        val controller = DeviceApprovalController(this) {
            val client = HttpClient(MockEngine { request ->
                val (body, status) = when (request.url.encodedPath) {
                    "/api/auth/login" -> """{"csrf_token":"login"}""" to HttpStatusCode.OK
                    "/api/auth/status" -> """{"authenticated":true,"csrf_token":"status"}""" to HttpStatusCode.OK
                    "/api/auth/refresh" -> {
                        assertEquals("status", request.headers["x-csrf-token"]); assertFalse(refreshed); refreshed = true
                        """{"csrf_token":"rotated"}""" to HttpStatusCode.OK
                    }
                    "/api/auth/device/approve" -> {
                        if (expired && !refreshed) "{}" to HttpStatusCode.Unauthorized else {
                            assertEquals("rotated", request.headers["x-csrf-token"]); approvals++
                            "{}" to HttpStatusCode.OK
                        }
                    }
                    else -> error("Unexpected request ${request.url.encodedPath}")
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            RemoteConnection("https://watch.example", store, client, browserMode = true)
        }
        try {
            val signedIn = CompletableDeferred<String?>(); controller.signIn("member", "password") { signedIn.complete(it) }
            withTimeout(2_000) { assertNull(signedIn.await()) }; controller.idle(); expired = true
            val result = CompletableDeferred<Result<Unit>>(); controller.approve("00123456") { result.complete(it) }
            withTimeout(2_000) { result.await().getOrThrow() }; controller.idle()
            assertEquals(1, approvals); assertTrue(refreshed)
        } finally { controller.reset() }
    }

    @Test fun resetWaitsForRotationBeforeTheReplacementAccountSignsIn() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var oldCallback = false; var factories = 0; var refreshComplete = false
        val controller = DeviceApprovalController(this) {
            val number = ++factories
            val client = HttpClient(MockEngine { request ->
                val (body, status) = when (request.url.encodedPath) {
                    "/api/auth/status" -> """{"authenticated":true,"csrf_token":"status"}""" to HttpStatusCode.OK
                    "/api/auth/me" -> "{}" to HttpStatusCode.Unauthorized
                    "/api/auth/refresh" -> {
                        entered.complete(Unit); release.await(); refreshComplete = true
                        """{"csrf_token":"rotated"}""" to HttpStatusCode.OK
                    }
                    "/api/auth/login" -> {
                        assertEquals(2, number); assertTrue(refreshComplete)
                        """{"csrf_token":"new-account"}""" to HttpStatusCode.OK
                    }
                    else -> error("Unexpected request")
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            RemoteConnection("https://watch.example", store, client, browserMode = true)
        }
        try {
            controller.restore { oldCallback = true }
            withTimeout(2_000) { entered.await() }
            controller.reset()
            val done = CompletableDeferred<String?>(); controller.signIn("replacement", "password") { done.complete(it) }
            yield(); assertEquals(1, factories)
            release.complete(Unit)
            withTimeout(2_000) { assertNull(done.await()) }; controller.idle()
            assertFalse(oldCallback); assertEquals("replacement", controller.username)
        } finally { release.complete(Unit); controller.reset() }
    }
    @Test fun signOutRefreshesAnExpiredCookieBeforeServerRevocation() = runBlocking<Unit> {
        var authenticated = true; var refreshes = 0; var loggedOut = false
        val controller = DeviceApprovalController(this) {
            val client = HttpClient(MockEngine { request ->
                val body = when (request.url.encodedPath) {
                    "/api/auth/login" -> """{"csrf_token":"login"}"""
                    "/api/auth/status" -> """{"authenticated":$authenticated,"csrf_token":"status"}"""
                    "/api/auth/refresh" -> { refreshes++; """{"csrf_token":"rotated"}""" }
                    "/api/auth/logout" -> {
                        assertEquals("rotated", request.headers["x-csrf-token"])
                        authenticated = false; loggedOut = true; "{}"
                    }
                    else -> error("Unexpected account/media request")
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            RemoteConnection("https://watch.example", store, client, browserMode = true)
        }
        try {
            val signedIn = CompletableDeferred<String?>(); controller.signIn("member", "password") { signedIn.complete(it) }
            withTimeout(2_000) { signedIn.await() }; controller.idle()
            val signedOut = CompletableDeferred<Unit>(); controller.signOut { signedOut.complete(Unit) }
            withTimeout(2_000) { signedOut.await() }; controller.idle()
            assertTrue(loggedOut); assertEquals(1, refreshes); assertNull(controller.username)
            val restored = CompletableDeferred<Unit>(); controller.restore { restored.complete(Unit) }
            withTimeout(2_000) { restored.await() }; controller.idle(); assertNull(controller.username)
        } finally { controller.reset() }
    }

    @Test fun resetDuringAFailedRefreshDoesNotInvokeTheOldErrorCallback() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var oldCallback = false; var factories = 0
        val controller = DeviceApprovalController(this) {
            val number = ++factories
            val client = HttpClient(MockEngine { request ->
                val (body, status) = when (request.url.encodedPath) {
                    "/api/auth/status" -> """{"authenticated":true,"csrf_token":"status"}""" to HttpStatusCode.OK
                    "/api/auth/me" -> "{}" to HttpStatusCode.Unauthorized
                    "/api/auth/refresh" -> {
                        entered.complete(Unit); release.await()
                        "{}" to HttpStatusCode.InternalServerError
                    }
                    "/api/auth/login" -> {
                        assertEquals(2, number)
                        """{"csrf_token":"replacement"}""" to HttpStatusCode.OK
                    }
                    else -> error("Unexpected request")
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            RemoteConnection("https://watch.example", store, client, browserMode = true)
        }
        try {
            controller.restore { oldCallback = true }
            withTimeout(2_000) { entered.await() }
            controller.reset()
            val signedIn = CompletableDeferred<String?>(); controller.signIn("replacement", "password") { signedIn.complete(it) }
            release.complete(Unit)
            withTimeout(2_000) { assertNull(signedIn.await()) }; controller.idle()
            assertFalse(oldCallback); assertEquals("replacement", controller.username)
        } finally { release.complete(Unit); controller.reset() }
    }

    @Test fun restrictedLookupUnlocksAndRetriesWithoutApprovingTheDevice() = runBlocking<Unit> {
        var unlocked = false; var lookups = 0; var approvals = 0
        val controller = DeviceApprovalController(this) {
            val client = HttpClient(MockEngine { request ->
                val (body, status) = when (request.url.encodedPath) {
                    "/api/auth/device/lookup" -> {
                        lookups++
                        if (unlocked) """{"device":{"device_name":"Living room","status":"pending"},"expires_at":12345}""" to HttpStatusCode.OK
                        else "{}" to HttpStatusCode.Forbidden
                    }
                    "/api/parent/status" -> """{"pin_configured":true,"restricted":true,"unlocked":false}""" to HttpStatusCode.OK
                    "/api/parent/unlock" -> {
                        unlocked = true
                        "{}" to HttpStatusCode.OK
                    }
                    "/api/auth/device/approve" -> { approvals++; "{}" to HttpStatusCode.OK }
                    else -> error("Unexpected media/account request ${request.url.encodedPath}")
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            RemoteConnection("https://watch.example", store, client, browserMode = true)
        }
        try {
            val denied = CompletableDeferred<Result<DeviceApproval>>()
            controller.lookup("00123456") { denied.complete(it) }
            assertIs<ParentAuthorizationRequired>(withTimeout(2_000) { denied.await() }.exceptionOrNull()); controller.idle()
            val retried = CompletableDeferred<Result<DeviceApproval>>()
            controller.unlockParent("012345") { result ->
                result.getOrThrow()
                controller.lookup("00123456") { retried.complete(it) }
            }
            assertEquals("Living room", withTimeout(2_000) { retried.await() }.getOrThrow().deviceName)
            controller.idle(); assertEquals(2, lookups); assertEquals(0, approvals)
        } finally { controller.reset() }
    }

    @Test fun failedRevocationKeepsAccountAndAllowsParentAuthorizedRetry() = runBlocking<Unit> {
        var unlocked = false; var logouts = 0; var signedOut = false
        val controller = DeviceApprovalController(this) {
            val client = HttpClient(MockEngine { request ->
                val (body, status) = when (request.url.encodedPath) {
                    "/api/auth/login" -> """{"csrf_token":"login"}""" to HttpStatusCode.OK
                    "/api/auth/status" -> """{"authenticated":true,"csrf_token":"status"}""" to HttpStatusCode.OK
                    "/api/auth/refresh" -> """{"csrf_token":"rotated"}""" to HttpStatusCode.OK
                    "/api/auth/logout" -> { logouts++; "{}" to if (unlocked) HttpStatusCode.OK else HttpStatusCode.Forbidden }
                    "/api/parent/status" -> """{"pin_configured":true,"restricted":true,"unlocked":false}""" to HttpStatusCode.OK
                    "/api/parent/unlock" -> { unlocked = true; "{}" to HttpStatusCode.OK }
                    else -> error("Unexpected request ${request.url.encodedPath}")
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            RemoteConnection("https://watch.example", store, client, browserMode = true)
        }
        try {
            val signedIn = CompletableDeferred<String?>(); controller.signIn("member", "password") { signedIn.complete(it) }
            assertNull(withTimeout(2_000) { signedIn.await() }); controller.idle()
            val denied = CompletableDeferred<Throwable>()
            controller.signOut(onFailure = { denied.complete(it) }) { signedOut = true }
            assertIs<ParentAuthorizationRequired>(withTimeout(2_000) { denied.await() }); controller.idle()
            assertEquals("member", controller.username); assertFalse(signedOut)
            val done = CompletableDeferred<Unit>()
            controller.unlockParent("012345") { result ->
                result.getOrThrow(); controller.signOut { signedOut = true; done.complete(Unit) }
            }
            withTimeout(2_000) { done.await() }; controller.idle()
            assertTrue(signedOut); assertNull(controller.username); assertEquals(2, logouts)
        } finally { controller.reset() }
    }

    @Test fun unrelatedForbiddenDoesNotAskForParentPin() = runBlocking<Unit> {
        val controller = DeviceApprovalController(this) {
            val client = HttpClient(MockEngine { request ->
                val status = if (request.url.encodedPath == "/api/parent/status") HttpStatusCode.OK else HttpStatusCode.Forbidden
                respond(if (status == HttpStatusCode.OK) """{"pin_configured":true,"restricted":false,"unlocked":false}""" else "{}",
                    status, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            RemoteConnection("https://watch.example", store, client, browserMode = true)
        }
        try {
            val denied = CompletableDeferred<Result<Unit>>(); controller.approve("00123456") { denied.complete(it) }
            assertEquals(403, assertIs<RemoteFailure>(withTimeout(2_000) { denied.await() }.exceptionOrNull()).status)
        } finally { controller.reset() }
    }

}
