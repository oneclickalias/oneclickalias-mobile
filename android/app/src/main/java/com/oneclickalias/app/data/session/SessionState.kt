package com.oneclickalias.app.data.session

/** What the UI needs to know about the session; never carries tokens. */
sealed interface SessionState {
    /** The stored session is still being read at app start. */
    data object Loading : SessionState

    data object SignedIn : SessionState

    /** @property notice why the user just landed signed out, if worth telling. */
    data class SignedOut(val notice: SignOutNotice? = null) : SessionState
}

enum class SignOutNotice {
    /** The server ended the session (expired, signed out elsewhere, token reuse). */
    SessionEnded,

    /** Logout could not reach the server; only this device's data was deleted. */
    SignedOutLocally,

    /** The user signed out every device of the account, this one included. */
    AllDevicesSignedOut,

    /** The user asked to delete the account; a login within the retention period cancels it. */
    AccountDeletionRequested
}
