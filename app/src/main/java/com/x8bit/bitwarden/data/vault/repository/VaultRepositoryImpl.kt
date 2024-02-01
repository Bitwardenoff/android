package com.x8bit.bitwarden.data.vault.repository

import android.net.Uri
import com.bitwarden.core.AttachmentView
import com.bitwarden.core.CipherType
import com.bitwarden.core.CipherView
import com.bitwarden.core.CollectionView
import com.bitwarden.core.DateTime
import com.bitwarden.core.FolderView
import com.bitwarden.core.InitOrgCryptoRequest
import com.bitwarden.core.InitUserCryptoMethod
import com.bitwarden.core.SendType
import com.bitwarden.core.SendView
import com.bitwarden.crypto.Kdf
import com.x8bit.bitwarden.data.auth.datasource.disk.AuthDiskSource
import com.x8bit.bitwarden.data.auth.repository.util.toSdkParams
import com.x8bit.bitwarden.data.auth.repository.util.toUpdatedUserStateJson
import com.x8bit.bitwarden.data.platform.datasource.disk.SettingsDiskSource
import com.x8bit.bitwarden.data.platform.datasource.network.util.isNoConnectionError
import com.x8bit.bitwarden.data.platform.manager.PushManager
import com.x8bit.bitwarden.data.platform.manager.dispatcher.DispatcherManager
import com.x8bit.bitwarden.data.platform.manager.model.SyncCipherDeleteData
import com.x8bit.bitwarden.data.platform.manager.model.SyncCipherUpsertData
import com.x8bit.bitwarden.data.platform.manager.model.SyncFolderDeleteData
import com.x8bit.bitwarden.data.platform.manager.model.SyncFolderUpsertData
import com.x8bit.bitwarden.data.platform.manager.model.SyncSendDeleteData
import com.x8bit.bitwarden.data.platform.manager.model.SyncSendUpsertData
import com.x8bit.bitwarden.data.platform.repository.model.DataState
import com.x8bit.bitwarden.data.platform.repository.util.bufferedMutableSharedFlow
import com.x8bit.bitwarden.data.platform.repository.util.combineDataStates
import com.x8bit.bitwarden.data.platform.repository.util.map
import com.x8bit.bitwarden.data.platform.repository.util.observeWhenSubscribedAndLoggedIn
import com.x8bit.bitwarden.data.platform.repository.util.updateToPendingOrLoading
import com.x8bit.bitwarden.data.platform.util.asFailure
import com.x8bit.bitwarden.data.platform.util.flatMap
import com.x8bit.bitwarden.data.vault.datasource.disk.VaultDiskSource
import com.x8bit.bitwarden.data.vault.datasource.network.model.AttachmentJsonRequest
import com.x8bit.bitwarden.data.vault.datasource.network.model.CreateCipherInOrganizationJsonRequest
import com.x8bit.bitwarden.data.vault.datasource.network.model.ShareCipherJsonRequest
import com.x8bit.bitwarden.data.vault.datasource.network.model.SyncResponseJson
import com.x8bit.bitwarden.data.vault.datasource.network.model.UpdateCipherCollectionsJsonRequest
import com.x8bit.bitwarden.data.vault.datasource.network.model.UpdateCipherResponseJson
import com.x8bit.bitwarden.data.vault.datasource.network.model.UpdateFolderResponseJson
import com.x8bit.bitwarden.data.vault.datasource.network.model.UpdateSendResponseJson
import com.x8bit.bitwarden.data.vault.datasource.network.service.CiphersService
import com.x8bit.bitwarden.data.vault.datasource.network.service.FolderService
import com.x8bit.bitwarden.data.vault.datasource.network.service.SendsService
import com.x8bit.bitwarden.data.vault.datasource.network.service.SyncService
import com.x8bit.bitwarden.data.vault.datasource.sdk.VaultSdkSource
import com.x8bit.bitwarden.data.vault.manager.FileManager
import com.x8bit.bitwarden.data.vault.manager.TotpCodeManager
import com.x8bit.bitwarden.data.vault.manager.VaultLockManager
import com.x8bit.bitwarden.data.vault.manager.model.DownloadResult
import com.x8bit.bitwarden.data.vault.manager.model.VerificationCodeItem
import com.x8bit.bitwarden.data.vault.repository.model.CreateAttachmentResult
import com.x8bit.bitwarden.data.vault.repository.model.CreateCipherResult
import com.x8bit.bitwarden.data.vault.repository.model.CreateFolderResult
import com.x8bit.bitwarden.data.vault.repository.model.CreateSendResult
import com.x8bit.bitwarden.data.vault.repository.model.DeleteAttachmentResult
import com.x8bit.bitwarden.data.vault.repository.model.DeleteCipherResult
import com.x8bit.bitwarden.data.vault.repository.model.DeleteFolderResult
import com.x8bit.bitwarden.data.vault.repository.model.DeleteSendResult
import com.x8bit.bitwarden.data.vault.repository.model.DomainsData
import com.x8bit.bitwarden.data.vault.repository.model.DownloadAttachmentResult
import com.x8bit.bitwarden.data.vault.repository.model.GenerateTotpResult
import com.x8bit.bitwarden.data.vault.repository.model.RemovePasswordSendResult
import com.x8bit.bitwarden.data.vault.repository.model.RestoreCipherResult
import com.x8bit.bitwarden.data.vault.repository.model.SendData
import com.x8bit.bitwarden.data.vault.repository.model.ShareCipherResult
import com.x8bit.bitwarden.data.vault.repository.model.TotpCodeResult
import com.x8bit.bitwarden.data.vault.repository.model.UpdateCipherResult
import com.x8bit.bitwarden.data.vault.repository.model.UpdateFolderResult
import com.x8bit.bitwarden.data.vault.repository.model.UpdateSendResult
import com.x8bit.bitwarden.data.vault.repository.model.VaultData
import com.x8bit.bitwarden.data.vault.repository.model.VaultUnlockResult
import com.x8bit.bitwarden.data.vault.repository.util.sortAlphabetically
import com.x8bit.bitwarden.data.vault.repository.util.toDomainsData
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedNetworkCipher
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedNetworkCipherResponse
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedNetworkFolder
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedNetworkSend
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkCipher
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkCipherList
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkCollectionList
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkFolder
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkFolderList
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkSend
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkSendList
import com.x8bit.bitwarden.ui.vault.feature.vault.model.VaultFilterType
import com.x8bit.bitwarden.ui.vault.feature.vault.util.toFilteredList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A "stop timeout delay" in milliseconds used to let a shared coroutine continue to run for the
 * specified period of time after it no longer has subscribers.
 */
