package com.alananasss.kittytune.core

/**
 * Desktop no-op shims for android.app.Application and androidx.lifecycle.AndroidViewModel,
 * so the KittyTune ViewModels port with minimal edits.
 *
 * On desktop there is no Application/Context: our data layer (PlayerPreferences, Config,
 * RetrofitClient, NetworkUtils, Strings) is all context-free. `getApplication()` returns a
 * dummy, and `getString` resolves through core.Strings.
 */
class Application

/**
 * Mirrors androidx.lifecycle.AndroidViewModel(application) but backed by a plain ViewModel.
 * ViewModels change only their base class import + the getString helpers (which now call str()).
 */
abstract class AndroidViewModel(private val application: Application) : androidx.lifecycle.ViewModel() {
    @Suppress("UNCHECKED_CAST")
    fun <T : Application> getApplication(): T = application as T
}

/** Single shared Application instance for ViewModels constructed without a factory. */
object AppInstance {
    val application = Application()
}
