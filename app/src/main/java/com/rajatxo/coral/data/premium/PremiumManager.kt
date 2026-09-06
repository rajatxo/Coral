package com.rajatxo.coral.data.premium

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for Coral's premium entitlement.
 *
 * Production (Play Store) flow:
 *   1. Google Play Billing reports the user's purchases to the app.
 *   2. We verify that an active "Coral Premium" subscription exists.
 *   3. We set [isPremium] = true.
 *   4. All premium features check [isPremium] before unlocking.
 *
 * Debug (current) flow:
 *   - [isPremium] starts as false.
 *   - Long-pressing the version row in Settings 7 times calls [debugUnlock()].
 *   - The state is kept in memory (resets on app restart) — enough for testing.
 *
 * Phase 7B will replace [debugUnlock] with the real Google Play Billing
 * integration. The rest of the app won't need to change because every premium
 * feature already queries [isPremium].
 */
object PremiumManager {

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /**
     * Toggle the debug premium state. Called from Settings when the user
     * long-presses the version row 7 times. In production builds this will
     * be replaced by [applyGooglePlayBillingResult].
     */
    fun debugUnlock(): Boolean {
        _isPremium.value = !_isPremium.value
        return _isPremium.value
    }

    /**
     * Sets the premium state from Google Play Billing's result.
     * Phase 7B will call this when it receives purchase updates from the
     * BillingClient.
     */
    fun applyGooglePlayBillingResult(hasActivePremium: Boolean) {
        _isPremium.value = hasActivePremium
    }
}
