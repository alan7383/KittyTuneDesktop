package com.alananasss.kittytune.ui.musicimport

import com.alananasss.kittytune.core.AndroidViewModel
import com.alananasss.kittytune.core.Application
import com.alananasss.kittytune.data.musicimport.MusicApi
import com.alananasss.kittytune.data.musicimport.MusicApiAuth
import com.alananasss.kittytune.data.musicimport.MusicImportStorage

class MusicApiAuthViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = MusicImportStorage(application)

    /**
     * Stores the delivered [MusicApiAuth] in local storage and returns the
     * provider name to navigate to, or null if the auth is malformed.
     */
    fun persistAuth(auth: MusicApiAuth): String? {
        val provider = auth.integration?.type?.let(MusicApi::fromProviderName) ?: return null
        storage.saveAuth(provider.providerName, auth)
        return provider.providerName
    }
}
