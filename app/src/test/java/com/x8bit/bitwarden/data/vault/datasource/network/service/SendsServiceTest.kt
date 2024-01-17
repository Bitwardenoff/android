package com.x8bit.bitwarden.data.vault.datasource.network.service

import android.net.Uri
import com.x8bit.bitwarden.data.platform.base.BaseServiceTest
import com.x8bit.bitwarden.data.vault.datasource.network.api.AzureApi
import com.x8bit.bitwarden.data.vault.datasource.network.api.SendsApi
import com.x8bit.bitwarden.data.vault.datasource.network.model.SendFileResponseJson
import com.x8bit.bitwarden.data.vault.datasource.network.model.SendTypeJson
import com.x8bit.bitwarden.data.vault.datasource.network.model.UpdateSendResponseJson
import com.x8bit.bitwarden.data.vault.datasource.network.model.createMockSend
import com.x8bit.bitwarden.data.vault.datasource.network.model.createMockSendJsonRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.create
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SendsServiceTest : BaseServiceTest() {
    private val clock: Clock = Clock.fixed(
        Instant.parse("2023-10-27T12:00:00Z"),
        ZoneOffset.UTC,
    )
    private val azureApi: AzureApi = retrofit.create()
    private val sendsApi: SendsApi = retrofit.create()

    private val sendsService: SendsService = SendsServiceImpl(
        azureApi = azureApi,
        sendsApi = sendsApi,
        json = json,
        clock = clock,
    )

    @BeforeEach
    fun setup() {
        mockkStatic(Uri::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `createFileSend should return the correct response`() = runTest {
        val response = SendFileResponseJson(
            url = "www.test.com",
            fileUploadType = SendFileResponseJson.FileUploadType.AZURE,
            sendResponse = createMockSend(number = 1, type = SendTypeJson.FILE),
        )
        server.enqueue(MockResponse().setBody(CREATE_FILE_SEND_SUCCESS_JSON))
        val result = sendsService.createFileSend(
            body = createMockSendJsonRequest(number = 1, type = SendTypeJson.FILE),
        )
        assertEquals(response, result.getOrThrow())
    }

    @Test
    fun `createSend should return the correct response`() = runTest {
        server.enqueue(MockResponse().setBody(CREATE_UPDATE_SEND_SUCCESS_JSON))
        val result = sendsService.createSend(
            body = createMockSendJsonRequest(number = 1, type = SendTypeJson.TEXT),
        )
        assertEquals(
            createMockSend(number = 1),
            result.getOrThrow(),
        )
    }

    @Test
    fun `updateSend with success response should return a Success with the correct send`() =
        runTest {
            server.enqueue(MockResponse().setBody(CREATE_UPDATE_SEND_SUCCESS_JSON))
            val result = sendsService.updateSend(
                sendId = "send-id-1",
                body = createMockSendJsonRequest(number = 1),
            )
            assertEquals(
                UpdateSendResponseJson.Success(
                    send = createMockSend(number = 1),
                ),
                result.getOrThrow(),
            )
        }

    @Test
    fun `updateSend with an invalid response should return an Invalid with the correct data`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(400).setBody(UPDATE_SEND_INVALID_JSON))
            val result = sendsService.updateSend(
                sendId = "send-id-1",
                body = createMockSendJsonRequest(number = 1),
            )
            assertEquals(
                UpdateSendResponseJson.Invalid(
                    message = "You do not have permission to edit this.",
                    validationErrors = null,
                ),
                result.getOrThrow(),
            )
        }

    @Test
    fun `uploadFile with Azure uploadFile success should return send`() = runTest {
        val url = "www.test.com"
        setupMockUri(url = url, queryParams = mapOf("sv" to "2024-04-03"))
        val mockSend = createMockSend(number = 1, type = SendTypeJson.FILE)
        val sendFileResponse = SendFileResponseJson(
            url = url,
            fileUploadType = SendFileResponseJson.FileUploadType.AZURE,
            sendResponse = mockSend,
        )
        val encryptedFile = byteArrayOf()
        server.enqueue(MockResponse().setResponseCode(201))

        val result = sendsService.uploadFile(
            sendFileResponse = sendFileResponse,
            encryptedFile = encryptedFile,
        )

        assertEquals(mockSend, result.getOrThrow())
    }

    @Test
    fun `uploadFile with Direct uploadFile success should return send`() = runTest {
        val mockSend = createMockSend(number = 1, type = SendTypeJson.FILE)
        val sendFileResponse = SendFileResponseJson(
            url = "www.test.com",
            fileUploadType = SendFileResponseJson.FileUploadType.DIRECT,
            sendResponse = mockSend,
        )
        val encryptedFile = byteArrayOf()
        server.enqueue(MockResponse().setResponseCode(201))

        val result = sendsService.uploadFile(
            sendFileResponse = sendFileResponse,
            encryptedFile = encryptedFile,
        )

        assertEquals(mockSend, result.getOrThrow())
    }

    @Test
    fun `deleteSend should return a Success with the correct data`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = sendsService.deleteSend(sendId = "send-id-1")
        assertEquals(Unit, result.getOrThrow())
    }

    @Test
    fun `removeSendPassword with success response should return a Success with the correct send`() =
        runTest {
            server.enqueue(MockResponse().setBody(CREATE_UPDATE_SEND_SUCCESS_JSON))
            val result = sendsService.removeSendPassword(sendId = "send-id-1")
            assertEquals(
                UpdateSendResponseJson.Success(send = createMockSend(number = 1)),
                result.getOrThrow(),
            )
        }

    @Suppress("MaxLineLength")
    @Test
    fun `removeSendPassword with an invalid response should return an Invalid with the correct data`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(400).setBody(UPDATE_SEND_INVALID_JSON))
            val result = sendsService.removeSendPassword(sendId = "send-id-1")
            assertEquals(
                UpdateSendResponseJson.Invalid(
                    message = "You do not have permission to edit this.",
                    validationErrors = null,
                ),
                result.getOrThrow(),
            )
        }

    private fun setupMockUri(
        url: String,
        queryParams: Map<String, String>,
    ): Uri {
        val mockUri = mockk<Uri> {
            queryParams.forEach {
                every { getQueryParameter(it.key) } returns it.value
            }
        }
        every { Uri.parse(url) } returns mockUri
        return mockUri
    }
}

