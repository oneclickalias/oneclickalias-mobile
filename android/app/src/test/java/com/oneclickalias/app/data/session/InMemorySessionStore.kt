package com.oneclickalias.app.data.session

import java.io.IOException

/** [SessionStore] for tests: keeps the session in a field and counts writes. */
class InMemorySessionStore(var stored: Session? = null) : SessionStore {
    var writes = 0

    /** Makes [write] fail like a full disk or a broken Keystore key. */
    var failWrites = false

    override suspend fun read(): Session? = stored

    override suspend fun write(session: Session) {
        if (failWrites) throw IOException("disk full")
        stored = session
        writes++
    }

    override suspend fun clear() {
        stored = null
    }
}
