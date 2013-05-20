package org.redbus.ui;


import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import org.redbus.R;
import org.redbus.data.RedbusContentProvider;
import org.redbus.settings.SettingsHelper;
import org.redbus.stopdb.IStopDbUpdateResponseListener;
import org.redbus.stopdb.StopDbHelper;
import org.redbus.stopdb.StopDbUpdateHelper;
import org.redbus.trafficnews.TrafficNewsHelper;

import java.util.Date;

public class BookmarksFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, DialogInterface.OnCancelListener, IStopDbUpdateResponseListener {
    private static final String bookmarksXmlFile = "/sdcard/redbus-stops.xml";

    public static final String BOOKMARKS_TABLE = "Bookmarks";
    public static final String BOOKMARKS_COL_STOPNAME = "StopName";

    private static final int TRAFFIC_CHECK_INTERVAL = 15 * 60;

    private BusyDialog busyDialog = null;
    private int stopDbExpectedRequestId = -1;

    private boolean isManualUpdateCheck = false;
    private SettingsHelper listDb;

    private static final String[] columnNames = new String[] { SettingsHelper.ID, SettingsHelper.BOOKMARKS_COL_STOPNAME};
    private static final int[] listViewIds = new int[] { R.id.stopbookmarks_stopcode, R.id.stopbookmarks_name};

    private SimpleCursorAdapter bookmarksAdapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        //setTitle("Bookmarks");

        busyDialog = new BusyDialog(getActivity());
        registerForContextMenu(getListView());

        listDb = new SettingsHelper(getActivity());

        // The following maps a row in the database to a row in the view:
        bookmarksAdapter = new SimpleCursorAdapter(getActivity(), R.layout.stopbookmarks_item, null,
                columnNames, listViewIds, 0);
        setListAdapter(bookmarksAdapter);

//        setListShown(false);

        SettingsHelper.triggerInitialGoogleBackup(getActivity());

        // Register for callbacks:
        getLoaderManager().initLoader(0, null, this);
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

