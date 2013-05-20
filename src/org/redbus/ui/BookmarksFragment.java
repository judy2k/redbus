package org.redbus.ui;


import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;
import org.redbus.R;
import org.redbus.settings.SettingsHelper;

public class BookmarksFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String bookmarksXmlFile = "/sdcard/redbus-stops.xml";

    private static final int TRAFFIC_CHECK_INTERVAL = 15 * 60;

    private BusyDialog busyDialog = null;
    private int stopDbExpectedRequestId = -1;

    private boolean isManualUpdateCheck = false;
    private SettingsHelper listDb;

    private static final String[] columnNames = new String[] { SettingsHelper.ID, SettingsHelper.BOOKMARKS_COL_STOPNAME};
    private static final int[] listViewIds = new int[] { R.id.stopbookmarks_stopcode, R.id.stopbookmarks_name};

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        //setTitle("Bookmarks");

        busyDialog = new BusyDialog(getActivity());
        registerForContextMenu(getListView());

        SimpleCursorAdapter sca = new SimpleCursorAdapter(getActivity(), R.layout.stopbookmarks_item, null,
                columnNames, listViewIds);

        SettingsHelper.triggerInitialGoogleBackup(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.stopbookmarks, null);

        return view;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        doSetupStuff();

        SettingsHelper db = new SettingsHelper(this);
        Cursor listContentsCursor = db.getBookmarks();
        // LoaderManager with a CursorLoader.

        //SettingsHelper tmp = Common.updateBookmarksListAdaptor(this);
        if (tmp != null)
            listDb = tmp;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Common.destroyBookmarksListAdaptor(this, listDb);

        if (busyDialog != null)
            busyDialog.dismiss();
        busyDialog = null;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        Uri baseUri;
        if (mCurFilter != null) {
            baseUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI,
                    Uri.encode(mCurFilter));
        } else {
            baseUri = Contacts.CONTENT_URI;
        }

        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        String select = "((" + Contacts.DISPLAY_NAME + " NOTNULL) AND ("
                + Contacts.HAS_PHONE_NUMBER + "=1) AND ("
                + Contacts.DISPLAY_NAME + " != '' ))";
        return new CursorLoader(getActivity(), baseUri,
                CONTACTS_SUMMARY_PROJECTION, select, null,
                Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
