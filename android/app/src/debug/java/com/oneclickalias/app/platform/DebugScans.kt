package com.oneclickalias.app.platform

import kotlinx.coroutines.flow.MutableSharedFlow

/** Debug builds only: texts from [DebugScanReceiver]; dropped while no scanner is shown. */
object DebugScans {
    val texts = MutableSharedFlow<String>(extraBufferCapacity = 1)
}