        SettingsHelper db = new SettingsHelper(getActivity());
        try {
            long nowSecs = new Date().getTime() / 1000;

            // show the changelog if this is a version upgrade.
            PackageInfo pi = getActivity().getPackageManager().getPackageInfo("org.redbus", 0);
            if (!db.getGlobalSetting("PREVIOUSVERSIONCODE", "").equals(Integer.toString(pi.versionCode))) {
                new AlertDialog.Builder(getActivity()).
                        setIcon(null).
                        setTitle("v" + pi.versionName + " changes").
                        setMessage(R.string.newversiontext).
                        setPositiveButton(android.R.string.ok, null).
                        show();
                db.setGlobalSetting("PREVIOUSVERSIONCODE", Integer.toString(pi.versionCode));
            }

            // check for stop database updates
            long nextUpdateCheck = Long.parseLong(db.getGlobalSetting("NEXTUPDATECHECK", "0"));
            long lastUpdateDate = Long.parseLong(db.getGlobalSetting("LASTUPDATE", "-1"));
            if (getActivity().getIntent().getBooleanExtra("DoManualUpdate", false)) {
                if (busyDialog != null)
                    busyDialog.show(BookmarksFragment.this, "Checking for updates...");
                isManualUpdateCheck = true;
                stopDbExpectedRequestId = StopDbUpdateHelper.checkForUpdates(lastUpdateDate, this);
            } else {
                // otherwise, we do an background update check
                if (nextUpdateCheck <= nowSecs) {
                    isManualUpdateCheck = false;
                    stopDbExpectedRequestId = StopDbUpdateHelper.checkForUpdates(lastUpdateDate, this);
                }
            }

            // check for new traffic information every TrafficCheckInternal seconds
            String doAutoTraffic = db.getGlobalSetting("AUTO_TRAFFIC", "0");
            if (doAutoTraffic.equals("1")) {
                long lastTrafficCheck = Long.parseLong(db.getGlobalSetting("LASTTRAFFICCHECK", "-1"));
                if ((lastTrafficCheck + TRAFFIC_CHECK_INTERVAL) <= nowSecs) {
                    db.setGlobalSetting("LASTTRAFFICCHECK", Long.toString(nowSecs));
                    // TODO: BRING THIS BACK
                    //TrafficNewsHelper.getTrafficNewsAsync(db.getGlobalSetting("trafficLastTweetId", null), 1, this);
                }
            }

        } catch (Throwable t) {
            // ignore
        } finally {
            db.close();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (busyDialog != null)
            busyDialog.dismiss();
        busyDialog = null;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(
                getActivity(),  // Context
                RedbusContentProvider.BOOKMARKS_URI,    // URI
                null,                                   // projection
                null,                                   // selection
                null,                                   // selectionArgs
                BOOKMARKS_COL_STOPNAME                  // Sort by stop name
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        bookmarksAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        bookmarksAdapter.swapCursor(null);
    }

    @Override
    public void onCancel(DialogInterface dialogInterface) {
        stopDbExpectedRequestId = -1;
    }

    public void onAsyncCheckUpdateError(int requestId) {
        if (requestId != stopDbExpectedRequestId)
            return;

        if (busyDialog != null)
            busyDialog.dismiss();

        if (isManualUpdateCheck)
            Toast.makeText(getActivity(), "Failed to check for bus stop data updates; please try again later", Toast.LENGTH_SHORT).show();
        setNextUpdateTime(false);
    }

    public void onAsyncCheckUpdateSuccess(int requestId, long updateDate) {
        if (requestId != stopDbExpectedRequestId)
            return;

        if (busyDialog != null)
            busyDialog.dismiss();
        setNextUpdateTime(true);

        if (updateDate == 0) {
            if (isManualUpdateCheck)
                Toast.makeText(getActivity(), "No new bus stop data available", Toast.LENGTH_SHORT).show();
            return;
        }

        // if its an auto-update, download it right away, otherwise prompt user
        if (!isManualUpdateCheck) {
            if (busyDialog != null)
                busyDialog.show(BookmarksFragment.this, "Downloading bus data update...");
            stopDbExpectedRequestId = StopDbUpdateHelper.getUpdate(updateDate, BookmarksFragment.this);
        } else {
            final long updateDateF = updateDate;
            new AlertDialog.Builder(getActivity())
                    .setTitle("New bus stops")
                    .setMessage("New bus stop data is available; shall I download it now?")
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    if (busyDialog != null)
                                        busyDialog.show(BookmarksFragment.this, "Downloading bus data update...");
                                    stopDbExpectedRequestId = StopDbUpdateHelper.getUpdate(updateDateF, BookmarksFragment.this);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    private void setNextUpdateTime(boolean wasSuccessful) {
        SettingsHelper db = new SettingsHelper(getActivity());
        try {
            int retryCount = Integer.parseInt(db.getGlobalSetting("UPDATECHECKRETRIES", "0"));
            if ((retryCount < 3) && (!wasSuccessful)) {
                // if it was unsuccessful and we've had less that 3 retries, try again in a minute
                long nextUpdateCheck = new Date().getTime() / 1000;
                nextUpdateCheck += 60; // 1 min
                db.setGlobalSetting("NEXTUPDATECHECK", Long.toString(nextUpdateCheck));
                db.getGlobalSetting("UPDATECHECKRETRIES", Integer.toString(retryCount + 1));
            } else {
                // if it was successful OR we've had > 2 retries, then we delay for ~24 hours before trying again
                long nextUpdateCheck = new Date().getTime() / 1000;
                nextUpdateCheck += 23 * 60 * 60; // 1 day
                nextUpdateCheck += (long) (Math.random() * (2 * 60 * 60.0)); // some random time in the 2 hours afterwards
                db.setGlobalSetting("NEXTUPDATECHECK", Long.toString(nextUpdateCheck));
                db.getGlobalSetting("UPDATECHECKRETRIES", "0");
            }
        } finally {
            db.close();
        }
    }

    public void onAsyncGetUpdateError(int requestId) {
        if (requestId != stopDbExpectedRequestId)
            return;

        if (busyDialog != null)
            busyDialog.dismiss();

        Toast.makeText(getActivity(), "Failed to download update; please try again later", Toast.LENGTH_SHORT).show();
    }

    public void onAsyncGetUpdateSuccess(int requestId, long updateDate, byte[] updateData) {
        if (requestId != stopDbExpectedRequestId)
            return;

        if (busyDialog != null)
            busyDialog.dismiss();

        try {
            StopDbHelper.saveRawDb(updateData);
        } catch (Throwable t) {
            Log.e("BusStopDatabaseUpdateHelper", "onPostExecute", t);
            Toast.makeText(getActivity(), "Failed to download update; please try again later", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getActivity(), "Update downloaded, and installed successfully...", Toast.LENGTH_SHORT).show();

        SettingsHelper db = new SettingsHelper(getActivity());
        try {
            db.setGlobalSetting("LASTUPDATE", Long.toString(updateDate));
        } catch (Exception e) {
            // ignore
        } finally {
            db.close();
        }
    }
}
