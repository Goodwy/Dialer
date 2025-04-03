package com.goodwy.dialer.helpers

import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri

/**
 * Utility methods for dealing with URIs.
 */
object UriUtils {
    private const val LOOKUP_URI_ENCODED = "encoded"

    /**
     * Checks whether two URI are equal, taking care of the case where either is null.
     */
    fun areEqual(uri1: Uri?, uri2: Uri?): Boolean {
        if (uri1 == null && uri2 == null) {
            return true
        }
        if (uri1 == null || uri2 == null) {
            return false
        }
        return uri1 == uri2
    }

    /**
     * Parses a string into a URI and returns null if the given string is null.
     */
    fun parseUriOrNull(uriString: String?): Uri? {
        if (uriString == null) {
            return null
        }
        return uriString.toUri()
    }

    /**
     * Converts a URI into a string, returns null if the given URI is null.
     */
    fun uriToString(uri: Uri?): String? {
        return uri?.toString()
    }

    /**
     * @return `uri` as-is if the authority is of contacts provider. Otherwise or `uri` is
     * null, return null otherwise
     */
    fun nullForNonContactsUri(uri: Uri?): Uri? {
        if (uri == null) {
            return null
        }
        return if (ContactsContract.AUTHORITY == uri.authority) uri else null
    }
}
