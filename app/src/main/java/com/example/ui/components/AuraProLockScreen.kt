package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import com.example.data.entitlement.SubscriptionType
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraProLockScreen(
    featureName: String,
    monthlyProductDetails: ProductDetails? = null,
    annualProductDetails: ProductDetails? = null,
    onBack: () -> Unit,
    onSubscribe: (SubscriptionType) -> Unit = {},
    onRestorePurchases: () -> Unit = {}
) {
    var selectedPlan by remember { mutableStateOf(SubscriptionType.ANNUAL) }
    
    val selectedDetails = if (selectedPlan == SubscriptionType.ANNUAL) annualProductDetails else monthlyProductDetails
    val hasTrial = remember(selectedDetails) {
        selectedDetails?.subscriptionOfferDetails?.any { offer ->
            offer.pricingPhases.pricingPhaseList.any { phase ->
                phase.priceAmountMicros == 0L && (phase.billingPeriod == "P1W" || phase.billingPeriod == "P7D")
            }
        } ?: false
    }

    val recurringPriceText = remember(selectedDetails) {
        selectedDetails?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.lastOrNull { it.priceAmountMicros > 0L }?.formattedPrice
    }

    val annualPriceText = remember(annualProductDetails) {
        annualProductDetails?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.lastOrNull { it.priceAmountMicros > 0L }?.formattedPrice
    }

    val monthlyPriceText = remember(monthlyProductDetails) {
        monthlyProductDetails?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.lastOrNull { it.priceAmountMicros > 0L }?.formattedPrice
    }

    val annualMonthlyEquivalent = remember(annualProductDetails) {
        annualProductDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList
            ?.lastOrNull { it.priceAmountMicros > 0L }?.let { phase ->
                if (phase.priceAmountMicros > 0L) {
                    val monthlyVal = (phase.priceAmountMicros / 12L) / 1_000_000.0
                    val symbol = if (phase.priceCurrencyCode == "USD") "$" else "${phase.priceCurrencyCode} "
                    String.format(java.util.Locale.US, "%s%.2f/mo", symbol, monthlyVal)
                } else null
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aura Pro", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onRestorePurchases) {
                        Text("Restore", fontWeight = FontWeight.Bold, color = DiscoveryViolet)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraCrispWhite)
            )
        },
        containerColor = AuraCrispWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    color = DiscoveryViolet.copy(alpha = 0.1f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = DiscoveryViolet,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = featureName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = AuraMidnight,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Unlock $featureName, Smart Cleanup automation, Iterative Visual Search, and advanced personalization with Aura Pro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuraMutedSlate,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Plan Selector Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Annual Plan Card
                    PlanCard(
                        title = "Annual Plan",
                        price = annualPriceText?.let { "$it / yr" } ?: "$49.99 / yr",
                        subtitle = annualMonthlyEquivalent ?: "$4.16 / month",
                        badge = "SAVE 30%",
                        isSelected = selectedPlan == SubscriptionType.ANNUAL,
                        onClick = { selectedPlan = SubscriptionType.ANNUAL },
                        modifier = Modifier.weight(1f)
                    )

                    // Monthly Plan Card
                    PlanCard(
                        title = "Monthly Plan",
                        price = monthlyPriceText?.let { "$it / mo" } ?: "$5.99 / mo",
                        subtitle = "Billed monthly",
                        badge = null,
                        isSelected = selectedPlan == SubscriptionType.MONTHLY,
                        onClick = { selectedPlan = SubscriptionType.MONTHLY },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Trial terms and disclosures
                val termsText = if (hasTrial) {
                    val price = recurringPriceText ?: if (selectedPlan == SubscriptionType.ANNUAL) "$49.99/year" else "$5.99/month"
                    "7 days free, then $price. Automatically renews unless canceled in Google Play Subscriptions."
                } else {
                    val price = recurringPriceText ?: if (selectedPlan == SubscriptionType.ANNUAL) "$49.99/year" else "$5.99/month"
                    "$price per ${if (selectedPlan == SubscriptionType.ANNUAL) "year" else "month"}. Automatically renews unless canceled in Google Play Subscriptions."
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DiscoveryViolet.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = termsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = AuraMidnight,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { onSubscribe(selectedPlan) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DiscoveryViolet),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    val buttonText = if (hasTrial) "Start 7-Day Free Trial" else "Subscribe to Aura Pro"
                    Text(buttonText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onBack) {
                    Text("Maybe Later", color = AuraMutedSlate, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    subtitle: String,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) DiscoveryViolet.copy(alpha = 0.08f) else AuraCrispWhite,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) DiscoveryViolet else AuraSubtleBorder
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AuraMutedSlate
                )
                if (badge != null) {
                    Surface(
                        shape = CircleShape,
                        color = DiscoveryViolet
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = price,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = AuraMidnight
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AuraMutedSlate
            )
        }
    }
}
