package com.x8bit.bitwarden.data.platform.repository

import com.x8bit.bitwarden.data.auth.datasource.disk.AuthDiskSource
import com.x8bit.bitwarden.data.platform.datasource.disk.EnvironmentDiskSource
import com.x8bit.bitwarden.data.platform.manager.dispatcher.DispatcherManager
import com.x8bit.bitwarden.data.platform.repository.model.Environment
import com.x8bit.bitwarden.data.platform.repository.util.toEnvironmentUrlsOrDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Primary implementation of [EnvironmentRepository].
 */
class EnvironmentRepositoryImpl(
    private val environmentDiskSource: EnvironmentDiskSource,
    private val serverConfigRepository: ServerConfigRepository,
    authDiskSource: AuthDiskSource,
    dispatcherManager: DispatcherManager,
) : EnvironmentRepository {

    private val scope = CoroutineScope(dispatcherManager.io)

    override var environment: Environment
        get() = environmentDiskSource
            .preAuthEnvironmentUrlData
            .toEnvironmentUrlsOrDefault()
        set(value) {
            environmentDiskSource.preAuthEnvironmentUrlData = value.environmentUrlData
            scope.launch {
                // Fetch new server configs on environment change
                serverConfigRepository.getServerConfig(forceRefresh = true)
            }
        }

    override val environmentStateFlow: StateFlow<Environment>
        get() = environmentDiskSource
            .preAuthEnvironmentUrlDataFlow
            .map { it.toEnvironmentUrlsOrDefault() }
            .stateIn(
                scope = scope,
                started = SharingStarted.Lazily,
                initialValue = Environment.Us,
            )

    init {
        authDiskSource
            .userStateFlow
            .onEach { userState ->
                // If the active account has environment data, set that as the current value.
                userState
                    ?.activeAccount
                    ?.settings
                    ?.environmentUrlData
                    ?.let {
                        environmentDiskSource.preAuthEnvironmentUrlData = it
                        // Fetch new server configs on active account  change
                        serverConfigRepository.getServerConfig(forceRefresh = true)
                    }
            }
            .launchIn(scope)
    }
}
