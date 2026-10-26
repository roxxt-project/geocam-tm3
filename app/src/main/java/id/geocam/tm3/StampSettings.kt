package id.geocam.tm3

import android.content.Context

/** User choices that change what is printed on the photo. */
data class StampSettings(
    val note: String = "",
    val decimalComma: Boolean = true,
    val showAddress: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("geocam", Context.MODE_PRIVATE)

    fun load() = StampSettings(
        note = prefs.getString(KEY_NOTE, "").orEmpty(),
        decimalComma = prefs.getBoolean(KEY_COMMA, true),
        showAddress = prefs.getBoolean(KEY_ADDRESS, true),
    )

    fun save(settings: StampSettings) {
        prefs.edit()
            .putString(KEY_NOTE, settings.note)
            .putBoolean(KEY_COMMA, settings.decimalComma)
            .putBoolean(KEY_ADDRESS, settings.showAddress)
            .apply()
    }

    private companion object {
        const val KEY_NOTE = "note"
        const val KEY_COMMA = "decimal_comma"
        const val KEY_ADDRESS = "show_address"
    }
}
