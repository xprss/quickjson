package io.github.xprss.quickjson

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class QuickJsonUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun createsObjectIntoBuilderAndSwitchesEditors() {
        compose.onNodeWithText("New", substring = true).performClick()
        compose.onNodeWithTag("new-object-menu-item").performClick()
        compose.onNodeWithText("Add property").assertIsDisplayed()
        compose.onNodeWithText("Code").performClick()
        compose.onNodeWithText("Code").assertIsDisplayed()
    }
}
