package fr.jloc.shoppinglist

import android.content.Context
import fr.jloc.shoppinglist.business.App
import fr.jloc.shoppinglist.business.PadsManager
import kotlinx.coroutines.CoroutineScope

internal class FakeApp private constructor(
    override val pads: PadsManager,
) : App {
    override var conditionsAccepted: Boolean = true
        private set

    override fun setConditionsAccepted() {
        conditionsAccepted = true
    }

    override var lastOpenedPadId: String? = null
        private set

    override fun setLastOpenedPadId(id: String) {
        lastOpenedPadId = id
    }

    companion object {
        suspend fun create(context: Context, coroutineScope: CoroutineScope): FakeApp {
            val db = SqliteAppRepository.openInMemory(context)
            val pads = PadsManager.load(db, coroutineScope)
            return FakeApp(pads)
        }
    }
}
