package org.nitish.project.sharedrop

import android.content.Context

private const val sharedPreferencesName = "sharedrop_prefs"
private const val localNameKey = "local_name"

actual fun provideDeviceNameStorage(): DeviceNameStorage {
    return AndroidDeviceNameStorage(AndroidContext.requireAppContext())
}

private class AndroidDeviceNameStorage(
    context: Context
) : DeviceNameStorage {
    private val sharedPreferences =
        context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)

    override suspend fun getLocalName(): String? = sharedPreferences.getString(localNameKey, null)

    override suspend fun setLocalName(name: String) {
        check(sharedPreferences.edit().putString(localNameKey, name).commit()) {
            "Failed to persist local device name"
        }
    }
}
