@file:Suppress("TooManyFunctions")

package com.x8bit.bitwarden.data.tools.generator.repository

import com.bitwarden.core.PasswordHistoryView
import com.bitwarden.generators.PassphraseGeneratorRequest
import com.bitwarden.generators.PasswordGeneratorRequest
import com.bitwarden.generators.UsernameGeneratorRequest
import com.x8bit.bitwarden.data.auth.datasource.disk.AuthDiskSource
import com.x8bit.bitwarden.data.auth.repository.model.PolicyInformation
import com.x8bit.bitwarden.data.auth.repository.util.policyInformation
import com.x8bit.bitwarden.data.platform.manager.dispatcher.DispatcherManager
import com.x8bit.bitwarden.data.platform.repository.model.LocalDataState
import com.x8bit.bitwarden.data.platform.repository.util.observeWhenSubscribedAndLoggedIn
import com.x8bit.bitwarden.data.tools.generator.datasource.disk.GeneratorDiskSource
import com.x8bit.bitwarden.data.tools.generator.datasource.disk.PasswordHistoryDiskSource
import com.x8bit.bitwarden.data.tools.generator.datasource.disk.entity.toPasswordHistory
import com.x8bit.bitwarden.data.tools.generator.datasource.disk.entity.toPasswordHistoryEntity
import com.x8bit.bitwarden.data.tools.generator.datasource.sdk.GeneratorSdkSource
import com.x8bit.bitwarden.data.tools.generator.repository.model.GeneratedCatchAllUsernameResult
import com.x8bit.bitwarden.data.tools.generator.repository.model.GeneratedForwardedServiceUsernameResult
import com.x8bit.bitwarden.data.tools.generator.repository.model.GeneratedPassphraseResult
import com.x8bit.bitwarden.data.tools.generator.repository.model.GeneratedPasswordResult
import com.x8bit.bitwarden.data.tools.generator.repository.model.GeneratedPlusAddressedUsernameResult
import com.x8bit.bitwarden.data.tools.generator.repository.model.GeneratedRandomWordUsernameResult
import com.x8bit.bitwarden.data.tools.generator.repository.model.GeneratorResult
import com.x8bit.bitwarden.data.tools.generator.repository.model.PasscodeGenerationOptions
import com.x8bit.bitwarden.data.tools.generator.repository.model.UsernameGenerationOptions
import com.x8bit.bitwarden.data.vault.datasource.network.model.PolicyTypeJson
import com.x8bit.bitwarden.data.vault.datasource.sdk.VaultSdkSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Singleton
import kotlin.math.max

/**
 * Default implementation of [GeneratorRepository].
 */