private const val STOP_TIMEOUT_DELAY_MS: Long = 1000L

/**
 * Default implementation of [VaultRepository].
 */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class VaultRepositoryImpl(
    private val syncService: SyncService,
    private val ciphersService: CiphersService,
    private val sendsService: SendsService,
    private val folderService: FolderService,
    private val vaultDiskSource: VaultDiskSource,
    private val vaultSdkSource: VaultSdkSource,
    private val authDiskSource: AuthDiskSource,
    private val settingsDiskSource: SettingsDiskSource,
    private val fileManager: FileManager,
    private val vaultLockManager: VaultLockManager,
    private val totpCodeManager: TotpCodeManager,
    private val pushManager: PushManager,
    private val clock: Clock,
    dispatcherManager: DispatcherManager,
) : VaultRepository,
    VaultLockManager by vaultLockManager {

    private val unconfinedScope = CoroutineScope(dispatcherManager.unconfined)
    private val ioScope = CoroutineScope(dispatcherManager.io)

    private var syncJob: Job = Job().apply { complete() }

    private val activeUserId: String? get() = authDiskSource.userState?.activeUserId

    private val mutableTotpCodeResultFlow = bufferedMutableSharedFlow<TotpCodeResult>()

    private val mutableSendDataStateFlow = MutableStateFlow<DataState<SendData>>(DataState.Loading)

    private val mutableCiphersStateFlow =
        MutableStateFlow<DataState<List<CipherView>>>(DataState.Loading)

    private val mutableFoldersStateFlow =
        MutableStateFlow<DataState<List<FolderView>>>(DataState.Loading)

    private val mutableCollectionsStateFlow =
        MutableStateFlow<DataState<List<CollectionView>>>(DataState.Loading)

    private val mutableDomainsStateFlow =
        MutableStateFlow<DataState<DomainsData>>(DataState.Loading)

    override var vaultFilterType: VaultFilterType = VaultFilterType.AllVaults

    override val vaultDataStateFlow: StateFlow<DataState<VaultData>> =
        combine(
            ciphersStateFlow,
            foldersStateFlow,
            collectionsStateFlow,
            sendDataStateFlow,
        ) { ciphersDataState, foldersDataState, collectionsDataState, sendsDataState ->
            combineDataStates(
                ciphersDataState,
                foldersDataState,
                collectionsDataState,
                sendsDataState,
            ) { ciphersData, foldersData, collectionsData, sendsData ->
                VaultData(
                    cipherViewList = ciphersData,
                    folderViewList = foldersData,
                    collectionViewList = collectionsData,
                    sendViewList = sendsData.sendViewList,
                )
            }
        }
            .stateIn(
                scope = unconfinedScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_DELAY_MS),
                initialValue = DataState.Loading,
            )

    override val totpCodeFlow: Flow<TotpCodeResult>
        get() = mutableTotpCodeResultFlow.asSharedFlow()

    override val ciphersStateFlow: StateFlow<DataState<List<CipherView>>>
        get() = mutableCiphersStateFlow.asStateFlow()

    override val domainsStateFlow: StateFlow<DataState<DomainsData>>
        get() = mutableDomainsStateFlow.asStateFlow()

    override val foldersStateFlow: StateFlow<DataState<List<FolderView>>>
        get() = mutableFoldersStateFlow.asStateFlow()

    override val collectionsStateFlow: StateFlow<DataState<List<CollectionView>>>
        get() = mutableCollectionsStateFlow.asStateFlow()

    override val sendDataStateFlow: StateFlow<DataState<SendData>>
        get() = mutableSendDataStateFlow.asStateFlow()

    init {
        // Setup ciphers MutableStateFlow
        mutableCiphersStateFlow
            .observeWhenSubscribedAndLoggedIn(authDiskSource.userStateFlow) { activeUserId ->
                observeVaultDiskCiphers(activeUserId)
            }
            .launchIn(unconfinedScope)
        // Setup domains MutableStateFlow
        mutableDomainsStateFlow
            .observeWhenSubscribedAndLoggedIn(authDiskSource.userStateFlow) { activeUserId ->
                observeVaultDiskDomains(activeUserId)
            }
            .launchIn(unconfinedScope)
        // Setup folders MutableStateFlow
        mutableFoldersStateFlow
            .observeWhenSubscribedAndLoggedIn(authDiskSource.userStateFlow) { activeUserId ->
                observeVaultDiskFolders(activeUserId)
            }
            .launchIn(unconfinedScope)
        // Setup collections MutableStateFlow
        mutableCollectionsStateFlow
            .observeWhenSubscribedAndLoggedIn(authDiskSource.userStateFlow) { activeUserId ->
                observeVaultDiskCollections(activeUserId)
            }
            .launchIn(unconfinedScope)
        // Setup sends MutableStateFlow
        mutableSendDataStateFlow
            .observeWhenSubscribedAndLoggedIn(authDiskSource.userStateFlow) { activeUserId ->
                observeVaultDiskSends(activeUserId)
            }
            .launchIn(unconfinedScope)

        pushManager
            .fullSyncFlow
            .onEach { syncIfNecessary() }
            .launchIn(unconfinedScope)

        pushManager
            .syncCipherDeleteFlow
            .onEach(::deleteCipher)
            .launchIn(unconfinedScope)

        pushManager
            .syncCipherUpsertFlow
            .onEach(::syncCipherIfNecessary)
            .launchIn(ioScope)

        pushManager
            .syncSendDeleteFlow
            .onEach(::deleteSend)
            .launchIn(unconfinedScope)

        pushManager
            .syncSendUpsertFlow
            .onEach(::syncSendIfNecessary)
            .launchIn(ioScope)

        pushManager
            .syncFolderDeleteFlow
            .onEach(::deleteFolder)
            .launchIn(unconfinedScope)

        pushManager
            .syncFolderUpsertFlow
            .onEach(::syncFolderIfNecessary)
            .launchIn(ioScope)
    }

    override fun clearUnlockedData() {
        mutableCiphersStateFlow.update { DataState.Loading }
        mutableDomainsStateFlow.update { DataState.Loading }
        mutableFoldersStateFlow.update { DataState.Loading }
        mutableCollectionsStateFlow.update { DataState.Loading }
        mutableSendDataStateFlow.update { DataState.Loading }
    }

    override fun deleteVaultData(userId: String) {
        ioScope.launch {
            vaultDiskSource.deleteVaultData(userId)
        }
    }

    override fun sync() {
        val userId = activeUserId ?: return
        if (!syncJob.isCompleted || isVaultUnlocking(userId)) return
        mutableCiphersStateFlow.updateToPendingOrLoading()
        mutableDomainsStateFlow.updateToPendingOrLoading()
        mutableFoldersStateFlow.updateToPendingOrLoading()
        mutableCollectionsStateFlow.updateToPendingOrLoading()
        mutableSendDataStateFlow.updateToPendingOrLoading()
        syncJob = ioScope.launch {
            syncService
                .sync()
                .fold(
                    onSuccess = { syncResponse ->
                        // Update user information with additional information from sync response
                        authDiskSource.userState = authDiskSource
                            .userState
                            ?.toUpdatedUserStateJson(
                                syncResponse = syncResponse,
                            )

                        unlockVaultForOrganizationsIfNecessary(syncResponse = syncResponse)
                        storeProfileData(syncResponse = syncResponse)
                        authDiskSource.storePolicies(
                            userId = userId,
                            policies = syncResponse.policies,
                        )
                        vaultDiskSource.replaceVaultData(userId = userId, vault = syncResponse)
                        settingsDiskSource.storeLastSyncTime(userId = userId, clock.instant())
                    },
                    onFailure = { throwable ->
                        mutableCiphersStateFlow.update { currentState ->
                            throwable.toNetworkOrErrorState(
                                data = currentState.data,
                            )
                        }
                        mutableDomainsStateFlow.update { currentState ->
                            throwable.toNetworkOrErrorState(
                                data = currentState.data,
                            )
                        }
                        mutableFoldersStateFlow.update { currentState ->
                            throwable.toNetworkOrErrorState(
                                data = currentState.data,
                            )
                        }
                        mutableCollectionsStateFlow.update { currentState ->
                            throwable.toNetworkOrErrorState(
                                data = currentState.data,
                            )
                        }
                        mutableSendDataStateFlow.update { currentState ->
                            throwable.toNetworkOrErrorState(
                                data = currentState.data,
                            )
                        }
                    },
                )
        }
    }

    @Suppress("MagicNumber")
    override fun syncIfNecessary() {
        val userId = activeUserId ?: return
        val currentInstant = clock.instant()
        val lastSyncInstant = settingsDiskSource.getLastSyncTime(userId = userId)

        // Sync if we have never done so or the last time was at last 30 minutes ago
        if (lastSyncInstant == null ||
            currentInstant.isAfter(lastSyncInstant.plus(30, ChronoUnit.MINUTES))
        ) {
            sync()
        }
    }

    override fun getVaultItemStateFlow(itemId: String): StateFlow<DataState<CipherView?>> =
        vaultDataStateFlow
            .map { dataState ->
                dataState.map { vaultData ->
                    vaultData
                        .cipherViewList
                        .find { it.id == itemId }
                }
            }
            .stateIn(
                scope = unconfinedScope,
                started = SharingStarted.Lazily,
                initialValue = DataState.Loading,
            )

    override fun getVaultFolderStateFlow(folderId: String): StateFlow<DataState<FolderView?>> =
        vaultDataStateFlow
            .map { dataState ->
                dataState.map { vaultData ->
                    vaultData
                        .folderViewList
                        .find { it.id == folderId }
                }
            }
            .stateIn(
                scope = unconfinedScope,
                started = SharingStarted.Lazily,
                initialValue = DataState.Loading,
            )

    override fun getSendStateFlow(sendId: String): StateFlow<DataState<SendView?>> =
        sendDataStateFlow
            .map { dataState ->
                dataState.map { sendData ->
                    sendData.sendViewList.find { it.id == sendId }
                }
            }
            .stateIn(
                scope = unconfinedScope,
                started = SharingStarted.Lazily,
                initialValue = DataState.Loading,
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAuthCodesFlow(): StateFlow<DataState<List<VerificationCodeItem>>> {
        val userId = activeUserId ?: return MutableStateFlow(
            DataState.Error(IllegalStateException("No active user"), null),
        )
        return vaultDataStateFlow
            .map { dataState ->
                dataState.map { vaultData ->
                    vaultData
                        .cipherViewList
                        .filter {
                            it.type == CipherType.LOGIN &&
                                !it.login?.totp.isNullOrBlank() &&
                                it.deletedDate == null
                        }
                        .toFilteredList(vaultFilterType)
                }
            }
            .flatMapLatest { cipherDataState ->
                val cipherList = cipherDataState.data ?: emptyList()
                totpCodeManager
                    .getTotpCodesStateFlow(
                        userId = userId,
                        cipherList = cipherList,
                    )
                    .map { verificationCodeDataStates ->
                        combineDataStates(
                            verificationCodeDataStates,
                            cipherDataState,
                        ) { verificationCodeItems, _ ->
                            // Just return the verification items; we are only combining the
                            // DataStates to know the overall state.
                            verificationCodeItems
                        }
                    }
            }
            .stateIn(
                scope = unconfinedScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = DataState.Loading,
            )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAuthCodeFlow(cipherId: String): StateFlow<DataState<VerificationCodeItem?>> {
        val userId = activeUserId ?: return MutableStateFlow(
            DataState.Error(IllegalStateException("No active user"), null),
        )
        return getVaultItemStateFlow(cipherId)
            .flatMapLatest { cipherDataState ->
                val cipher = cipherDataState.data
                    ?: return@flatMapLatest flowOf(DataState.Loaded(null))
                totpCodeManager
                    .getTotpCodeStateFlow(
                        userId = userId,
                        cipher = cipher,
                    )
                    .map { totpCodeDataState ->
                        combineDataStates(
                            totpCodeDataState,
                            cipherDataState,
                        ) { totpCodeData, _ ->
                            // Just return the verification items; we are only combining the
                            // DataStates to know the overall state.
                            totpCodeData
                        }
                    }
            }
            .stateIn(
                scope = unconfinedScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = DataState.Loading,
            )
    }

    override fun emitTotpCodeResult(totpCodeResult: TotpCodeResult) {
        mutableTotpCodeResultFlow.tryEmit(totpCodeResult)
    }

    @Suppress("ReturnCount")
    override suspend fun unlockVaultWithBiometrics(): VaultUnlockResult {
        val userId = activeUserId ?: return VaultUnlockResult.InvalidStateError
        val biometricsKey = authDiskSource
            .getUserBiometricUnlockKey(userId = userId)
            ?: return VaultUnlockResult.InvalidStateError
        return unlockVaultForUser(
            userId = userId,
            initUserCryptoMethod = InitUserCryptoMethod.DecryptedKey(
                decryptedUserKey = biometricsKey,
            ),
        )
            .also {
                if (it is VaultUnlockResult.Success) {
                    deriveTemporaryPinProtectedUserKeyIfNecessary(userId = userId)
                }
            }
    }

    @Suppress("ReturnCount")
    override suspend fun unlockVaultWithMasterPassword(
        masterPassword: String,
    ): VaultUnlockResult {
        val userId = activeUserId ?: return VaultUnlockResult.InvalidStateError
        val userKey = authDiskSource.getUserKey(userId = userId)
            ?: return VaultUnlockResult.InvalidStateError
        return unlockVaultForUser(
            userId = userId,
            initUserCryptoMethod = InitUserCryptoMethod.Password(
                password = masterPassword,
                userKey = userKey,
            ),
        )
            .also {
                if (it is VaultUnlockResult.Success) {
                    deriveTemporaryPinProtectedUserKeyIfNecessary(userId = userId)
                }
            }
    }

    @Suppress("ReturnCount")
    override suspend fun unlockVaultWithPin(
        pin: String,
    ): VaultUnlockResult {
        val userId = activeUserId ?: return VaultUnlockResult.InvalidStateError
        val pinProtectedUserKey = authDiskSource.getPinProtectedUserKey(userId = userId)
            ?: return VaultUnlockResult.InvalidStateError
        return unlockVaultForUser(
            userId = userId,
            initUserCryptoMethod = InitUserCryptoMethod.Pin(
                pin = pin,
                pinProtectedUserKey = pinProtectedUserKey,
            ),
        )
    }

    override suspend fun unlockVault(
        userId: String,
        masterPassword: String,
        email: String,
        kdf: Kdf,
        userKey: String,
        privateKey: String,
        organizationKeys: Map<String, String>?,
    ): VaultUnlockResult =
        unlockVault(
            userId = userId,
            email = email,
            kdf = kdf,
            privateKey = privateKey,
            initUserCryptoMethod = InitUserCryptoMethod.Password(
                password = masterPassword,
                userKey = userKey,
            ),
            organizationKeys = organizationKeys,
        )

    override suspend fun createCipher(cipherView: CipherView): CreateCipherResult {
        val userId = activeUserId ?: return CreateCipherResult.Error
        return vaultSdkSource
            .encryptCipher(
                userId = userId,
                cipherView = cipherView,
            )
            .flatMap { cipher ->
                ciphersService
                    .createCipher(
                        body = cipher.toEncryptedNetworkCipher(),
                    )
            }
            .fold(
                onFailure = {
                    CreateCipherResult.Error
                },
                onSuccess = {
                    vaultDiskSource.saveCipher(userId = userId, cipher = it)
                    CreateCipherResult.Success
                },
            )
    }

    override suspend fun createCipherInOrganization(
        cipherView: CipherView,
        collectionIds: List<String>,
    ): CreateCipherResult {
        val userId = activeUserId ?: return CreateCipherResult.Error
        return vaultSdkSource
            .encryptCipher(
                userId = userId,
                cipherView = cipherView,
            )
            .flatMap { cipher ->
                ciphersService
                    .createCipherInOrganization(
                        body = CreateCipherInOrganizationJsonRequest(
                            cipher = cipher.toEncryptedNetworkCipher(),
                            collectionIds = collectionIds,
                        ),
                    )
            }
            .onSuccess { vaultDiskSource.saveCipher(userId = userId, cipher = it) }
            .fold(
                onFailure = { CreateCipherResult.Error },
                onSuccess = { CreateCipherResult.Success },
            )
    }

    override suspend fun hardDeleteCipher(cipherId: String): DeleteCipherResult {
        val userId = activeUserId ?: return DeleteCipherResult.Error
        return ciphersService
            .hardDeleteCipher(cipherId)
            .onSuccess { vaultDiskSource.deleteCipher(userId, cipherId) }
            .fold(
                onSuccess = { DeleteCipherResult.Success },
                onFailure = { DeleteCipherResult.Error },
            )
    }

    override suspend fun softDeleteCipher(
        cipherId: String,
        cipherView: CipherView,
    ): DeleteCipherResult {
        val userId = activeUserId ?: return DeleteCipherResult.Error
        return ciphersService
            .softDeleteCipher(cipherId)
            .fold(
                onSuccess = {
                    vaultSdkSource
                        .encryptCipher(
                            userId = userId,
                            cipherView = cipherView.copy(
                                deletedDate = Instant.now(),
                            ),
                        )
                        .onSuccess { cipher ->
                            vaultDiskSource.saveCipher(
                                userId = userId,
                                cipher = cipher.toEncryptedNetworkCipherResponse(),
                            )
                        }
                    DeleteCipherResult.Success
                },
                onFailure = { DeleteCipherResult.Error },
            )
    }

    override suspend fun deleteCipherAttachment(
        cipherId: String,
        attachmentId: String,
        cipherView: CipherView,
    ): DeleteAttachmentResult {
        val userId = activeUserId ?: return DeleteAttachmentResult.Error
        return ciphersService
            .deleteCipherAttachment(
                cipherId = cipherId,
                attachmentId = attachmentId,
            )
            .flatMap {
                vaultSdkSource
                    .encryptCipher(
                        userId = userId,
                        cipherView = cipherView.copy(
                            attachments = cipherView.attachments?.mapNotNull {
                                if (it.id == attachmentId) null else it
                            },
                        ),
                    )
            }
            .onSuccess { cipher ->
                vaultDiskSource.saveCipher(
                    userId = userId,
                    cipher = cipher.toEncryptedNetworkCipherResponse(),
                )
            }
            .fold(
                onSuccess = { DeleteAttachmentResult.Success },
                onFailure = { DeleteAttachmentResult.Error },
            )
    }

    override suspend fun restoreCipher(
        cipherId: String,
        cipherView: CipherView,
    ): RestoreCipherResult {
        val userId = activeUserId ?: return RestoreCipherResult.Error
        return ciphersService
            .restoreCipher(cipherId)
            .flatMap {
                vaultSdkSource.encryptCipher(
                    userId = userId,
                    cipherView = cipherView.copy(
                        deletedDate = null,
                    ),
                )
            }
            .onSuccess { cipher ->
                vaultDiskSource.saveCipher(
                    userId = userId,
                    cipher = cipher.toEncryptedNetworkCipherResponse(),
                )
            }
            .fold(
                onSuccess = { RestoreCipherResult.Success },
                onFailure = { RestoreCipherResult.Error },
            )
    }

    override suspend fun updateCipher(
        cipherId: String,
        cipherView: CipherView,
    ): UpdateCipherResult {
        val userId = activeUserId ?: return UpdateCipherResult.Error(null)
        return vaultSdkSource
            .encryptCipher(
                userId = userId,
                cipherView = cipherView,
            )
            .flatMap { cipher ->
                ciphersService.updateCipher(
                    cipherId = cipherId,
                    body = cipher.toEncryptedNetworkCipher(),
                )
            }
            .fold(
                onFailure = { UpdateCipherResult.Error(errorMessage = null) },
                onSuccess = { response ->
                    when (response) {
                        is UpdateCipherResponseJson.Invalid -> {
                            UpdateCipherResult.Error(errorMessage = response.message)
                        }

                        is UpdateCipherResponseJson.Success -> {
                            vaultDiskSource.saveCipher(userId = userId, cipher = response.cipher)
                            UpdateCipherResult.Success
                        }
                    }
                },
            )
    }

    override suspend fun shareCipher(
        cipherId: String,
        cipherView: CipherView,
        collectionIds: List<String>,
    ): ShareCipherResult {
        val userId = activeUserId ?: return ShareCipherResult.Error
        return vaultSdkSource
            .encryptCipher(
                userId = userId,
                cipherView = cipherView,
            )
            .flatMap { cipher ->
                ciphersService.shareCipher(
                    cipherId = cipherId,
                    body = ShareCipherJsonRequest(
                        cipher = cipher.toEncryptedNetworkCipher(),
                        collectionIds = collectionIds,
                    ),
                )
            }
            .onSuccess { vaultDiskSource.saveCipher(userId = userId, cipher = it) }
            .fold(
                onFailure = { ShareCipherResult.Error },
                onSuccess = { ShareCipherResult.Success },
            )
    }

    override suspend fun updateCipherCollections(
        cipherId: String,
        cipherView: CipherView,
        collectionIds: List<String>,
    ): ShareCipherResult {
        val userId = activeUserId ?: return ShareCipherResult.Error
        return ciphersService
            .updateCipherCollections(
                cipherId = cipherId,
                body = UpdateCipherCollectionsJsonRequest(collectionIds = collectionIds),
            )
            .flatMap {
                vaultSdkSource
                    .encryptCipher(
                        userId = userId,
                        cipherView = cipherView.copy(collectionIds = collectionIds),
                    )
            }
            .onSuccess { cipher ->
                vaultDiskSource.saveCipher(
                    userId = userId,
                    cipher = cipher.toEncryptedNetworkCipherResponse(),
                )
            }
            .fold(
                onSuccess = { ShareCipherResult.Success },
                onFailure = { ShareCipherResult.Error },
            )
    }

    override suspend fun createAttachment(
        cipherId: String,
        cipherView: CipherView,
        fileSizeBytes: String,
        fileName: String,
        fileUri: Uri,
    ): CreateAttachmentResult {
        val userId = activeUserId ?: return CreateAttachmentResult.Error
        val attachmentView = AttachmentView(
            id = null,
            url = null,
            size = fileSizeBytes,
            sizeName = null,
            fileName = fileName,
            key = null,
        )
        return vaultSdkSource
            .encryptCipher(
                userId = userId,
                cipherView = cipherView,
            )
            .flatMap { cipher ->
                vaultSdkSource.encryptAttachment(
                    userId = userId,
                    cipher = cipher,
                    attachmentView = attachmentView,
                    fileBuffer = fileManager.uriToByteArray(fileUri = fileUri),
                )
            }
            .flatMap { attachmentEncryptResult ->
                ciphersService
                    .createAttachment(
                        cipherId = cipherId,
                        body = AttachmentJsonRequest(
                            // We know these values are present because
                            // - the filename/size are passed into the function
                            // - the SDK call fills in the key
                            fileName = requireNotNull(attachmentEncryptResult.attachment.fileName),
                            key = requireNotNull(attachmentEncryptResult.attachment.key),
                            fileSize = requireNotNull(attachmentEncryptResult.attachment.size),
                        ),
                    )
                    .flatMap { attachmentJsonResponse ->
                        ciphersService.uploadAttachment(
                            attachmentJsonResponse = attachmentJsonResponse,
                            encryptedFile = attachmentEncryptResult.contents,
                        )
                    }
            }
            .onSuccess {
                // Save the send immediately, regardless of whether the decrypt succeeds
                vaultDiskSource.saveCipher(userId = userId, cipher = it)
            }
            .flatMap {
                vaultSdkSource.decryptCipher(
                    userId = userId,
                    cipher = it.toEncryptedSdkCipher(),
                )
            }
            .fold(
                onFailure = { CreateAttachmentResult.Error },
                onSuccess = { CreateAttachmentResult.Success(it) },
            )
    }

    @Suppress("ReturnCount")
    override suspend fun downloadAttachment(
        cipherView: CipherView,
        attachmentId: String,
    ): DownloadAttachmentResult {
        val userId = requireNotNull(authDiskSource.userState?.activeUserId)

        val cipher = vaultSdkSource
            .encryptCipher(
                userId = userId,
                cipherView = cipherView,
            )
            .fold(
                onSuccess = { it },
                onFailure = { return DownloadAttachmentResult.Failure },
            )
        val attachment = cipher.attachments?.find { it.id == attachmentId }
            ?: return DownloadAttachmentResult.Failure

        val attachmentData = ciphersService
            .getCipherAttachment(
                cipherId = requireNotNull(cipher.id),
                attachmentId = attachmentId,
            )
            .fold(
                onSuccess = { it },
                onFailure = { return DownloadAttachmentResult.Failure },
            )

        val url = attachmentData.url ?: return DownloadAttachmentResult.Failure

        val encryptedFile = when (val result = fileManager.downloadFileToCache(url)) {
            DownloadResult.Failure -> return DownloadAttachmentResult.Failure
            is DownloadResult.Success -> result.file
        }

        val decryptedFile = File(encryptedFile.path + "_decrypted")
        return vaultSdkSource
            .decryptFile(
                userId = userId,
                cipher = cipher,
                attachment = attachment,
                encryptedFilePath = encryptedFile.path,
                decryptedFilePath = decryptedFile.path,
            )
            .fold(
                onSuccess = {
                    encryptedFile.delete()
                    DownloadAttachmentResult.Success(decryptedFile)
                },
                onFailure = {
                    encryptedFile.delete()
                    DownloadAttachmentResult.Failure
                },
            )
    }

    override suspend fun createSend(
        sendView: SendView,
        fileUri: Uri?,
    ): CreateSendResult {
        val userId = activeUserId ?: return CreateSendResult.Error
        return vaultSdkSource
            .encryptSend(
                userId = userId,
                sendView = sendView,
            )
            .flatMap { send ->
                when (send.type) {
                    SendType.TEXT -> {
                        sendsService.createTextSend(body = send.toEncryptedNetworkSend())
                    }

                    SendType.FILE -> {
                        val uri = fileUri ?: return@flatMap IllegalArgumentException(
                            "File URI must be present to create a File Send.",
                        )
                            .asFailure()
                        vaultSdkSource
                            .encryptBuffer(
                                userId = userId,
                                send = send,
                                fileBuffer = fileManager.uriToByteArray(fileUri = uri),
                            )
                            .flatMap { encryptedFile ->
                                sendsService
                                    .createFileSend(
                                        body = send.toEncryptedNetworkSend(
                                            fileLength = encryptedFile.size,
                                        ),
                                    )
                                    .flatMap { sendFileResponse ->
                                        sendsService.uploadFile(
                                            sendFileResponse = sendFileResponse,
                                            encryptedFile = encryptedFile,
                                        )
                                    }
                            }
                    }
                }
            }
            .onSuccess {
                // Save the send immediately, regardless of whether the decrypt succeeds
                vaultDiskSource.saveSend(userId = userId, send = it)
            }
            .flatMap {
                vaultSdkSource.decryptSend(
                    userId = userId,
                    send = it.toEncryptedSdkSend(),
                )
            }
            .fold(
                onFailure = { CreateSendResult.Error },
                onSuccess = { CreateSendResult.Success(it) },
            )
    }

    override suspend fun updateSend(
        sendId: String,
        sendView: SendView,
    ): UpdateSendResult {
        val userId = activeUserId ?: return UpdateSendResult.Error(null)
        return vaultSdkSource
            .encryptSend(
                userId = userId,
                sendView = sendView,
            )
            .flatMap { send ->
                sendsService.updateSend(
                    sendId = sendId,
                    body = send.toEncryptedNetworkSend(),
                )
            }
            .fold(
                onFailure = { UpdateSendResult.Error(errorMessage = null) },
                onSuccess = { response ->
                    when (response) {
                        is UpdateSendResponseJson.Invalid -> {
                            UpdateSendResult.Error(errorMessage = response.message)
                        }

                        is UpdateSendResponseJson.Success -> {
                            vaultDiskSource.saveSend(userId = userId, send = response.send)
                            vaultSdkSource
                                .decryptSend(
                                    userId = userId,
                                    send = response.send.toEncryptedSdkSend(),
                                )
                                .getOrNull()
                                ?.let { UpdateSendResult.Success(sendView = it) }
                                ?: UpdateSendResult.Error(errorMessage = null)
                        }
                    }
                },
            )
    }

    override suspend fun removePasswordSend(sendId: String): RemovePasswordSendResult {
        val userId = activeUserId ?: return RemovePasswordSendResult.Error(null)
        return sendsService
            .removeSendPassword(sendId = sendId)
            .fold(
                onSuccess = { response ->
                    when (response) {
                        is UpdateSendResponseJson.Invalid -> {
                            RemovePasswordSendResult.Error(errorMessage = response.message)
                        }

                        is UpdateSendResponseJson.Success -> {
                            vaultDiskSource.saveSend(userId = userId, send = response.send)
                            vaultSdkSource
                                .decryptSend(
                                    userId = userId,
                                    send = response.send.toEncryptedSdkSend(),
                                )
                                .getOrNull()
                                ?.let { RemovePasswordSendResult.Success(sendView = it) }
                                ?: RemovePasswordSendResult.Error(errorMessage = null)
                        }
                    }
                },
                onFailure = { RemovePasswordSendResult.Error(errorMessage = null) },
            )
    }

    override suspend fun deleteSend(sendId: String): DeleteSendResult {
        val userId = activeUserId ?: return DeleteSendResult.Error
        return sendsService
            .deleteSend(sendId)
            .onSuccess { vaultDiskSource.deleteSend(userId, sendId) }
            .fold(
                onSuccess = { DeleteSendResult.Success },
                onFailure = { DeleteSendResult.Error },
            )
    }

    override suspend fun generateTotp(
        totpCode: String,
        time: DateTime,
    ): GenerateTotpResult {
        val userId = activeUserId ?: return GenerateTotpResult.Error
        return vaultSdkSource.generateTotp(
            time = time,
            userId = userId,
            totp = totpCode,
        )
            .fold(
                onSuccess = {
                    GenerateTotpResult.Success(
                        code = it.code,
                        periodSeconds = it.period.toInt(),
                    )
                },
                onFailure = { GenerateTotpResult.Error },
            )
    }

    override suspend fun createFolder(folderView: FolderView): CreateFolderResult {
        val userId = activeUserId ?: return CreateFolderResult.Error
        return vaultSdkSource
            .encryptFolder(
                userId = userId,
                folder = folderView,
            )
            .flatMap { folder ->
                folderService
                    .createFolder(
                        body = folder.toEncryptedNetworkFolder(),
                    )
            }
            .onSuccess { vaultDiskSource.saveFolder(userId = userId, folder = it) }
            .flatMap { vaultSdkSource.decryptFolder(userId, it.toEncryptedSdkFolder()) }
            .fold(
                onSuccess = { CreateFolderResult.Success(folderView = it) },
                onFailure = { CreateFolderResult.Error },
            )
    }

    override suspend fun updateFolder(
        folderId: String,
        folderView: FolderView,
    ): UpdateFolderResult {
        val userId = activeUserId ?: return UpdateFolderResult.Error(null)
        return vaultSdkSource
            .encryptFolder(
                userId = userId,
                folder = folderView,
            )
            .flatMap { folder ->
                folderService
                    .updateFolder(
                        folderId = folder.id.toString(),
                        body = folder.toEncryptedNetworkFolder(),
                    )
            }
            .fold(
                onSuccess = { response ->
                    when (response) {
                        is UpdateFolderResponseJson.Success -> {
                            vaultDiskSource.saveFolder(userId, response.folder)
                            vaultSdkSource
                                .decryptFolder(
                                    userId,
                                    response.folder.toEncryptedSdkFolder(),
                                )
                                .fold(
                                    onSuccess = { UpdateFolderResult.Success(it) },
                                    onFailure = { UpdateFolderResult.Error(errorMessage = null) },
                                )
                        }

                        is UpdateFolderResponseJson.Invalid -> {
                            UpdateFolderResult.Error(response.message)
                        }
                    }
                },
                onFailure = { UpdateFolderResult.Error(it.message) },
            )
    }

    override suspend fun deleteFolder(folderId: String): DeleteFolderResult {
        val userId = activeUserId ?: return DeleteFolderResult.Error
        return folderService
            .deleteFolder(
                folderId = folderId,
            )
            .onSuccess {
                clearFolderIdFromCiphers(folderId, userId)
                vaultDiskSource.deleteFolder(userId, folderId)
            }
            .fold(
                onSuccess = { DeleteFolderResult.Success },
                onFailure = { DeleteFolderResult.Error },
            )
    }

    private suspend fun clearFolderIdFromCiphers(folderId: String, userId: String) {
        vaultDiskSource.getCiphers(userId).firstOrNull()?.forEach {
            if (it.folderId == folderId) {
                vaultDiskSource.saveCipher(
                    userId, it.copy(folderId = null),
                )
            }
        }
    }

    /**
     * Checks if the given [userId] has an associated encrypted PIN key but not a pin-protected user
     * key. This indicates a scenario in which a user has requested PIN unlocking but requires
     * master-password unlocking on app restart. This function may then be called after such an
     * unlock to derive a pin-protected user key and store it in memory for use for any subsequent
     * unlocks during this current app session.
     *
     * If the user's vault has not yet been unlocked, this call will do nothing.
     */
    private suspend fun deriveTemporaryPinProtectedUserKeyIfNecessary(userId: String) {
        val encryptedPin = authDiskSource.getEncryptedPin(userId = userId) ?: return
        val existingPinProtectedUserKey = authDiskSource.getPinProtectedUserKey(userId = userId)
        if (existingPinProtectedUserKey != null) return
        vaultSdkSource
            .derivePinProtectedUserKey(
                userId = userId,
                encryptedPin = encryptedPin,
            )
            .onSuccess { pinProtectedUserKey ->
                authDiskSource.storePinProtectedUserKey(
                    userId = userId,
                    pinProtectedUserKey = pinProtectedUserKey,
                    inMemoryOnly = true,
                )
            }
    }

    private fun storeProfileData(
        syncResponse: SyncResponseJson,
    ) {
        val profile = syncResponse.profile
        val userId = profile.id
        val userKey = profile.key
        val privateKey = profile.privateKey
        authDiskSource.apply {
            storeUserKey(
                userId = userId,
                userKey = userKey,
            )
            storePrivateKey(
                userId = userId,
                privateKey = privateKey,
            )
            storeOrganizationKeys(
                userId = profile.id,
                organizationKeys = profile.organizations
                    .orEmpty()
                    .filter { it.key != null }
                    .associate { it.id to requireNotNull(it.key) },
            )
            storeOrganizations(
                userId = profile.id,
                organizations = syncResponse.profile.organizations,
            )
        }
    }

    @Suppress("ReturnCount")
    private suspend fun unlockVaultForUser(
        userId: String,
        initUserCryptoMethod: InitUserCryptoMethod,
    ): VaultUnlockResult {
        val account = authDiskSource.userState?.accounts?.get(userId)
            ?: return VaultUnlockResult.InvalidStateError
        val privateKey = authDiskSource.getPrivateKey(userId = userId)
            ?: return VaultUnlockResult.InvalidStateError
        val organizationKeys = authDiskSource
            .getOrganizationKeys(userId = userId)
        return unlockVault(
            userId = userId,
            email = account.profile.email,
            kdf = account.profile.toSdkParams(),
            privateKey = privateKey,
            initUserCryptoMethod = initUserCryptoMethod,
            organizationKeys = organizationKeys,
        )
    }

    private suspend fun unlockVaultForOrganizationsIfNecessary(
        syncResponse: SyncResponseJson,
    ) {
        val profile = syncResponse.profile
        val organizationKeys = profile.organizations
            .orEmpty()
            .filter { it.key != null }
            .associate { it.id to requireNotNull(it.key) }
            .takeUnless { it.isEmpty() }
            ?: return

        // There shouldn't be issues when unlocking directly from the syncResponse so we can ignore
        // the return type here.
        vaultSdkSource
            .initializeOrganizationCrypto(
                userId = syncResponse.profile.id,
                request = InitOrgCryptoRequest(
                    organizationKeys = organizationKeys,
                ),
            )
    }

    private fun observeVaultDiskCiphers(
        userId: String,
    ): Flow<DataState<List<CipherView>>> =
        vaultDiskSource
            .getCiphers(userId = userId)
            .onStart {
                mutableCiphersStateFlow.value = DataState.Loading
            }
            .map {
                vaultSdkSource
                    .decryptCipherList(
                        userId = userId,
                        cipherList = it.toEncryptedSdkCipherList(),
                    )
                    .fold(
                        onSuccess = { ciphers -> DataState.Loaded(ciphers.sortAlphabetically()) },
                        onFailure = { throwable -> DataState.Error(throwable) },
                    )
            }
            .onEach { mutableCiphersStateFlow.value = it }

    private fun observeVaultDiskDomains(
        userId: String,
    ): Flow<DataState<DomainsData>> =
        vaultDiskSource
            .getDomains(userId = userId)
            .onStart { mutableDomainsStateFlow.value = DataState.Loading }
            .map {
                DataState.Loaded(
                    data = it.toDomainsData(),
                )
            }
            .onEach { mutableDomainsStateFlow.value = it }

    private fun observeVaultDiskFolders(
        userId: String,
    ): Flow<DataState<List<FolderView>>> =
        vaultDiskSource
            .getFolders(userId = userId)
            .onStart { mutableFoldersStateFlow.value = DataState.Loading }
            .map {
                vaultSdkSource
                    .decryptFolderList(
                        userId = userId,
                        folderList = it.toEncryptedSdkFolderList(),
                    )
                    .fold(
                        onSuccess = { folders -> DataState.Loaded(folders.sortAlphabetically()) },
                        onFailure = { throwable -> DataState.Error(throwable) },
                    )
            }
            .onEach { mutableFoldersStateFlow.value = it }

    private fun observeVaultDiskCollections(
        userId: String,
    ): Flow<DataState<List<CollectionView>>> =
        vaultDiskSource
            .getCollections(userId = userId)
            .onStart { mutableCollectionsStateFlow.value = DataState.Loading }
            .map {
                vaultSdkSource
                    .decryptCollectionList(
                        userId = userId,
                        collectionList = it.toEncryptedSdkCollectionList(),
                    )
                    .fold(
                        onSuccess = { collections ->
                            DataState.Loaded(
                                collections.sortAlphabetically(),
                            )
                        },
                        onFailure = { throwable -> DataState.Error(throwable) },
                    )
            }
            .onEach { mutableCollectionsStateFlow.value = it }

    private fun observeVaultDiskSends(
        userId: String,
    ): Flow<DataState<SendData>> =
        vaultDiskSource
            .getSends(userId = userId)
            .onStart { mutableSendDataStateFlow.value = DataState.Loading }
            .map {
                vaultSdkSource
                    .decryptSendList(
                        userId = userId,
                        sendList = it.toEncryptedSdkSendList(),
                    )
                    .fold(
                        onSuccess = { sends -> DataState.Loaded(SendData(sends)) },
                        onFailure = { throwable -> DataState.Error(throwable) },
                    )
            }
            .onEach { mutableSendDataStateFlow.value = it }

    //region Push notification helpers
    /**
     * Deletes the cipher specified by [syncCipherDeleteData] from disk.
     */
    private suspend fun deleteCipher(syncCipherDeleteData: SyncCipherDeleteData) {
        val userId = activeUserId ?: return

        val cipherId = syncCipherDeleteData.cipherId
        vaultDiskSource.deleteCipher(
            userId = userId,
            cipherId = cipherId,
        )
    }

    /**
     * Syncs an individual cipher contained in [syncCipherUpsertData] to disk if certain criteria
     * are met. If the resource cannot be found cloud-side, and it was updated, delete it from disk
     * for now.
     */
    @Suppress("ReturnCount")
    private suspend fun syncCipherIfNecessary(syncCipherUpsertData: SyncCipherUpsertData) {
        val userId = activeUserId ?: return
        val cipherId = syncCipherUpsertData.cipherId
        val organizationId = syncCipherUpsertData.organizationId
        val collectionIds = syncCipherUpsertData.collectionIds
        val revisionDate = syncCipherUpsertData.revisionDate
        val isUpdate = syncCipherUpsertData.isUpdate

        val localCipher = ciphersStateFlow
            .mapNotNull { it.data }
            .first()
            .find { it.id == cipherId }

        // Return if local cipher is more recent
        if (localCipher != null &&
            localCipher.revisionDate.epochSecond > revisionDate.toEpochSecond()
        ) {
            return
        }

        var shouldUpdate: Boolean
        val shouldCheckCollections: Boolean

        when {
            isUpdate -> {
                shouldUpdate = localCipher != null
                shouldCheckCollections = true
            }

            collectionIds == null || organizationId == null -> {
                shouldUpdate = localCipher == null
                shouldCheckCollections = false
            }

            else -> {
                shouldUpdate = false
                shouldCheckCollections = true
            }
        }

        if (!shouldUpdate && shouldCheckCollections && organizationId != null) {
            // Check if there are any collections in common
            shouldUpdate = collectionsStateFlow
                .mapNotNull { it.data }
                .first()
                .mapNotNull { it.id }
                .any { collectionIds?.contains(it) == true } == true
        }

        if (!shouldUpdate) return

        ciphersService
            .getCipher(cipherId)
            .fold(
                onSuccess = { vaultDiskSource.saveCipher(userId, it) },
                onFailure = {
                    // Delete any updates if it's missing from the server
                    val httpException = it as? HttpException
                    @Suppress("MagicNumber")
                    if (httpException?.code() == 404 && isUpdate) {
                        vaultDiskSource.deleteCipher(userId = userId, cipherId = cipherId)
                    }
                },
            )
    }

    /**
     * Deletes the send specified by [syncSendDeleteData] from disk.
     */
    private suspend fun deleteSend(syncSendDeleteData: SyncSendDeleteData) {
        val userId = activeUserId ?: return

        val sendId = syncSendDeleteData.sendId
        vaultDiskSource.deleteSend(
            userId = userId,
            sendId = sendId,
        )
    }

    /**
     * Syncs an individual send contained in [syncSendUpsertData] to disk if certain criteria are
     * met. If the resource cannot be found cloud-side, and it was updated, delete it from disk for
     * now.
     */
    private suspend fun syncSendIfNecessary(syncSendUpsertData: SyncSendUpsertData) {
        val userId = activeUserId ?: return
        val sendId = syncSendUpsertData.sendId
        val isUpdate = syncSendUpsertData.isUpdate
        val revisionDate = syncSendUpsertData.revisionDate

        val localSend = sendDataStateFlow
            .mapNotNull { it.data }
            .first()
            .sendViewList
            .find { it.id == sendId }
        val isValidCreate = !isUpdate && localSend == null
        val isValidUpdate = isUpdate &&
            localSend != null &&
            localSend.revisionDate.epochSecond < revisionDate.toEpochSecond()

        if (!isValidCreate && !isValidUpdate) return

        sendsService
            .getSend(sendId)
            .fold(
                onSuccess = { vaultDiskSource.saveSend(userId, it) },
                onFailure = {
                    // Delete any updates if it's missing from the server
                    val httpException = it as? HttpException
                    @Suppress("MagicNumber")
                    if (httpException?.code() == 404 && isUpdate) {
                        vaultDiskSource.deleteSend(userId = userId, sendId = sendId)
                    }
                },
            )
    }

    /**
     * Deletes the folder specified by [syncFolderDeleteData] from disk.
     */
    private suspend fun deleteFolder(syncFolderDeleteData: SyncFolderDeleteData) {
        val userId = activeUserId ?: return

        val folderId = syncFolderDeleteData.folderId
        clearFolderIdFromCiphers(
            folderId = folderId,
            userId = userId,
        )
        vaultDiskSource.deleteFolder(
            folderId = folderId,
            userId = userId,
        )
    }

    /**
     * Syncs an individual folder contained in [syncFolderUpsertData] to disk if certain criteria
     * are met.
     */
    private suspend fun syncFolderIfNecessary(syncFolderUpsertData: SyncFolderUpsertData) {
        val userId = activeUserId ?: return
        val folderId = syncFolderUpsertData.folderId
        val isUpdate = syncFolderUpsertData.isUpdate
        val revisionDate = syncFolderUpsertData.revisionDate

        val localFolder = foldersStateFlow
            .mapNotNull { it.data }
            .first()
            .find { it.id == folderId }
        val isValidCreate = !isUpdate && localFolder == null
        val isValidUpdate = isUpdate &&
            localFolder != null &&
            localFolder.revisionDate.epochSecond < revisionDate.toEpochSecond()

        if (!isValidCreate && !isValidUpdate) return

        folderService
            .getFolder(folderId)
            .onSuccess { vaultDiskSource.saveFolder(userId, it) }
    }
    //endregion Push Notification helpers
}

private fun <T> Throwable.toNetworkOrErrorState(data: T?): DataState<T> =
    if (isNoConnectionError()) {
        DataState.NoNetwork(data = data)
    } else {
        DataState.Error(
            error = this,
            data = data,
        )
    }