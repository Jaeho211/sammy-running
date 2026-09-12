package com.sammy.running.core

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PublishingTest {
    private class FakeTransport(vararg responses: GitHubResponse) : GitHubTransport {
        private val pending = ArrayDeque(responses.toList())
        val requests = mutableListOf<GitHubRequest>()
        override fun execute(request: GitHubRequest): GitHubResponse {
            requests += request
            return pending.removeFirst()
        }
    }

    private val config = GitHubConfig("runner", "run-log", "main", "secret-token")
    private val mapped = RunMapper().map(Fixtures.runs().first())
    private val json = RunMapper().toJson(mapped)

    @Test fun `creates a file with the required path message branch and auth`() {
        val transport = FakeTransport(GitHubResponse(404, "{}"), GitHubResponse(201, "{}"))
        val result = GitHubPublisher(transport).publish(config, mapped, json)

        assertEquals(PublishResult.Published("data/runs/${mapped.id}.json"), result)
        assertEquals(listOf("GET", "PUT"), transport.requests.map { it.method })
        assertTrue(transport.requests.first().url.endsWith("data/runs/${mapped.id}.json?ref=main"))
        assertEquals("Bearer secret-token", transport.requests.last().headers["Authorization"])
        assertEquals("sammy-running-android", transport.requests.last().headers["User-Agent"])
        val body = JsonParser.parseString(transport.requests.last().body).asJsonObject
        assertEquals("Add run: ${mapped.date} 3.24km", body["message"].asString)
        assertEquals("main", body["branch"].asString)
        assertEquals(json, String(Base64.getDecoder().decode(body["content"].asString), StandardCharsets.UTF_8))
    }

    @Test fun `identical remote content recovers published state without a put`() {
        val remote = """{"content":"${Base64.getEncoder().encodeToString(json.toByteArray())}"}"""
        val transport = FakeTransport(GitHubResponse(200, remote))
        val result = GitHubPublisher(transport).publish(config, mapped, json)
        assertEquals(PublishResult.Published("data/runs/${mapped.id}.json", true), result)
        assertEquals(1, transport.requests.size)
    }

    @Test fun `different remote content reports collision and never overwrites`() {
        val other = Base64.getEncoder().encodeToString("{\"id\":\"different\"}".toByteArray())
        val transport = FakeTransport(GitHubResponse(200, """{"content":"$other"}"""))
        assertIs<PublishResult.Collision>(GitHubPublisher(transport).publish(config, mapped, json))
        assertEquals(1, transport.requests.size)
    }

    @Test fun `race after create is resolved by reading remote content`() {
        val remote = """{"content":"${Base64.getEncoder().encodeToString(json.toByteArray())}"}"""
        val transport = FakeTransport(GitHubResponse(404, "{}"), GitHubResponse(422, "{}"), GitHubResponse(200, remote))
        assertEquals(PublishResult.Published("data/runs/${mapped.id}.json", true), GitHubPublisher(transport).publish(config, mapped, json))
        assertEquals(listOf("GET", "PUT", "GET"), transport.requests.map { it.method })
    }

    @Test fun `published identity prefers session id and has deterministic fallback`() {
        val run = Fixtures.runs().first()
        assertEquals(PublishedRunIdentity.key(run), PublishedRunIdentity.key(run.copy(distanceMeters = 1.0)))
        val withoutId = run.copy(sessionId = "")
        assertEquals(PublishedRunIdentity.key(withoutId), PublishedRunIdentity.key(withoutId.copy()))
        assertNotEquals(PublishedRunIdentity.key(withoutId), PublishedRunIdentity.key(withoutId.copy(durationSeconds = 1.0)))
    }

    @Test fun `device flow requests a code without a client secret`() {
        val response = """{
            "device_code":"device-secret","user_code":"ABCD-EFGH",
            "verification_uri":"https://github.com/login/device","expires_in":900,"interval":5
        }"""
        val transport = FakeTransport(GitHubResponse(200, response))
        val code = GitHubDeviceAuthClient(transport).requestCode("client-id")
        assertEquals("ABCD-EFGH", code.userCode)
        assertEquals("client_id=client-id", transport.requests.single().body)
        assertTrue(transport.requests.single().body?.contains("secret") == false)
    }

    @Test fun `device polling handles pending slowdown and authorization`() {
        val authorized = """{
            "access_token":"access","expires_in":28800,"refresh_token":"refresh",
            "refresh_token_expires_in":15897600,"token_type":"bearer"
        }"""
        val transport = FakeTransport(
            GitHubResponse(200, "{\"error\":\"authorization_pending\"}"),
            GitHubResponse(200, "{\"error\":\"slow_down\"}"),
            GitHubResponse(200, authorized),
        )
        val client = GitHubDeviceAuthClient(transport)
        assertEquals(DevicePollResult.Pending, client.poll("client", "device"))
        assertEquals(DevicePollResult.SlowDown, client.poll("client", "device"))
        val result = assertIs<DevicePollResult.Authorized>(client.poll("client", "device"))
        assertEquals("access", result.tokens.accessToken)
        assertEquals("refresh", result.tokens.refreshToken)
        assertTrue(transport.requests.all { it.body?.contains("client_secret") == false })
    }

    @Test fun `refresh uses device flow client id and refresh token`() {
        val transport = FakeTransport(GitHubResponse(200, "{\"access_token\":\"new-access\",\"expires_in\":28800}"))
        val tokens = GitHubDeviceAuthClient(transport).refresh("client", "refresh-value")
        assertEquals("new-access", tokens.accessToken)
        val body = transport.requests.single().body.orEmpty()
        assertTrue(body.contains("grant_type=refresh_token"))
        assertTrue(body.contains("refresh_token=refresh-value"))
        assertTrue(!body.contains("client_secret"))
    }
}