@Singleton
class GeneratorRepositoryImpl(
    private val generatorSdkSource: GeneratorSdkSource,
    private val generatorDiskSource: GeneratorDiskSource,
    private val authDiskSource: AuthDiskSource,
    private val vaultSdkSource: VaultSdkSource,
    private val passwordHistoryDiskSource: PasswordHistoryDiskSource,
    dispatcherManager: DispatcherManager,
) : GeneratorRepository {

    private val scope = CoroutineScope(dispatcherManager.io)

    private val mutablePasswordHistoryStateFlow =
        MutableStateFlow<LocalDataState<List<PasswordHistoryView>>>(LocalDataState.Loading)

    private val generatorResultChannel = Channel<GeneratorResult>(capacity = Int.MAX_VALUE)

    override val passwordHistoryStateFlow: StateFlow<LocalDataState<List<PasswordHistoryView>>>
        get() = mutablePasswordHistoryStateFlow.asStateFlow()

    override val generatorResultFlow: Flow<GeneratorResult>
        get() = generatorResultChannel.receiveAsFlow()

    init {
        mutablePasswordHistoryStateFlow
            .observeWhenSubscribedAndLoggedIn(authDiskSource.userStateFlow) { activeUserId ->
                observePasswordHistoryForUser(activeUserId)
            }
            .launchIn(scope)
    }

    private fun observePasswordHistoryForUser(
        userId: String,
    ): Flow<Result<List<PasswordHistoryView>>> =
        passwordHistoryDiskSource
            .getPasswordHistoriesForUser(userId)
            .onStart { mutablePasswordHistoryStateFlow.value = LocalDataState.Loading }
            .map { encryptedPasswordHistoryList ->
                val passwordHistories = encryptedPasswordHistoryList.map { it.toPasswordHistory() }
                vaultSdkSource
                    .decryptPasswordHistoryList(
                        userId = userId,
                        passwordHistoryList = passwordHistories,
                    )
            }
            .onEach { encryptedPasswordHistoryListResult ->
                mutablePasswordHistoryStateFlow.value = encryptedPasswordHistoryListResult.fold(
                    onSuccess = {
                        LocalDataState.Loaded(
                            it.sortedByDescending { history -> history.lastUsedDate },
                        )
                    },
                    onFailure = { LocalDataState.Error(it) },
                )
            }

    override fun emitGeneratorResult(generatorResult: GeneratorResult) {
        generatorResultChannel.trySend(generatorResult)
    }

    override suspend fun generatePassword(
        passwordGeneratorRequest: PasswordGeneratorRequest,
        shouldSave: Boolean,
    ): GeneratedPasswordResult =
        generatorSdkSource
            .generatePassword(passwordGeneratorRequest)
            .fold(
                onSuccess = { generatedPassword ->
                    val passwordHistoryView = PasswordHistoryView(
                        password = generatedPassword,
                        lastUsedDate = Instant.now(),
                    )

                    if (shouldSave) {
                        scope.launch {
                            storePasswordHistory(passwordHistoryView)
                        }
                    }
                    GeneratedPasswordResult.Success(generatedPassword)
                },
                onFailure = { GeneratedPasswordResult.InvalidRequest },
            )

    override suspend fun generatePassphrase(
        passphraseGeneratorRequest: PassphraseGeneratorRequest,
    ): GeneratedPassphraseResult =
        generatorSdkSource
            .generatePassphrase(passphraseGeneratorRequest)
            .fold(
                onSuccess = { generatedPassphrase ->
                    val passwordHistoryView = PasswordHistoryView(
                        password = generatedPassphrase,
                        lastUsedDate = Instant.now(),
                    )
                    scope.launch {
                        storePasswordHistory(passwordHistoryView)
                    }
                    GeneratedPassphraseResult.Success(generatedPassphrase)
                },
                onFailure = { GeneratedPassphraseResult.InvalidRequest },
            )

    override suspend fun generatePlusAddressedEmail(
        plusAddressedEmailGeneratorRequest: UsernameGeneratorRequest.Subaddress,
    ): GeneratedPlusAddressedUsernameResult =
        generatorSdkSource.generatePlusAddressedEmail(plusAddressedEmailGeneratorRequest)
            .fold(
                onSuccess = { generatedEmail ->
                    GeneratedPlusAddressedUsernameResult.Success(generatedEmail)
                },
                onFailure = {
                    GeneratedPlusAddressedUsernameResult.InvalidRequest
                },
            )

    override suspend fun generateCatchAllEmail(
        catchAllEmailGeneratorRequest: UsernameGeneratorRequest.Catchall,
    ): GeneratedCatchAllUsernameResult =
        generatorSdkSource.generateCatchAllEmail(catchAllEmailGeneratorRequest)
            .fold(
                onSuccess = { generatedEmail ->
                    GeneratedCatchAllUsernameResult.Success(generatedEmail)
                },
                onFailure = {
                    GeneratedCatchAllUsernameResult.InvalidRequest
                },
            )

    override suspend fun generateRandomWordUsername(
        randomWordGeneratorRequest: UsernameGeneratorRequest.Word,
    ): GeneratedRandomWordUsernameResult =
        generatorSdkSource.generateRandomWord(randomWordGeneratorRequest)
            .fold(
                onSuccess = { generatedUsername ->
                    GeneratedRandomWordUsernameResult.Success(generatedUsername)
                },
                onFailure = {
                    GeneratedRandomWordUsernameResult.InvalidRequest
                },
            )

    override suspend fun generateForwardedServiceUsername(
        forwardedServiceGeneratorRequest: UsernameGeneratorRequest.Forwarded,
    ): GeneratedForwardedServiceUsernameResult =
        generatorSdkSource.generateForwardedServiceEmail(forwardedServiceGeneratorRequest)
            .fold(
                onSuccess = { generatedEmail ->
                    GeneratedForwardedServiceUsernameResult.Success(generatedEmail)
                },
                onFailure = {
                    GeneratedForwardedServiceUsernameResult.InvalidRequest
                },
            )

    @Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod")
    override fun getPasswordGeneratorPolicy(): PolicyInformation.PasswordGenerator? {
        val userId = authDiskSource.userState?.activeUserId ?: return null
        val policies = authDiskSource.getPolicies(userId) ?: return null

        var minLength: Int? = null
        var useUpper = false
        var useLower = false
        var useNumbers = false
        var useSpecial = false
        var minNumbers: Int? = null
        var minSpecial: Int? = null
        var minNumberWords: Int? = null
        var capitalize = false
        var includeNumber = false

        var isPassphrasePresent = false
        policies.filter { it.type == PolicyTypeJson.PASSWORD_GENERATOR && it.isEnabled }
            .mapNotNull { it.policyInformation as? PolicyInformation.PasswordGenerator }
            .forEach { policy ->
                if (policy.defaultType == PolicyInformation.PasswordGenerator.TYPE_PASSPHRASE) {
                    isPassphrasePresent = true
                }
                minLength = max(minLength ?: 0, policy.minLength ?: 0)
                useUpper = useUpper || policy.useUpper == true
                useLower = useLower || policy.useLower == true
                useNumbers = useNumbers || policy.useNumbers == true
                useSpecial = useSpecial || policy.useSpecial == true
                minNumbers = max(minNumbers ?: 0, policy.minNumbers ?: 0)
                minSpecial = max(minSpecial ?: 0, policy.minSpecial ?: 0)
                minNumberWords = max(minNumberWords ?: 0, policy.minNumberWords ?: 0)
                capitalize = capitalize || policy.capitalize == true
                includeNumber = includeNumber || policy.includeNumber == true
            }

        // Only return a new policy if any policy settings were actually provided
        return PolicyInformation.PasswordGenerator(
            defaultType = if (isPassphrasePresent) {
                PolicyInformation.PasswordGenerator.TYPE_PASSPHRASE
            } else {
                PolicyInformation.PasswordGenerator.TYPE_PASSWORD
            },
            minLength = minLength,
            useUpper = useUpper,
            useLower = useLower,
            useNumbers = useNumbers,
            useSpecial = useSpecial,
            minNumbers = minNumbers,
            minSpecial = minSpecial,
            minNumberWords = minNumberWords,
            capitalize = capitalize,
            includeNumber = includeNumber,
        ).takeIf {
            listOf(
                minLength,
                useUpper,
                useLower,
                useNumbers,
                useSpecial,
                minNumbers,
                minSpecial,
                minNumberWords,
                capitalize,
                includeNumber,
            )
                .any { it != null }
        }
    }

    override fun getPasscodeGenerationOptions(): PasscodeGenerationOptions? {
        val userId = authDiskSource.userState?.activeUserId
        return userId?.let { generatorDiskSource.getPasscodeGenerationOptions(it) }
    }

    override fun savePasscodeGenerationOptions(options: PasscodeGenerationOptions) {
        val userId = authDiskSource.userState?.activeUserId
        userId?.let { generatorDiskSource.storePasscodeGenerationOptions(it, options) }
    }

    override fun getUsernameGenerationOptions(): UsernameGenerationOptions? {
        val userId = authDiskSource.userState?.activeUserId
        return userId?.let { generatorDiskSource.getUsernameGenerationOptions(it) }
    }

    override fun saveUsernameGenerationOptions(options: UsernameGenerationOptions) {
        val userId = authDiskSource.userState?.activeUserId
        userId?.let { generatorDiskSource.storeUsernameGenerationOptions(it, options) }
    }

    override suspend fun storePasswordHistory(passwordHistoryView: PasswordHistoryView) {
        val userId = authDiskSource.userState?.activeUserId ?: return
        val encryptedPasswordHistory = vaultSdkSource
            .encryptPasswordHistory(
                userId = userId,
                passwordHistory = passwordHistoryView,
            )
            .getOrNull() ?: return
        passwordHistoryDiskSource.insertPasswordHistory(
            encryptedPasswordHistory.toPasswordHistoryEntity(userId),
        )
    }

    override suspend fun clearPasswordHistory() {
        val userId = authDiskSource.userState?.activeUserId ?: return
        passwordHistoryDiskSource.clearPasswordHistories(userId)
    }
}
