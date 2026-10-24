package com.example.ui.screens

import androidx.lifecycle.ViewModel
import com.example.core.mediamodel.PresentationEligibleMedia
import com.example.data.mediastore.AdmissionProgress
import com.example.data.mediastore.PresentationMediaStore
import kotlinx.coroutines.flow.StateFlow

class LibraryViewModel(
    private val presentationMediaStore: PresentationMediaStore
) : ViewModel() {
    val eligibleMedia: StateFlow<List<PresentationEligibleMedia>> = presentationMediaStore.eligibleMedia
    val admissionProgress: StateFlow<AdmissionProgress> = presentationMediaStore.admissionProgress
}