private const val CREATE_UPDATE_SEND_SUCCESS_JSON = """
{
  "id": "mockId-1",
  "accessId": "mockAccessId-1",
  "type": 1,
  "name": "mockName-1",
  "notes": "mockNotes-1",
  "file": {
    "id": "mockId-1",
    "fileName": "mockFileName-1",
    "size": 1,
    "sizeName": "mockSizeName-1"
  },
  "text": {
    "text": "mockText-1",
    "hidden": false
  },
  "key": "mockKey-1",
  "maxAccessCount": 1,
  "accessCount": 1,
  "password": "mockPassword-1",
  "disabled": false,
  "revisionDate": "2023-10-27T12:00:00.00Z",
  "expirationDate": "2023-10-27T12:00:00.00Z",
  "deletionDate": "2023-10-27T12:00:00.00Z",
  "hideEmail": false
}
"""

private const val CREATE_FILE_SEND_SUCCESS_JSON = """
{
  "url": "www.test.com",
  "fileUploadType": "1",
  "sendResponse": {
    "id": "mockId-1",
    "accessId": "mockAccessId-1",
    "type": 1,
    "name": "mockName-1",
    "notes": "mockNotes-1",
    "file": {
      "id": "mockId-1",
      "fileName": "mockFileName-1",
      "size": 1,
      "sizeName": "mockSizeName-1"
    },
    "text": {
      "text": "mockText-1",
      "hidden": false
    },
    "key": "mockKey-1",
    "maxAccessCount": 1,
    "accessCount": 1,
    "password": "mockPassword-1",
    "disabled": false,
    "revisionDate": "2023-10-27T12:00:00.00Z",
    "expirationDate": "2023-10-27T12:00:00.00Z",
    "deletionDate": "2023-10-27T12:00:00.00Z",
    "hideEmail": false
  }
}
"""

private const val UPDATE_SEND_INVALID_JSON = """
{
  "message": "You do not have permission to edit this.",
  "validationErrors": null
}
"""
