package com.chooloo.www.callmanager;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.widget.Toast;

import com.chooloo.www.callmanager.CallManager;
import com.chooloo.www.callmanager.Contact;
import com.chooloo.www.callmanager.activity.MainActivity;

import java.util.ArrayList;

import androidx.core.app.ActivityCompat;
import timber.log.Timber;

import static com.chooloo.www.callmanager.CallManager.getDisplayName;
import static com.chooloo.www.callmanager.CallManager.sCall;

public class ContactsManager {

    private ArrayList<Contact> mContacts = new ArrayList<Contact>();
    private ArrayList<Contact> mCurrentContacts = new ArrayList<Contact>();

    /**
     * Returns a list of all the contacts on the phone as a list of Contact objects
     *
     * @param context
     * @return ArrayList<Contact> a list of contacts
     */
    public ArrayList<Contact> getContactList(Context context) {
        ArrayList<Contact> contacts = new ArrayList<Contact>();
        ContentResolver cr = context.getContentResolver();
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null);

        if ((cur != null ? cur.getCount() : 0) > 0) {
            while (cur != null && cur.moveToNext()) {
                String id = cur.getString(
                        cur.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cur.getString(cur.getColumnIndex(
                        ContactsContract.Contacts.DISPLAY_NAME));

                if (cur.getInt(cur.getColumnIndex(
                        ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                    Cursor pCur = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id}, null);
                    while (pCur.moveToNext()) {
                        String phoneNo = pCur.getString(pCur.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        contacts.add(new Contact(name, phoneNo));
                        Timber.i("Name: " + name);
                        Timber.i("Phone Number: " + phoneNo);
                    }
                    pCur.close();
                }
            }
        }
        if (cur != null) {
            cur.close();
        }
        return contacts;
    }

    /**
     * Returns the list of all the contacts
     *
     * @return ArrayList<Contact>
     */
    public ArrayList<Contact> getContacts() {
        return mContacts;
    }

    /**
     * Resturns the list of all the contacts from the last lookup
     *
     * @return ArrayList<Contact>
     */
    public ArrayList<Contact> getCurrentContacts() {
        return mCurrentContacts;
    }

    /**
     * Returns a list of all the contacts on the phone that contain the given number
     *
     * @param context
     * @param num     the number by which to search for contacts
     * @return returns an ArrayList of all the matching contacts
     */
    public ArrayList<Contact> getContactsByNum(Context context, String num) {
        ArrayList<Contact> contacts = new ArrayList<Contact>();
        for (Contact contact : mContacts) {
            if (contact.getContactNumber().contains(num)) {
                Timber.i("Got a matching contact: " + contact.getContactName() + " number: " + contact.getContactNumber());
                contacts.add(contact);
            }
        }
        return contacts;
    }

    /**
     * Get the current contact's name from the end side of the current call
     *
     * @return the contact's name
     */
    public static String getCallerName(Context context, String phoneNumber) {
        //Check for permission to read contacts
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            //Don't prompt the user now, they are getting a call
            return null;
        }

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};
        String contactName;

        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
        if (cursor == null) return null;
        if (cursor.moveToFirst()) {
            contactName = cursor.getString(0);
        } else {
            return null;
        }
        cursor.close();

        return contactName;
    }

    /**
     * Creates an instant of AsyncContactLookup and executes it
     */
    public ArrayList<Contact> getContactByNumInBackground(Context context, boolean showProgress, String num) {
        AsyncContactLookup lookup = new AsyncContactLookup(context, showProgress, num);
        lookup.execute();
        return mCurrentContacts;
    }

    /**
     * Updates the sContacts list in background
     *
     * @param context
     * @param showProgress show loading screen or not
     */
    public void updateContactsInBackground(Context context, boolean showProgress) {
        AsyncContactsUpdater updater = new AsyncContactsUpdater(context, showProgress);
        updater.execute();
    }

    public class AsyncContactsUpdater extends AsyncTask<String, String, String> {

        private boolean mShowProgress;
        ProgressDialog mProgressDialog;
        String mStatus;
        Context mContext;

        public AsyncContactsUpdater(Context context, boolean showProgress) {
            this.mShowProgress = showProgress;
            this.mContext = context;
        }

        @Override
        protected String doInBackground(String... objects) {
            publishProgress("Getting Contacts...");
            try {
                mContacts = getContactList(mContext);
            } catch (Exception e) {
                Timber.e(e);
                mStatus = "Something went wrong, try again later";
            }
            return mStatus;
        }

        @Override
        protected void onPreExecute() {
            if (mShowProgress) {
                mProgressDialog = new ProgressDialog(mContext, ProgressDialog.THEME_DEVICE_DEFAULT_LIGHT);
                mProgressDialog.setTitle("Updating Contacts");
                mProgressDialog.setMessage("I bet you can't even count to 10");
                mProgressDialog.show();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            if (mShowProgress) mProgressDialog.dismiss();
            Toast.makeText(mContext, "Updated contacts successfuly", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onProgressUpdate(String... values) {
        }
    }

    public class AsyncContactLookup extends AsyncTask<String, String, String> {

        boolean mShowProgress;
        Context mContext;
        String mNumber;

        public AsyncContactLookup(Context context, boolean showProgress, String num) {
            this.mNumber = num;
            this.mContext = context;
            this.mShowProgress = showProgress;
        }

        @Override
        protected String doInBackground(String... strings) {
            String status = "0";
            publishProgress("Getting contacts...");
            try {
                mCurrentContacts = getContactsByNum(mContext, mNumber);
                status = "1";
            } catch (Exception e) {
                status = "0";
            }
            return status;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(String s) {
            if (s == "0") {
                Toast.makeText(mContext, "Something is wrong, couldn't get contacts", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
