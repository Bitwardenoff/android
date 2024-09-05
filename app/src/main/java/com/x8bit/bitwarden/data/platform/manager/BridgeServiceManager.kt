package com.x8bit.bitwarden.data.platform.manager

import com.bitwarden.bridge.IBridgeService

/**
 * Provides access to [IBridgeService] APIs in an injectable and testable manner.
 */
interface BridgeServiceManager {

    /**
     * Binder that implements [IBridgeService]. Null can be returned to represent a no-op binder.
     */
    val binder: IBridgeService.Stub?
}
