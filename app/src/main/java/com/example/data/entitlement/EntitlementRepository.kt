package com.example.data.entitlement

import android.util.Log
import com.example.data.db.UserPreferenceDao
import com.example.data.db.UserPreferenceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository responsible for orchestrating Aura Pro entitlement state.
 * 
 * Responsibilities:
 * 1. Maintain and expose the current [ProState].
 * 2. Persist local entitlement state to encrypted storage (SQLCipher).
 * 3. Enforce feature access decisions.
 * 4. Emit access-denied events for paywall triggering.
 */
class EntitlementRepository(
    private val preferenceDao: UserPreferenceDao,
    private val scope: CoroutineScope
) {
    private val _proState = MutableStateFlow<ProState>(ProState.Unknown)
    val proState: StateFlow<ProState> = _proState.asStateFlow()

    private val _accessDeniedEvents = MutableSharedFlow<ProFeature>()
    val accessDeniedEvents: SharedFlow<ProFeature> = _accessDeniedEvents.asSharedFlow()

    private val PREF_KEY_STATUS = "aura_pro_status_v1"
    private val TAG = "EntitlementRepository"

    init {
        loadLocalEntitlement()
    }

    /**
     * Loads local entitlement state.
     * Restores valid legacy lifetime Pro preferences without demoting to Free.
     */
    private fun loadLocalEntitlement() {
        scope.launch {
            try {
                val pref = preferenceDao.getPreference(PREF_KEY_STATUS)
                if (pref?.value == "PRO") {
                    _proState.value = ProState.Pro(purchaseToken = "LIFETIME_PURCHASE", subscriptionType = SubscriptionType.LIFETIME)
                    Log.i(TAG, "Local entitlement loaded: Valid Lifetime Pro restored.")
                } else {
                    _proState.value = ProState.Free
                    Log.d(TAG, "Local entitlement loaded: Free tier enforced.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load local entitlement", e)
                _proState.value = ProState.Free // Safety fallback
            }
        }
    }

    /**
     * Updates and persists the entitlement state.
     * To be called by BillingManager or manual restore actions.
     */
    suspend fun updateEntitlement(
        isPro: Boolean,
        purchaseToken: String? = null,
        subscriptionType: SubscriptionType = SubscriptionType.MONTHLY,
        expiresAtMillis: Long = 0L
    ) {
        val newState = if (isPro) {
            ProState.Pro(purchaseToken, subscriptionType, expiresAtMillis)
        } else {
            ProState.Free
        }
        _proState.value = newState
        
        try {
            preferenceDao.insertPreference(
                UserPreferenceEntity(PREF_KEY_STATUS, if (isPro) "PRO" else "FREE")
            )
            Log.i(TAG, "Entitlement updated and persisted: $newState")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist entitlement update", e)
        }
    }

    /**
     * Centralized access decision for Pro features.
     * If access is denied, a [ProFeature] event is emitted to trigger the paywall.
     */
    fun isFeatureAvailable(feature: ProFeature): Boolean {
        val current = _proState.value
        if (current.isPro) {
            return true
        } else {
            scope.launch {
                _accessDeniedEvents.emit(feature)
            }
            return false
        }
    }
}
