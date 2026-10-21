package com.example.data.entitlement

/**
 * Type of subscription tier granting Pro access.
 */
enum class SubscriptionType {
    MONTHLY, ANNUAL, TRIAL
}

/**
 * Sealed representation of the user's entitlement state.
 * Ensures that 'Unknown' or 'Free' states do not accidentally grant Pro access.
 */
sealed class ProState {
    /**
     * Initial state before local persistence or Play Billing has been queried.
     * Must be treated as non-Pro for gating decisions.
     */
    object Unknown : ProState()

    /**
     * Confirmed non-Pro user.
     */
    object Free : ProState()

    /**
     * Confirmed Pro user with valid subscription or lifetime entitlement.
     * @param purchaseToken Optional token from Google Play for verification.
     * @param subscriptionType Type of subscription (Monthly, Annual, Trial, Lifetime).
     * @param expiresAtMillis Expiration timestamp if applicable.
     */
    data class Pro(
        val purchaseToken: String? = null,
        val subscriptionType: SubscriptionType = SubscriptionType.MONTHLY,
        val expiresAtMillis: Long = 0L
    ) : ProState()

    /**
     * Direct check for Pro entitlement.
     */
    val isPro: Boolean
        get() = this is Pro

    /**
     * Indicates if the state has moved beyond the initial Unknown phase.
     */
    val isInitialized: Boolean
        get() = this !is Unknown
}
