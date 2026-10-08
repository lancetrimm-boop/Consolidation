# Aura Media Eligibility Boundary Architecture

## Executive Summary
The Aura Media Eligibility Boundary enforces a strict single-direction media classification, admission, and consumption pipeline. Raw and unverified media files can **never** reach presentation UI surfaces, and playback is strictly gated at T-0 (time of playback) using cryptographically verified consumption tokens.

---

## 1. Domain Model Hierarchy

```
[MediaProvider / MediaStore]
       │
       ▼
  RawMediaItem  (Unvalidated raw filesystem metadata)
       │
       ▼ (MediaEligibilityEvaluator analysis)
ClassifiedMediaItem (Structured analysis: corrupt/codec/permission)
       │
       ├──► EligibilityResult.Ineligible ──► DiagnosticMediaStore
       │
       └──► EligibilityResult.Eligible
               │
               ▼ (Admitted via Gate 1)
       PresentationEligibleMedia (Immutable, snapshotHash + gateVersion)
               │
               ▼ (Gate 2 verification at T-0 playback)
       PlaybackConsumptionToken (Grants playback authorization)
```

1. **`RawMediaItem`**: Pure data representation from OS MediaStore or file system. Contains raw file path, size, date modified, and basic MediaStore IDs.
2. **`ClassifiedMediaItem`**: Output of deep classification and format inspection. Contains fields indicating codec support, corruption status, permission state, file existence, and test status.
3. **`PresentationEligibleMedia`**: Admitted domain object stored in `PresentationMediaStore`. Immutable record containing `id`, `uri`, `title`, `durationMs`, `mimeType`, `width`, `height`, `dateAddedEpochMs`, `snapshotHash`, and `gateVersion`.
4. **`PlaybackConsumptionToken`**: Cryptographically bound authorization token issued by Gate 2 (`MediaConsumptionGate`) at T-0 prior to ExoPlayer/media engine initialization.

---

## 2. Two-Gate Architecture

### Gate 1: Admission Gate (`MediaAdmissionGate`)
- **Location**: `com.example.core.mediagate.MediaAdmissionGate`
- **Responsibility**: Processes `ClassifiedMediaItem` objects as they are scanned or updated.
- **Contract**:
  - Evaluates items using `MediaEligibilityEvaluator`.
  - On `Eligible`: computes `snapshotHash`, instantiates `PresentationEligibleMedia`, admits it into `PresentationMediaStore`, and clears any diagnostic entries for the ID.
  - On `Ineligible`: revokes the ID from `PresentationMediaStore` and records `IneligibleMediaEntry` in `DiagnosticMediaStore`.

### Gate 2: Consumption Gate (`MediaConsumptionGate`)
- **Location**: `com.example.core.mediagate.MediaConsumptionGate`
- **Responsibility**: Validates candidate media at T-0 (the exact millisecond playback is requested).
- **Checks Performed**:
  1. **Atomic Store Check**: Verifies item exists in `PresentationMediaStore` with matching `snapshotHash` and `gateVersion`.
  2. **File System Existence**: Verifies file accessibility on disk using `FileSystemValidator` (handles unmounted SD cards, deleted files, TOCTOU race conditions).
- **Outcome**:
  - `Allowed(PlaybackConsumptionToken)`: Playback proceeds safely.
  - `Rejected(IneligibilityReason)`: Playback is rejected. The candidate is removed, metrics are recorded, and queue replenishment silently advances to the next candidate.

---

## 3. MediaStores & UI Isolation

- **`PresentationMediaStore`**: Serves **only** presentation UI surfaces (Library, Channels, Collections, MeTV). Exposes `eligibleMedia` (`StateFlow<List<PresentationEligibleMedia>>`) and `admissionProgress`.
- **`DiagnosticMediaStore`**: Serves **only** Cleanup and Diagnostics surfaces (e.g. `CleanupReviewViewModel`). Exposes `ineligibleItems` (`StateFlow<List<IneligibleMediaEntry>>`) with detailed `IneligibilityReason` sets (`MISSING`, `CORRUPT`, `UNSUPPORTED`, `INACCESSIBLE`, `INCOMPLETE_SCAN`, `UNTESTED`).

---

## 4. MeTV & Playback Engine Integration

- **Pure Consumer**: MeTV receives candidates via `PresentationMediaStore.eligibleMedia`.
- **Candidate Metadata**: `MeTVCandidate` holds `mediaId`, `mediaItem`, `gateVersion`, and `snapshotHash`.
- **TOCTOU Protection**: If a file is deleted from disk after being enqueued:
  - Gate 2 detects missing/corrupt file at T-0.
  - `MeTVPlaybackController` records `gate2_rejected` metric, removes candidate, replenishes queue, and silently skips to next valid item. Zero error dialogs presented to the user.

---

## 5. Upgrade Audit Worker

- **Class**: `com.example.data.worker.MediaEligibilityAuditWorker`
- **Behavior**: WorkManager `CoroutineWorker` executed once on post-upgrade cold start (tracked via `SharedPreferences` flag `has_completed_upgrade_audit`).
- **Action**: Reads all persisted candidates, evaluates each with `MediaEligibilityEvaluator`, updates metadata for valid items, and purges stale/ineligible candidates with audit log `[AUDIT_PURGE] id=… reasons=…`.

---

## 6. Observability & Testing

- **`EligibilityMetricsTracker`**: Tracks `gate1_admitted`, `gate1_rejected`, `gate2_rejected`, and `batch_revoke_count`.
- **Integration Test Coverage**:
  - `Phase1DomainModelTest.kt`: Domain model immutability & validation.
  - `MediaAdmissionGateTest.kt`: Gate 1 admission & batch revocation.
  - `Phase3MediaConsumptionGateTest.kt`: Gate 2 playback route resolution & TOCTOU handling.
  - `Phase4ConsumerMigrationTest.kt`: PresentationMediaStore isolation across Library & Channels.
  - `Phase5MeTVGate2IntegrationTest.kt`: MeTV candidate gating & silent skip logic.
  - `Phase6BoundaryCompletionTest.kt`: Cleanup ViewModel diagnostic isolation & Audit Worker purging.
