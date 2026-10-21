package com.example.data.billing

import com.android.billingclient.api.*
import com.example.data.entitlement.EntitlementRepository
import com.example.data.entitlement.SubscriptionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class BillingManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockEntitlementRepo: EntitlementRepository = mock()
    private val mockProvider: BillingProvider = mock()
    
    private lateinit var billingManager: BillingManager

    @Before
    fun setup() {
        billingManager = BillingManager(mock(), mockEntitlementRepo, testScope, mockProvider, testDispatcher)
    }

    @Test
    fun `Active monthly subscription purchase updates repository to PRO`() = runTest(testDispatcher) {
        val purchase: Purchase = mock()
        whenever(purchase.products).thenReturn(listOf("aura_pro_monthly"))
        whenever(purchase.purchaseState).thenReturn(Purchase.PurchaseState.PURCHASED)
        whenever(purchase.isAcknowledged).thenReturn(true)
        whenever(purchase.purchaseToken).thenReturn("monthly_token")

        whenever(mockProvider.isReady()).thenReturn(true)
        
        doAnswer {
            val listener = it.getArgument<PurchasesResponseListener>(1)
            listener.onQueryPurchasesResponse(
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build(),
                listOf(purchase)
            )
        }.whenever(mockProvider).queryPurchasesAsync(any(), any())

        billingManager.queryActivePurchases()

        verify(mockEntitlementRepo).updateEntitlement(
            eq(true),
            eq("monthly_token"),
            eq(SubscriptionType.MONTHLY),
            eq(0L)
        )
    }

    @Test
    fun `Active annual subscription purchase updates repository to PRO`() = runTest(testDispatcher) {
        val purchase: Purchase = mock()
        whenever(purchase.products).thenReturn(listOf("aura_pro_annual"))
        whenever(purchase.purchaseState).thenReturn(Purchase.PurchaseState.PURCHASED)
        whenever(purchase.isAcknowledged).thenReturn(true)
        whenever(purchase.purchaseToken).thenReturn("annual_token")

        whenever(mockProvider.isReady()).thenReturn(true)
        
        doAnswer {
            val listener = it.getArgument<PurchasesResponseListener>(1)
            listener.onQueryPurchasesResponse(
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build(),
                listOf(purchase)
            )
        }.whenever(mockProvider).queryPurchasesAsync(any(), any())

        billingManager.queryActivePurchases()

        verify(mockEntitlementRepo).updateEntitlement(
            eq(true),
            eq("annual_token"),
            eq(SubscriptionType.ANNUAL),
            eq(0L)
        )
    }

    @Test
    fun `No active subscription purchase updates repository to FREE`() = runTest(testDispatcher) {
        whenever(mockProvider.isReady()).thenReturn(true)
        
        doAnswer {
            val listener = it.getArgument<PurchasesResponseListener>(1)
            listener.onQueryPurchasesResponse(
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build(),
                emptyList()
            )
        }.whenever(mockProvider).queryPurchasesAsync(any(), any())

        billingManager.queryActivePurchases()

        verify(mockEntitlementRepo).updateEntitlement(
            eq(false),
            eq(null),
            any(),
            eq(0L)
        )
    }

    @Test
    fun `Unacknowledged subscription purchase triggers acknowledgement`() = runTest(testDispatcher) {
        val purchase: Purchase = mock()
        whenever(purchase.products).thenReturn(listOf("aura_pro_monthly"))
        whenever(purchase.purchaseState).thenReturn(Purchase.PurchaseState.PURCHASED)
        whenever(purchase.isAcknowledged).thenReturn(false)
        whenever(purchase.purchaseToken).thenReturn("unack_sub_token")

        whenever(mockProvider.isReady()).thenReturn(true)
        
        doAnswer {
            val listener = it.getArgument<PurchasesResponseListener>(1)
            listener.onQueryPurchasesResponse(
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build(),
                listOf(purchase)
            )
        }.whenever(mockProvider).queryPurchasesAsync(any(), any())

        billingManager.queryActivePurchases()

        verify(mockProvider).acknowledgePurchase(any(), any())
    }

    @Test
    fun `Restore purchases invokes active subscription query`() = runTest(testDispatcher) {
        whenever(mockProvider.isReady()).thenReturn(true)
        
        doAnswer {
            val listener = it.getArgument<PurchasesResponseListener>(1)
            listener.onQueryPurchasesResponse(
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build(),
                emptyList()
            )
        }.whenever(mockProvider).queryPurchasesAsync(any(), any())

        billingManager.restorePurchases()

        verify(mockProvider).queryPurchasesAsync(any(), any())
    }

    @Test
    fun `hasEligibleSevenDayTrial returns false for null ProductDetails`() = runTest(testDispatcher) {
        org.junit.Assert.assertFalse(billingManager.hasEligibleSevenDayTrial(null))
    }

    @Test
    fun `findBestOfferDetails returns null for null ProductDetails`() = runTest(testDispatcher) {
        org.junit.Assert.assertNull(billingManager.findBestOfferDetails(null))
    }
}
