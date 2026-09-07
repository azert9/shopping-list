package fr.jloc.shoppinglist

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.jloc.shoppinglist.business.App
import fr.jloc.shoppinglist.business.AppRepository
import fr.jloc.shoppinglist.business.PadsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val SETTINGS_LAST_SELECTED_PAD = stringPreferencesKey("last_selected_pad")
private val SETTINGS_ACCEPTED_CONDITIONS_VERSION = intPreferencesKey("accepted_conditions_version")

class AndroidApp : Application() {

    private lateinit var db: AppRepository

    val app: CompletableDeferred<App> = CompletableDeferred()

    override fun onCreate() {
        super.onCreate()
        db = SqliteAppRepository.openGlobal(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            app.complete(initAppImpl())
        }
    }

    private suspend fun initAppImpl(): AppImpl {

        val prefs = preferencesDataStore.data.first()

        val conditionsAccepted =
            (prefs[SETTINGS_ACCEPTED_CONDITIONS_VERSION] ?: 0) >= 1

        val lastOpenedPadId = prefs[SETTINGS_LAST_SELECTED_PAD]

        @OptIn(DelicateCoroutinesApi::class) val pads = PadsManager.load(
            db = db,
            coroutineScope = GlobalScope,
        )

        return AppImpl(
            preferences = preferencesDataStore,
            conditionsAccepted = conditionsAccepted,
            lastOpenedPadId = lastOpenedPadId,
            pads = pads,
        )
    }
}

private class AppImpl(
    private val preferences: DataStore<Preferences>,
    @Volatile override var conditionsAccepted: Boolean,
    lastOpenedPadId: String?,
    override val pads: PadsManager,
) : App {

    private val lock = Any()

    override fun setConditionsAccepted() {
        if (conditionsAccepted) return
        synchronized(lock) {
            if (conditionsAccepted) return
            conditionsAccepted = true
        }
        CoroutineScope(Dispatchers.IO).launch {
            preferences.updateData {
                it.toMutablePreferences().also { preferences ->
                    preferences[SETTINGS_ACCEPTED_CONDITIONS_VERSION] = 1
                }
            }
        }
    }

    @Volatile
    private var _lastOpenedPadId: String? = lastOpenedPadId

    override val lastOpenedPadId: String?
        get() = _lastOpenedPadId

    override fun setLastOpenedPadId(id: String) {
        _lastOpenedPadId = id
        CoroutineScope(Dispatchers.IO).launch {
            preferences.updateData {
                it.toMutablePreferences().also { preferences ->
                    preferences[SETTINGS_LAST_SELECTED_PAD] = id
                }
            }
        }
    }
}
