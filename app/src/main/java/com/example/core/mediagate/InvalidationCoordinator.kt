package com.example.core.mediagate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.example.core.mediamodel.ClassifiedMediaItem
import com.example.core.mediamodel.IneligibilityReason
import com.example.data.mediastore.PresentationMediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InvalidationCoordinator(
    private val context: Context,
    private val presentationStore: PresentationMediaStore,
    private val admissionGate: MediaAdmissionGate,
    private val scope: CoroutineScope
) {
    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val data = intent?.data
                scope.launch(Dispatchers.IO) {
                    handleStorageRemoval(data)
                }
            }
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            // Fallback or ignore if unregister/register throws in unit tests without context
        }
    }

    fun stop() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
            receiver = null
        }
    }

    fun onAnalysisCompleted(classifiedItem: ClassifiedMediaItem) {
        scope.launch(Dispatchers.IO) {
            admissionGate.processClassifiedItem(classifiedItem)
        }
    }

    fun onMediaStoreChanged(modifiedIds: Set<String>, deletedIds: Set<String>) {
        scope.launch(Dispatchers.IO) {
            if (deletedIds.isNotEmpty()) {
                presentationStore.batchRevoke(deletedIds, setOf(IneligibilityReason.MISSING))
            }
            for (id in modifiedIds) {
                admissionGate.reEvaluate(id)
            }
        }
    }

    private suspend fun handleStorageRemoval(uri: Uri?) {
        val path = uri?.path ?: return
        val currentEligible = presentationStore.eligibleMedia.value
        val affectedIds = currentEligible.filter { item ->
            val itemPath = Uri.parse(item.uri).path
            itemPath != null && itemPath.startsWith(path)
        }.map { it.id }.toSet()

        if (affectedIds.isNotEmpty()) {
            presentationStore.batchRevoke(affectedIds, setOf(IneligibilityReason.INACCESSIBLE))
        }
    }
}
