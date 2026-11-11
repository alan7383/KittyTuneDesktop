package com.alananasss.kittytune.data.drm

internal class PlaybackDrmSession {
    private var current: OwnedSession? = null
    private var encryption: HlsEncryptionInfo? = null
    private var token: String? = null

    @Synchronized
    fun session(encryption: HlsEncryptionInfo, token: String): WidevineLicenseSession {
        current?.let { if (this.encryption == encryption && this.token == token && !it.expired) return it }
        current?.close()
        current = null
        val source = WidevineCdmRegistry.require()
        val lease = PlaybackCdms.acquire(source)
        try {
            val session = OwnedSession(SoundCloudDrm.openPlaybackSession(lease.cdm, encryption, token), source, lease)
            if (session.expired) {
                session.close()
                throw DrmUnavailableException("the CDM changed while the stream was being licensed")
            }
            current = session
            this.encryption = encryption
            this.token = token
            return session
        } catch (e: Throwable) {
            lease.close()
            throw e
        }
    }

    @Synchronized
    fun release(session: WidevineLicenseSession) {
        if (current !== session) return
        current = null
        session.close()
    }
}

private object PlaybackCdms {
    private val claimed = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<WidevineCdm, Boolean>())

    fun acquire(source: WidevineCdm): PlaybackCdmLease {
        if (synchronized(claimed) { claimed.add(source) }) {
            return PlaybackCdmLease(source) { synchronized(claimed) { claimed.remove(source) } }
        }
        val extra = source.fork()
            ?: throw DrmUnavailableException("this CDM cannot open a second playback instance")
        check(extra !== source) { "a second playback instance must have its own session" }
        return PlaybackCdmLease(extra) { extra.close() }
    }
}

private class PlaybackCdmLease(val cdm: WidevineCdm, private val release: () -> Unit) : AutoCloseable {
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}

private class OwnedSession(
    private val session: WidevineLicenseSession,
    private val source: WidevineCdm,
    private val lease: PlaybackCdmLease,
) : WidevineLicenseSession, KeyIdScopedSession, ExpiringSession {
    @Volatile private var closed = false
    override val expired: Boolean
        get() = closed || source !== WidevineCdmRegistry.current() || session.isExpired

    private fun requireActive() {
        if (expired) throw DrmLicenseException("the playback session expired")
    }

    @Synchronized override fun licenseRequest(): ByteArray {
        requireActive()
        return session.licenseRequest()
    }

    @Synchronized override fun provideLicense(license: ByteArray): List<ContentKey> {
        requireActive()
        return session.provideLicense(license)
    }

    @Synchronized override fun useKeyId(kid: ByteArray) {
        requireActive()
        (session as? KeyIdScopedSession)?.useKeyId(kid)
    }

    @Synchronized override fun decryptSample(data: ByteArray, iv: ByteArray, subsamples: List<Pair<Int, Int>>): ByteArray {
        requireActive()
        return session.decryptSample(data, iv, subsamples)
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        try { session.close() } finally { lease.close() }
    }
}
