package com.snapkode.nivolauncher.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactResult(val lookupKey: String, val contactId: Long, val name: String) {
    val uri: Uri get() = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
}

object ContactsSearch {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun search(context: Context, query: String, limit: Int = 5): List<ContactResult> {
        if (query.isBlank() || !hasPermission(context)) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC")
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext() && size < limit) {
                            add(ContactResult(cursor.getString(1).orEmpty(), cursor.getLong(0), cursor.getString(2).orEmpty()))
                        }
                    }
                }.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun open(context: Context, contact: ContactResult) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, contact.uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
