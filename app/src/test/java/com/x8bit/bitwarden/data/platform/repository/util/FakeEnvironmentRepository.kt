package com.x8bit.bitwarden.data.platform.repository.util

import com.x8bit.bitwarden.data.platform.repository.EnvironmentRepository
import com.x8bit.bitwarden.data.platform.repository.model.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A faked implementation of [EnvironmentRepository] based on in-memory caching.
 */
class FakeEnvironmentRepository : EnvironmentRepository {
    private var saveCurrentEnvironmentForEmailCalled = false
    private var loadEnvironmentForEmailCalled = false

    override var environment: Environment
        get() = mutableEnvironmentStateFlow.value
        set(value) {
            mutableEnvironmentStateFlow.value = value
        }
    override val environmentStateFlow: StateFlow<Environment>
        get() = mutableEnvironmentStateFlow.asStateFlow()

    override fun saveCurrentEnvironmentForEmail(userEmail: String) {
        saveCurrentEnvironmentForEmailCalled = true
    }

    override fun loadEnvironmentForEmail(userEmail: String): Boolean {
        loadEnvironmentForEmailCalled = true
        return true
    }
    private val mutableEnvironmentStateFlow = MutableStateFlow<Environment>(Environment.Us)
}
