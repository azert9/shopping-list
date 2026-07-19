package fr.jloc.shoppinglist

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.jloc.shoppinglist.business.AppRepository
import fr.jloc.shoppinglist.business.PadsManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val SETTINGS_LAST_SELECTED_PAD = stringPreferencesKey("last_selected_pad")
private val SETTINGS_ACCEPTED_CONDITIONS_VERSION = intPreferencesKey("accepted_conditions_version")

class ShoppingListApp : Application() {

    lateinit var db: AppRepository

    override fun onCreate() {
        super.onCreate()
        db = SqliteAppRepository.openGlobal(applicationContext)
    }

    @Volatile
    private var pads: PadsManager? = null
    private val padsSingletonMutex = Mutex()

    /** Return the global instance of [PadsManager], after instantiating it if required. */
    suspend fun padsManager(): PadsManager = pads ?: padsSingletonMutex.withLock {
        pads ?: withContext(NonCancellable) {
            @OptIn(DelicateCoroutinesApi::class)
            pads = PadsManager.load(
                db = db,
                coroutineScope = GlobalScope,
            )
            pads!!
        }
    }

    suspend fun getLastSelectedPad(): String? = dataStore.data.first()[SETTINGS_LAST_SELECTED_PAD]

    suspend fun setLastSelectedPad(id: String?) {
        dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                if (id != null) {
                    preferences[SETTINGS_LAST_SELECTED_PAD] = id
                } else {
                    preferences.remove(SETTINGS_LAST_SELECTED_PAD)
                }
            }
        }
    }

    suspend fun getConditionsAccepted(): Boolean =
        (dataStore.data.first()[SETTINGS_ACCEPTED_CONDITIONS_VERSION] ?: 0) >= 1

    suspend fun setConditionsAccepted() {
        dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[SETTINGS_ACCEPTED_CONDITIONS_VERSION] = 1
            }
        }
    }
}
