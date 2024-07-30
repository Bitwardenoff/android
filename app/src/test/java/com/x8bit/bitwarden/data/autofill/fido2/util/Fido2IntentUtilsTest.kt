package com.x8bit.bitwarden.data.autofill.fido2.util

import android.content.Intent
import android.content.pm.SigningInfo
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderCreateCredentialRequest
import androidx.credentials.provider.ProviderGetCredentialRequest
import com.x8bit.bitwarden.data.autofill.fido2.model.Fido2CredentialAssertionRequest
import com.x8bit.bitwarden.data.autofill.fido2.model.Fido2CredentialRequest
import com.x8bit.bitwarden.data.platform.util.isBuildVersionBelow
import com.x8bit.bitwarden.ui.platform.manager.intent.EXTRA_KEY_CIPHER_ID
import com.x8bit.bitwarden.ui.platform.manager.intent.EXTRA_KEY_CREDENTIAL_ID
import com.x8bit.bitwarden.ui.platform.manager.intent.EXTRA_KEY_USER_ID
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Fido2IntentUtilsTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(::isBuildVersionBelow)
        mockkObject(PendingIntentHandler.Companion)
        every { isBuildVersionBelow(any()) } returns false
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(::isBuildVersionBelow)
        unmockkObject(PendingIntentHandler.Companion)
    }

    @Test
    fun `getFido2CredentialRequestOrNull should return Fido2CredentialRequest when present`() {
        val intent = mockk<Intent> {
            every { getStringExtra(EXTRA_KEY_USER_ID) } returns "mockUserId"
        }
        val mockCallingRequest = mockk<CreatePublicKeyCredentialRequest> {
            every { requestJson } returns "requestJson"
            every { clientDataHash } returns byteArrayOf(0)
            every { preferImmediatelyAvailableCredentials } returns false
            every { origin } returns "mockOrigin"
            every { isAutoSelectAllowed } returns true
        }
        val mockCallingAppInfo = CallingAppInfo(
            packageName = "mockPackageName",
            signingInfo = SigningInfo(),
            origin = "mockOrigin",
        )
        val mockProviderRequest = ProviderCreateCredentialRequest(
            callingRequest = mockCallingRequest,
            callingAppInfo = mockCallingAppInfo,
        )

        every {
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        } returns mockProviderRequest

        val createRequest = intent.getFido2CredentialRequestOrNull()
        assertEquals(
            Fido2CredentialRequest(
                userId = "mockUserId",
                requestJson = mockCallingRequest.requestJson,
                packageName = mockCallingAppInfo.packageName,
                signingInfo = mockCallingAppInfo.signingInfo,
                origin = mockCallingAppInfo.origin,
            ),
            createRequest,
        )
    }

    @Test
    fun `getFido2CredentialRequestOrNull should return null when build version is below 34`() {
        val intent = mockk<Intent>()

        every { isBuildVersionBelow(34) } returns true

        assertNull(intent.getFido2CredentialRequestOrNull())
    }

    @Suppress("MaxLineLength")
    @Test
    fun `getFido2CredentialRequestOrNull should return null when intent is not a provider create credential request`() {
        val intent = mockk<Intent>()

        every {
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        } returns null

        assertNull(intent.getFido2CredentialRequestOrNull())
    }

    @Suppress("MaxLineLength")
    @Test
    fun `getFido2CredentialRequestOrNull should return null when calling request is not a public key credential create request`() {
        val intent = mockk<Intent>()
        val mockCallingRequest = mockk<CreatePasswordRequest>()
        val mockCallingAppInfo = CallingAppInfo(
            packageName = "mockPackageName",
            signingInfo = SigningInfo(),
            origin = "mockOrigin",
        )
        val mockProviderRequest = ProviderCreateCredentialRequest(
            callingRequest = mockCallingRequest,
            callingAppInfo = mockCallingAppInfo,
        )
        every {
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        } returns mockProviderRequest

        assertNull(intent.getFido2CredentialRequestOrNull())
    }

    @Suppress("MaxLineLength")
    @Test
    fun `getFido2CredentialRequestOrNull should return null when user id is not present in extras`() {
        val intent = mockk<Intent> {
            every { getStringExtra(EXTRA_KEY_USER_ID) } returns null
        }
        val mockCallingRequest = mockk<CreatePublicKeyCredentialRequest> {
            every { requestJson } returns "requestJson"
            every { clientDataHash } returns byteArrayOf(0)
            every { preferImmediatelyAvailableCredentials } returns false
            every { origin } returns "mockOrigin"
            every { isAutoSelectAllowed } returns true
        }
        val mockCallingAppInfo = CallingAppInfo(
            packageName = "mockPackageName",
            signingInfo = SigningInfo(),
            origin = "mockOrigin",
        )
        val mockProviderRequest = ProviderCreateCredentialRequest(
            callingRequest = mockCallingRequest,
            callingAppInfo = mockCallingAppInfo,
        )

        every {
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        } returns mockProviderRequest

        assertNull(intent.getFido2CredentialRequestOrNull())
    }

    @Test
    fun `getFido2AssertionRequestOrNull should return Fido2AssertionRequest when present`() {
        val intent = mockk<Intent> {
            every { getStringExtra(EXTRA_KEY_CIPHER_ID) } returns "mockCipherId"
            every { getStringExtra(EXTRA_KEY_CREDENTIAL_ID) } returns "mockCredentialId"
        }
        val mockOption = GetPublicKeyCredentialOption(
            requestJson = "requestJson",
            clientDataHash = byteArrayOf(0),
            allowedProviders = emptySet(),
        )
        val mockCallingAppInfo = CallingAppInfo(
            packageName = "mockPackageName",
            signingInfo = SigningInfo(),
            origin = "mockOrigin",
        )
        val mockProviderGetCredentialRequest = ProviderGetCredentialRequest(
            credentialOptions = listOf(mockOption),
            callingAppInfo = mockCallingAppInfo,
        )

        every {
            PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        } returns mockProviderGetCredentialRequest

        val assertionRequest = intent.getFido2AssertionRequestOrNull()

        assertNotNull(assertionRequest)
        assertEquals(
            Fido2CredentialAssertionRequest(
                cipherId = "mockCipherId",
                credentialId = "mockCredentialId",
                requestJson = mockOption.requestJson,
                clientDataHash = mockOption.clientDataHash,
                packageName = mockCallingAppInfo.packageName,
                signingInfo = mockCallingAppInfo.signingInfo,
                origin = mockCallingAppInfo.origin,
            ),
            assertionRequest,
        )
    }

    @Test
    fun `getFido2AssertionRequestOrNull should return null when build version is below 34`() {
        val intent = mockk<Intent>()
        every { isBuildVersionBelow(34) } returns true

        val assertionRequest = intent.getFido2AssertionRequestOrNull()

        assertNull(assertionRequest)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `getFido2AssertionRequestOrNull should return null when retrieveProviderGetCredentialRequest is null`() {
        val intent = mockk<Intent>()

        every {
            PendingIntentHandler.retrieveProviderGetCredentialRequest(any())
        } returns null

        val assertionRequest = intent.getFido2AssertionRequestOrNull()

        assertNull(assertionRequest)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `getFido2AssertionRequestOrNull should return null when no passkey credential options are present in request`() {
        val intent = mockk<Intent>()

        val mockProviderGetCredentialRequest = ProviderGetCredentialRequest(
            credentialOptions = listOf(GetPasswordOption()),
            callingAppInfo = mockk(),
        )
        every {
            PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        } returns mockProviderGetCredentialRequest

        val assertionRequest = intent.getFido2AssertionRequestOrNull()

        assertNull(assertionRequest)
    }

    @Test
    fun `getFido2AssertionRequestOrNull should return null when credential id is not in extras`() {
        val intent = mockk<Intent> {
            every { getStringExtra(EXTRA_KEY_CREDENTIAL_ID) } returns null
        }
        val mockOption = GetPublicKeyCredentialOption(
            requestJson = "requestJson",
            clientDataHash = byteArrayOf(0),
            allowedProviders = emptySet(),
        )
        val mockProviderGetCredentialRequest = ProviderGetCredentialRequest(
            credentialOptions = listOf(mockOption),
            callingAppInfo = mockk(),
        )
        every {
            PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        } returns mockProviderGetCredentialRequest

        val assertionRequest = intent.getFido2AssertionRequestOrNull()

        assertNull(assertionRequest)
    }

    @Test
    fun `getFido2AssertionRequestOrNull should return null when cipher id is not in extras`() {
        val intent = mockk<Intent> {
            every { getStringExtra(EXTRA_KEY_CREDENTIAL_ID) } returns "mockCredentialId"
            every { getStringExtra(EXTRA_KEY_CIPHER_ID) } returns null
        }
        val mockOption = GetPublicKeyCredentialOption(
            requestJson = "requestJson",
            clientDataHash = byteArrayOf(0),
            allowedProviders = emptySet(),
        )
        val mockProviderGetCredentialRequest = ProviderGetCredentialRequest(
            credentialOptions = listOf(mockOption),
            callingAppInfo = mockk(),
        )
        every {
            PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        } returns mockProviderGetCredentialRequest

        val assertionRequest = intent.getFido2AssertionRequestOrNull()

        assertNull(assertionRequest)
    }
}
