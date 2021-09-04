package com.chooloo.www.koler.interactor.contacts

import android.Manifest.permission.WRITE_CONTACTS
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import android.telephony.PhoneNumberUtils
import androidx.annotation.RequiresPermission
import com.chooloo.www.koler.contentresolver.ContactsContentResolver
import com.chooloo.www.koler.data.account.Contact
import com.chooloo.www.koler.interactor.base.BaseInteractorImpl
import com.chooloo.www.koler.interactor.numbers.NumbersInteractor
import com.chooloo.www.koler.interactor.phoneaccounts.PhoneAccountsInteractor
import com.chooloo.www.koler.util.annotation.RequiresDefaultDialer

class ContactsInteractorImpl(
    private val context: Context,
    private val numbersInteractor: NumbersInteractor,
    private val phoneAccountsInteractor: PhoneAccountsInteractor,
) : BaseInteractorImpl<ContactsInteractor.Listener>(), ContactsInteractor {
    override fun getContact(contactId: Long, callback: (Contact?) -> Unit) {
        ContactsContentResolver(context, contactId).queryContent { contacts ->
            contacts?.let { callback.invoke(contacts.getOrNull(0)) } ?: callback.invoke(null)
        }
    }


    @RequiresPermission(WRITE_CONTACTS)
    override fun deleteContact(contactId: Long) {
        context.contentResolver.delete(
            Uri.withAppendedPath(
                Contacts.CONTENT_URI,
                contactId.toString()
            ), null, null
        )
    }

    @RequiresDefaultDialer
    override fun blockContact(contactId: Long, onSuccess: (() -> Unit)?) {
        phoneAccountsInteractor.getContactAccounts(contactId) { accounts ->
            accounts?.forEach { numbersInteractor.blockNumber(it.number) }
            onSuccess?.invoke()
        }
    }

    override fun unblockContact(contactId: Long, onSuccess: (() -> Unit)?) {
        phoneAccountsInteractor.getContactAccounts(contactId) { accounts ->
            accounts?.forEach { numbersInteractor.unblockNumber(it.number) }
            onSuccess?.invoke()
        }
    }

    @RequiresPermission(WRITE_CONTACTS)
    override fun toggleContactFavorite(contactId: Long, isFavorite: Boolean) {
        val contentValues = ContentValues()
        contentValues.put(Contacts.STARRED, if (isFavorite) 1 else 0)
        val filter = "${Contacts._ID}=$contactId"
        context.contentResolver.update(Contacts.CONTENT_URI, contentValues, filter, null);
    }

    override fun getIsContactBlocked(contactId: Long, callback: (Boolean) -> Unit) {
        phoneAccountsInteractor.getContactAccounts(contactId) { accounts ->
            callback.invoke(accounts?.all { numbersInteractor.isNumberBlocked(it.number) } ?: false)
        }
    }


    override fun openSmsView(number: String?) {
        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse(String.format("smsto:%s", PhoneNumberUtils.normalizeNumber(number)))
        )
        if (context !is Activity) {
            intent.flags = FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    override fun openContactView(contactId: Long) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.withAppendedPath(Contacts.CONTENT_URI, contactId.toString())
            if (context !is Activity) flags = FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    override fun openAddContactView(number: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
            if (context !is Activity) flags = FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    override fun openEditContactView(contactId: Long) {
        val intent = Intent(Intent.ACTION_EDIT, Contacts.CONTENT_URI).apply {
            data = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
            if (context !is Activity) flags = FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}