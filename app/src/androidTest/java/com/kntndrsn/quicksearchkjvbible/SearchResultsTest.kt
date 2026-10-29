package com.kntndrsn.quicksearchkjvbible

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class SearchResultsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun keyboardSearchShowsReferenceResultsUntilSelected() {
        search("John 3:16")

        compose.onNodeWithTag("search_results_page").assertIsDisplayed().assertHeightIsAtLeast(261.dp)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("search_reference_result").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("search_reference_result").assertIsDisplayed().performClick()

        compose.onNodeWithTag("search_results_page").assertDoesNotExist()
        compose.onNodeWithText("Chapter 3").assertIsDisplayed()
    }

    @Test
    fun keyboardSearchShowsBooksWithoutOpeningChapterPicker() {
        search("Genesis")

        compose.onNodeWithTag("search_results_page").assertIsDisplayed()
        compose.onNodeWithText("Choose chapter").assertDoesNotExist()
        compose.onNodeWithText("Tap to pick a chapter", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("search_results").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close search results").performClick()

        compose.onNodeWithTag("search_results_page").assertDoesNotExist()
    }

    @Test
    fun keyboardSearchWithNoMatchesStillShowsResultsPage() {
        search("zzzzzzzzzzzzzzzz")

        compose.onNodeWithTag("search_results_page").assertIsDisplayed()
        compose.onNodeWithText("No results found.").assertIsDisplayed()
    }

    private fun search(query: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("search_input").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("search_input").performClick().performTextInput(query)
        compose.onNodeWithTag("search_input").performImeAction()
    }
}
