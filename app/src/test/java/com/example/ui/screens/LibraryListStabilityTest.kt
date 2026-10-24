package com.example.ui.screens

import com.example.ui.models.LibraryItemUi
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryListStabilityTest {

    @Test
    fun testLibraryItemUi_SameSetCheck_IdentifiesEqualItemSets() {
        val uiItem1 = LibraryItemUi("m1", "Alpha", "PHOTO", "", "", "")
        val uiItem2 = LibraryItemUi("m2", "Beta", "PHOTO", "", "", "")
        val uiItem3 = LibraryItemUi("m3", "Gamma", "PHOTO", "", "", "")

        val listA = listOf(uiItem1, uiItem2, uiItem3)
        // Simulated reordered list after favorite boost on m2
        val listB = listOf(uiItem2, uiItem1, uiItem3)

        val idsA = listA.map { it.id }
        val idsB = listB.map { it.id }

        val isSameSet = idsA.size == idsB.size && idsA.toSet() == idsB.toSet()
        assertTrue("Re-ranked list with identical IDs is recognized as same set of items", isSameSet)
    }
}
