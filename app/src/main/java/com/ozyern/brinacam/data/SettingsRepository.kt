package com.ozyern.brinacam.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ozyern.brinacam.camera.AspectSetting
import com.ozyern.brinacam.camera.FlashSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** User preferences that survive restarts. */
data class CameraSettings(
    val flash: FlashSetting = FlashSetting.OFF,
    val timerSeconds: Int = 0,
    val gridOn: Boolean = true,
    val aspect: AspectSetting = AspectSetting.R4_3,
    val hdrOn: Boolean = false,
    val ultraHdr: Boolean = true,
    val mirrorFront: Boolean = true,
    val shutterSound: Boolean = true,
    val burstOn: Boolean = false,
    val privateMetadata: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val flash = stringPreferencesKey("flash")
        val timer = intPreferencesKey("timer")
        val grid = booleanPreferencesKey("grid")
        val aspect = stringPreferencesKey("aspect")
        val hdr = booleanPreferencesKey("hdr")
        val ultraHdr = booleanPreferencesKey("ultra_hdr")
        val mirror = booleanPreferencesKey("mirror")
        val sound = booleanPreferencesKey("sound")
        val burst = booleanPreferencesKey("burst")
        val privateMetadata = booleanPreferencesKey("private_metadata")
    }

    val settings: Flow<CameraSettings> = context.dataStore.data.map { prefs ->
        val defaults = CameraSettings()
        CameraSettings(
            flash = enumOrDefault(prefs[Keys.flash], defaults.flash),
            timerSeconds = prefs[Keys.timer] ?: defaults.timerSeconds,
            gridOn = prefs[Keys.grid] ?: defaults.gridOn,
            aspect = enumOrDefault(prefs[Keys.aspect], defaults.aspect),
            hdrOn = prefs[Keys.hdr] ?: defaults.hdrOn,
            ultraHdr = prefs[Keys.ultraHdr] ?: defaults.ultraHdr,
            mirrorFront = prefs[Keys.mirror] ?: defaults.mirrorFront,
            shutterSound = prefs[Keys.sound] ?: defaults.shutterSound,
            burstOn = prefs[Keys.burst] ?: defaults.burstOn,
            privateMetadata = prefs[Keys.privateMetadata] ?: defaults.privateMetadata,
        )
    }

    suspend fun current(): CameraSettings = settings.first()

    suspend fun save(settings: CameraSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.flash] = settings.flash.name
            prefs[Keys.timer] = settings.timerSeconds
            prefs[Keys.grid] = settings.gridOn
            prefs[Keys.aspect] = settings.aspect.name
            prefs[Keys.hdr] = settings.hdrOn
            prefs[Keys.ultraHdr] = settings.ultraHdr
            prefs[Keys.mirror] = settings.mirrorFront
            prefs[Keys.sound] = settings.shutterSound
            prefs[Keys.burst] = settings.burstOn
            prefs[Keys.privateMetadata] = settings.privateMetadata
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
