package com.isaacbegue.afinador.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Define a delegate to create the DataStore instance for the whole application
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tuner_settings")

/**
 * Repository class to handle reading and writing user preferences using DataStore.
 */
class PreferencesRepository(private val context: Context) {

    // Define the key for storing the default tuning name preference
    private val defaultTuningKey = stringPreferencesKey("default_tuning_name")

    /**
     * Flow that emits the currently saved default tuning name.
     * It emits the name of the tuning or potentially null if none is set.
     */
    val defaultTuningNameFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[defaultTuningKey] // Read the value associated with the key
        }

    /**
     * Saves the selected default tuning name to DataStore preferences.
     * @param tuningName The name of the InstrumentTuning to save as default.
     */
    suspend fun saveDefaultTuningName(tuningName: String) {
        context.dataStore.edit { settings ->
            settings[defaultTuningKey] = tuningName // Write the value to the key
        }
    }
}