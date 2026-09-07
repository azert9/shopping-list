package fr.jloc.shoppinglist.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {

    val drawerEnabled: Boolean
        get() = false

    fun navigate(backStack: MutableList<Route>) {
        backStack.add(this)
    }

    @Serializable
    data class Pad(
        val selectedPadId: String?,
    ) : Route {
        override val drawerEnabled = true

        override fun navigate(backStack: MutableList<Route>) {
            while (backStack.isNotEmpty()) {
                if (backStack.removeAt(backStack.size - 1) is Pad) {
                    break
                }
            }
            backStack.add(this)
        }
    }

    @Serializable
    data object About : Route

    @Serializable
    data class SharePad(
        val uri: String,
    ) : Route

    @Serializable
    data class AddRemotePad(
        val uri: String,
    ) : Route
}
