package fr.jloc.shoppinglist

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import fr.jloc.shoppinglist.ui.activities.main.Screen
import org.junit.Test
import org.junit.Rule

class UiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun listManagementTest() {

        // helpers

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        fun onNodeWithText(resId: Int): SemanticsNodeInteraction {
            return composeTestRule.onNodeWithText(ctx.getString(resId))
        }

        fun onNodeWithContentDescription(resId: Int): SemanticsNodeInteraction {
            return composeTestRule.onNodeWithContentDescription(ctx.getString(resId))
        }

        fun click(resId: Int) {
            onNodeWithText(resId).performClick()
        }

        fun clickImg(resId: Int) {
            onNodeWithContentDescription(resId).performClick()
        }

        fun fill(resId: Int, text: String, clear: Boolean = false) {
            val node = onNodeWithText(resId)
            if (clear) {
                node.performTextClearance()
            }
            node.performTextInput(text)
        }

        fun assertMandatoryShoppingListCreation() {
            composeTestRule.onNode(
                hasText(ctx.getString(R.string.create_pad_dialog_title)) and hasTestTag("dialog_title")
            ).assertIsDisplayed()
            // TODO: also try to dismiss using back nav or clicking outside
            onNodeWithText(R.string.dialog_cancel).assertIsNotDisplayed()
        }

        //

        composeTestRule.setContent {
            Screen()
        }

        // when opening the app, we get a dialog for creating the first shopping list

        assertMandatoryShoppingListCreation()
        onNodeWithText(R.string.pad_name_input_label).performTextInput("Shopping List 1")
        click(R.string.dialog_submit_create)
        onNodeWithText(R.string.empty_pad_placeholder).assertIsDisplayed()

        // the name of the list should be displayed in the app bar and in the drawer

        composeTestRule.onAllNodesWithText("Shopping List 1").assertCountEquals(2)

        // creating another list

        clickImg(R.string.menu)
        clickImg(R.string.action_create_pad)
        onNodeWithText(R.string.pad_name_input_label).performTextInput("Shopping List 2")
        onNodeWithText(R.string.dialog_cancel).assertIsDisplayed()
        click(R.string.dialog_submit_create)

        composeTestRule.onAllNodesWithText("Shopping List 1").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Shopping List 2").assertCountEquals(1)

        composeTestRule.onNodeWithTag("drawer").performTouchInput {
            swipeLeft()
        }

        // creating an element in the first list

        clickImg(R.string.action_add_pad_item)

        onNodeWithText(R.string.pad_item_name_input_label).performTextInput("Thing")

        click(R.string.dialog_submit_add)

        composeTestRule.onNodeWithText("Thing").assertIsDisplayed()

        // selecting the second list

        clickImg(R.string.menu)

        composeTestRule.onNodeWithText("Shopping List 2").performClick()

        composeTestRule.onNodeWithTag("drawer").performTouchInput {
            swipeLeft()
        }

        composeTestRule.onNodeWithText("Thing").assertIsNotDisplayed()

        // selecting the first list

        clickImg(R.string.menu)

        composeTestRule.onNodeWithText("Shopping List 1").performClick()

        composeTestRule.onNodeWithTag("drawer").performTouchInput {
            swipeLeft()
        }

        composeTestRule.onNodeWithText("Thing").assertIsDisplayed()

        // renaming the list

        clickImg(R.string.pad_options)
        click(R.string.action_rename_pad)
        fill(R.string.pad_name_input_label, "Shopping List 1.2", clear = true)
        click(R.string.dialog_submit_apply)

        composeTestRule.onNodeWithText("Thing").assertIsDisplayed()

        composeTestRule.onNodeWithText("Shopping List 1").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("Shopping List 1.2").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Shopping List 2").assertCountEquals(1)

        // deleting the list

        clickImg(R.string.pad_options)
        click(R.string.action_delete_pad)
        click(R.string.dialog_submit_delete)

        composeTestRule.onNodeWithText("Thing").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("Shopping List 1").assertDoesNotExist()
        composeTestRule.onNodeWithText("Shopping List 1.2").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("Shopping List 2").assertCountEquals(2)

        // deleting the last list

        clickImg(R.string.pad_options)
        click(R.string.action_delete_pad)
        click(R.string.dialog_submit_delete)

        assertMandatoryShoppingListCreation()
    }

    // TODO: test the shopping list itself
}
