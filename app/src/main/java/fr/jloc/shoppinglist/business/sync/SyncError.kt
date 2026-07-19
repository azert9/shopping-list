package fr.jloc.shoppinglist.business.sync

import java.lang.Exception

class SyncError : Exception {

    val code: Code

    enum class Code {
        SERVER_ERROR, NETWORK_ERROR, APP_NEEDS_UPDATE, UNEXPECTED_ERROR,
    }

    constructor(code: Code, details: String) : super(makeErrorMessage(code, details)) {
        this.code = code
    }

    constructor(code: Code, details: String, cause: Throwable) : super(
        makeErrorMessage(
            code, details
        ), cause
    ) {
        this.code = code
    }

    companion object {

        fun newServerError(details: String, cause: Throwable? = null): SyncError =
            if (cause == null) {
                SyncError(Code.SERVER_ERROR, details)
            } else {
                SyncError(Code.SERVER_ERROR, details, cause)
            }

        fun newNetworkError(details: String, cause: Throwable? = null): SyncError =
            if (cause == null) {
                SyncError(Code.NETWORK_ERROR, details)
            } else {
                SyncError(Code.NETWORK_ERROR, details, cause)
            }

        fun newAppNeedsUpdate(details: String, cause: Throwable? = null): SyncError =
            if (cause == null) {
                SyncError(Code.APP_NEEDS_UPDATE, details)
            } else {
                SyncError(Code.APP_NEEDS_UPDATE, details, cause)
            }

        fun newUnexpectedError(details: String, cause: Throwable? = null): SyncError =
            if (cause == null) {
                SyncError(Code.UNEXPECTED_ERROR, details)
            } else {
                SyncError(Code.UNEXPECTED_ERROR, details, cause)
            }
    }

}

private fun makeErrorMessage(code: SyncError.Code, details: String): String = when (code) {
    SyncError.Code.SERVER_ERROR -> "Sync failed: Server error: $details"
    SyncError.Code.NETWORK_ERROR -> "Sync failed: Network error: $details"
    SyncError.Code.APP_NEEDS_UPDATE -> "Sync failed: App needs update: $details"
    SyncError.Code.UNEXPECTED_ERROR -> "Sync failed: Unexpected error: $details"
}
