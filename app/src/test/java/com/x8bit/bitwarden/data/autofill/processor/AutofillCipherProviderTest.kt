package com.x8bit.bitwarden.data.autofill.processor

import com.bitwarden.core.CardView
import com.bitwarden.core.CipherType
import com.bitwarden.core.CipherView
import com.bitwarden.core.LoginView
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.autofill.model.AutofillCipher
import com.x8bit.bitwarden.data.autofill.provider.AutofillCipherProvider
import com.x8bit.bitwarden.data.autofill.provider.AutofillCipherProviderImpl
import com.x8bit.bitwarden.data.platform.repository.model.DataState
import com.x8bit.bitwarden.data.platform.util.subtitle
import com.x8bit.bitwarden.data.platform.util.takeIfUriMatches
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import com.x8bit.bitwarden.data.vault.repository.model.VaultState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AutofillCipherProviderTest {
    private val cardView: CardView = mockk {
        every { cardholderName } returns CARD_CARDHOLDER_NAME
        every { code } returns CARD_CODE
        every { expMonth } returns CARD_EXP_MONTH
        every { expYear } returns CARD_EXP_YEAR
        every { number } returns CARD_NUMBER
    }
    private val cardCipherView: CipherView = mockk {
        every { card } returns cardView
        every { name } returns CARD_NAME
        every { type } returns CipherType.CARD
    }
    private val loginView: LoginView = mockk {
        every { password } returns LOGIN_PASSWORD
        every { username } returns LOGIN_USERNAME
    }
    private val loginCipherView: CipherView = mockk {
        every { login } returns loginView
        every { name } returns LOGIN_NAME
        every { type } returns CipherType.LOGIN
    }
    private val authRepository: AuthRepository = mockk {
        every { activeUserId } returns ACTIVE_USER_ID
    }
    private val mutableVaultStateFlow = MutableStateFlow(
        VaultState(
            unlockingVaultUserIds = emptySet(),
            unlockedVaultUserIds = emptySet(),
        ),
    )
    private val mutableCiphersStateFlow = MutableStateFlow<DataState<List<CipherView>>>(
        DataState.Loading,
    )
    private val vaultRepository: VaultRepository = mockk {
        every { ciphersStateFlow } returns mutableCiphersStateFlow
        every { vaultStateFlow } returns mutableVaultStateFlow
        every { isVaultUnlocked(ACTIVE_USER_ID) } answers {
            ACTIVE_USER_ID in mutableVaultStateFlow.value.unlockedVaultUserIds
        }
    }

    private lateinit var autofillCipherProvider: AutofillCipherProvider

    @BeforeEach
    fun setup() {
        mockkStatic(CipherView::takeIfUriMatches)
        mockkStatic(CipherView::subtitle)
        autofillCipherProvider = AutofillCipherProviderImpl(
            authRepository = authRepository,
            vaultRepository = vaultRepository,
        )
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(CipherView::takeIfUriMatches)
        unmockkStatic(CipherView::subtitle)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `isVaultLocked when there is no active user should return true`() =
        runTest {
            every { authRepository.activeUserId } returns null

            val result = async {
                autofillCipherProvider.isVaultLocked()
            }

            testScheduler.advanceUntilIdle()
            assertTrue(result.isCompleted)
            assertTrue(result.await())
        }

    @Suppress("MaxLineLength")
    @Test
    fun `isVaultLocked when there is an active user should wait for pending unlocking to finish and return the locked state for that user`() =
        runTest {
            every { authRepository.activeUserId } returns ACTIVE_USER_ID
            mutableVaultStateFlow.value = VaultState(
                unlockedVaultUserIds = emptySet(),
                unlockingVaultUserIds = setOf(ACTIVE_USER_ID),
            )

            val result = async {
                autofillCipherProvider.isVaultLocked()
            }

            testScheduler.advanceUntilIdle()
            assertFalse(result.isCompleted)

            mutableVaultStateFlow.value = VaultState(
                unlockedVaultUserIds = setOf(ACTIVE_USER_ID),
                unlockingVaultUserIds = emptySet(),
            )

            testScheduler.advanceUntilIdle()
            assertTrue(result.isCompleted)

            assertFalse(result.await())
        }

    @Test
    fun `getCardAutofillCiphers when unlocked should return non-null card ciphers`() =
        runTest {
            val cipherViews = listOf(
                cardCipherView,
                loginCipherView,
            )
            mutableCiphersStateFlow.value = DataState.Loaded(
                data = cipherViews,
            )
            mutableVaultStateFlow.value = VaultState(
                unlockedVaultUserIds = setOf(ACTIVE_USER_ID),
                unlockingVaultUserIds = emptySet(),
            )
            val expected = listOf(
                CARD_AUTOFILL_CIPHER,
            )
            every { cardCipherView.subtitle } returns CARD_SUBTITLE

            // Test & Verify
            val actual = autofillCipherProvider.getCardAutofillCiphers()

            assertEquals(expected, actual)
        }

    @Test
    fun `getCardAutofillCiphers when locked should return an empty list`() = runTest {
        mutableVaultStateFlow.value = VaultState(
            unlockedVaultUserIds = emptySet(),
            unlockingVaultUserIds = emptySet(),
        )

        // Test & Verify
        val actual = autofillCipherProvider.getCardAutofillCiphers()

        assertEquals(emptyList<AutofillCipher.Card>(), actual)
    }

    @Test
    fun `getLoginAutofillCiphers when unlocked should return matching login ciphers`() =
        runTest {
            val unmatchedLoginCipherView: CipherView = mockk {
                every { takeIfUriMatches(URI) } returns null
                every { type } returns CipherType.LOGIN
            }
            val cipherViews = listOf(
                cardCipherView,
                loginCipherView,
                unmatchedLoginCipherView,
            )
            mutableCiphersStateFlow.value = DataState.Loaded(
                data = cipherViews,
            )
            mutableVaultStateFlow.value = VaultState(
                unlockedVaultUserIds = setOf(ACTIVE_USER_ID),
                unlockingVaultUserIds = emptySet(),
            )
            val expected = listOf(
                LOGIN_AUTOFILL_CIPHER,
            )
            every { loginCipherView.subtitle } returns LOGIN_SUBTITLE
            every { loginCipherView.takeIfUriMatches(URI) } returns loginCipherView

            // Test & Verify
            val actual = autofillCipherProvider.getLoginAutofillCiphers(
                uri = URI,
            )

            assertEquals(expected, actual)
        }

    @Test
    fun `getLoginAutofillCiphers when locked should return an empty list`() = runTest {
        mutableVaultStateFlow.value = VaultState(
            unlockedVaultUserIds = emptySet(),
            unlockingVaultUserIds = emptySet(),
        )

        // Test & Verify
        val actual = autofillCipherProvider.getLoginAutofillCiphers(
            uri = URI,
        )

        assertEquals(emptyList<AutofillCipher.Login>(), actual)
    }
}

private const val ACTIVE_USER_ID = "activeUserId"
private const val CARD_CARDHOLDER_NAME = "John Doe"
private const val CARD_CODE = "123"
private const val CARD_EXP_MONTH = "January"
private const val CARD_EXP_YEAR = "2029"
private const val CARD_NAME = "John's Card"
private const val CARD_NUMBER = "1234567890"
private const val CARD_SUBTITLE = "7890"
private val CARD_AUTOFILL_CIPHER = AutofillCipher.Card(
    cardholderName = CARD_CARDHOLDER_NAME,
    code = CARD_CODE,
    expirationMonth = CARD_EXP_MONTH,
    expirationYear = CARD_EXP_YEAR,
    name = CARD_NAME,
    number = CARD_NUMBER,
    subtitle = CARD_SUBTITLE,
)
private const val LOGIN_NAME = "John's Login"
private const val LOGIN_PASSWORD = "Password123"
private const val LOGIN_SUBTITLE = "John Doe"
private const val LOGIN_USERNAME = "John-Bitwarden"
private val LOGIN_AUTOFILL_CIPHER = AutofillCipher.Login(
    name = LOGIN_NAME,
    password = LOGIN_PASSWORD,
    subtitle = LOGIN_SUBTITLE,
    username = LOGIN_USERNAME,
)
private val CIPHERS = listOf(
    CARD_AUTOFILL_CIPHER,
    LOGIN_AUTOFILL_CIPHER,
)
private const val URI: String = "androidapp://com.x8bit.bitwarden"