package org.viptv.app

import kotlinx.serialization.json.*
import kotlin.test.*
import org.viptv.app.connection.*
import com.sun.jna.Platform
import java.nio.file.Files

class DesktopTorrentWorkerTest {
    private fun fixture(body: String): DesktopTorrentWorker = DesktopTorrentWorker(listOf("python3", "-u", "-c",
        "import json,sys,time\nprint('{\"version\":2,\"ready\":true}',flush=True)\n" + body))

    @Test fun readinessIsConsumedBeforeFirstControlReply() {
        if (!Platform.isLinux()) return
        val worker = fixture("for line in sys.stdin:\n x=json.loads(line)\n print(json.dumps({'version':2,'ok':True,'op':x['op']}),flush=True)")
        try {
            val reply = worker.call(buildJsonObject { put("op", "open") })
            assertTrue(reply.getValue("ok").jsonPrimitive.boolean)
            assertEquals("open", reply.getValue("op").jsonPrimitive.content)
        } finally { worker.terminate() }
        assertFalse(worker.isAlive())
        assertTrue(worker.processHasExited())
    }

    @Test fun forcedSettlementReplyTerminatesTheActualChildAndRemainsObservable() {
        if (!Platform.isLinux()) return
        val worker = fixture("sys.stdin.readline();print(json.dumps({'version':2,'ok':False,'terminate':True,'error':'settlement_timeout'}),flush=True);time.sleep(60)")
        try {
            assertFalse(worker.processHasExited())
            val reply = worker.call(buildJsonObject { put("op", "close") })
            assertFalse(reply.getValue("ok").jsonPrimitive.boolean)
            assertTrue(reply.getValue("terminate").jsonPrimitive.boolean)
            assertEquals("settlement_timeout", reply.getValue("error").jsonPrimitive.content)
            assertTrue(worker.processHasExited())
        } finally { worker.terminate() }
    }

    @Test fun stuckOrMalformedRepliesTerminateTheOwnedWorker() {
        if (!Platform.isLinux()) return
        for (body in listOf("sys.stdin.readline();time.sleep(60)",
            "sys.stdin.readline();print('{\"version\":1,\"ok\":true}',flush=True)",
            "sys.stdin.readline();print('x'*(6*1024*1024+1),flush=True)",
            "sys.stdin.readline();sys.exit(0)")) {
            val worker = fixture(body)
            try { assertFails { worker.call(buildJsonObject { put("op", "observe") }, 300) } }
            finally { worker.terminate() }
            assertFalse(worker.isAlive())
            assertTrue(worker.processHasExited())
        }
    }

    @Test fun packagedWorkerOpensAndReleasesItsCache() {
        val host = assertNotNull(platformTorrentRuntime(), "packaged worker unavailable")
        val before = host.elapsedMillis()
        assertTrue(host.elapsedMillis() >= before)
        val directory = Files.createTempDirectory("viptv-worker-test-")
        try {
            repeat(2) {
                val worker = host.connect()
                try {
                    val opened = worker.call(buildJsonObject { put("op", "open"); put("config", buildJsonObject { put("cache_dir", directory.toString()) }) }, 8_000)
                    assertTrue(opened.getValue("ok").jsonPrimitive.boolean)
                    val stopped = worker.call(buildJsonObject { put("op", "shutdown") })
                    assertTrue(stopped.getValue("ok").jsonPrimitive.boolean)
                } finally { worker.terminate() }
            }
        } finally { directory.toFile().deleteRecursively() }
    }
}
