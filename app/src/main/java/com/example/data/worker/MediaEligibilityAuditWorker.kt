package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.mediagate.ClassifiedItemProvider
import com.example.core.mediagate.DefaultSnapshotHasher
import com.example.core.mediagate.MediaEligibilityEvaluator
import com.example.core.mediagate.SnapshotHasher
import com.example.core.mediamodel.EligibilityResult
import com.example.data.metv.MeTVCandidate
import com.example.data.metv.MeTVPlaybackController

class MediaEligibilityAuditWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val itemProvider: ClassifiedItemProvider? = null,
    private val playbackController: MeTVPlaybackController? = null,
    private val evaluator: MediaEligibilityEvaluator = MediaEligibilityEvaluator,
    private val hasher: SnapshotHasher = DefaultSnapshotHasher()
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val PREF_NAME = "aura_eligibility_audit_prefs"
        const val KEY_AUDIT_COMPLETED = "has_completed_upgrade_audit"
        const val TAG_AUDIT = "MediaEligibilityAudit"

        fun isAuditCompleted(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUDIT_COMPLETED, false)
        }

        fun markAuditCompleted(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_AUDIT_COMPLETED, true).apply()
        }
    }

    override suspend fun doWork(): Result {
        if (isAuditCompleted(applicationContext)) {
            return Result.success()
        }

        try {
            val candidates = playbackController?.currentQueue ?: emptyList()
            auditCandidates(candidates)
            markAuditCompleted(applicationContext)
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG_AUDIT, "Error during media eligibility audit", e)
            return Result.failure()
        }
    }

    suspend fun auditCandidates(candidates: List<MeTVCandidate>): List<MeTVCandidate> {
        val updatedList = mutableListOf<MeTVCandidate>()
        for (candidate in candidates) {
            val classified = itemProvider?.getClassifiedItem(candidate.mediaId)
            if (classified == null) {
                Log.i(TAG_AUDIT, "[AUDIT_PURGE] id=${candidate.mediaId} reasons=[MISSING]")
                playbackController?.removeCandidate(candidate.mediaId)
                continue
            }

            when (val evaluation = evaluator.evaluate(classified)) {
                is EligibilityResult.Eligible -> {
                    val freshHash = hasher.compute(classified)
                    val updated = candidate.copy(
                        gateVersion = MediaEligibilityEvaluator.CURRENT_CONTRACT_VERSION,
                        snapshotHash = freshHash
                    )
                    updatedList.add(updated)
                }
                is EligibilityResult.Ineligible -> {
                    Log.i(TAG_AUDIT, "[AUDIT_PURGE] id=${candidate.mediaId} reasons=${evaluation.reasons}")
                    playbackController?.removeCandidate(candidate.mediaId)
                }
            }
        }
        return updatedList
    }
}
