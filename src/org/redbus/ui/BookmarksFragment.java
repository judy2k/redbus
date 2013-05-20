package org.redbus.ui;


import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.InputFilter;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.*;
import android.widget.*;
import org.redbus.R;
import org.redbus.data.RedbusContentProvider;
import org.redbus.settings.SettingsHelper;
import org.redbus.stopdb.IStopDbUpdateResponseListener;
import org.redbus.stopdb.StopDbHelper;
import org.redbus.stopdb.StopDbUpdateHelper;
import org.redbus.trafficnews.TrafficNewsHelper;
import org.redbus.ui.arrivaltime.ArrivalTimeActivity;
import org.redbus.ui.arrivaltime.NearbyBookmarkedArrivalTimeActivity;
import org.redbus.ui.stopmap.StopMapActivity;
import org.redbus.ui.trafficinfo.TrafficInfoActivity;

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

        busyDialog = new BusyDialog(getActivity());
        registerForContextMenu(getListView());

        listDb = new SettingsHelper(getActivity());

        // The following maps a row in the database to a row in the view:
        bookmarksAdapter = new SimpleCursorAdapter(getActivity(), R.layout.stopbookmarks_item, null,
                columnNames, listViewIds, 0);
        setListAdapter(bookmarksAdapter);

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

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        doShowArrivalTimes((int) id);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.stopbookmarks_item_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        long stopCode = menuInfo.id;
        String bookmarkName = ((TextView) menuInfo.targetView.findViewById(R.id.stopbookmarks_name)).getText().toString();

        switch(item.getItemId()) {
            case R.id.stopbookmarks_item_menu_bustimes:
                doShowArrivalTimes((int) stopCode);
                return true;

            case R.id.stopbookmarks_item_menu_showonmap:
                doShowOnMap((int) stopCode);
                return true;

            case R.id.stopbookmarks_item_menu_rename:
                // TODO: Put this back in
                //Common.doRenameBookmark(getActivity(), (int) stopCode, bookmarkName, this);
                return true;

            case R.id.stopbookmarks_item_menu_delete:
                // TODO: Put this back in
                //Common.doDeleteBookmark(getActivity(), (int) stopCode, this);
                return true;
        }

        return super.onContextItemSelected(item);
    }

    // TODO: The following items should probably be inflated into the ActionBar:
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        MenuInflater inflater = getActivity().getMenuInflater();
//        inflater.inflate(R.menu.stopbookmarks_menu, menu);
//        return true;
//    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {

        // Update the tabs menu option
        MenuItem settingsMI = menu.findItem(R.id.stopbookmarks_menu_settings);
        MenuItem tabsMI = settingsMI.getSubMenu().findItem(R.id.stopbookmarks_menu_tabs);
        MenuItem viewMI = menu.findItem(R.id.stopbookmarks_menu_view);
        MenuItem checkTrafficMI = menu.findItem(R.id.stopbookmarks_menu_checktraffic);
        SettingsHelper settings = new SettingsHelper(getActivity());
        if (settings.getGlobalSetting("TABSENABLED", "true").equals("true")) {
            tabsMI.setTitle("Disable tabs");
            viewMI.setVisible(false);
            checkTrafficMI.setVisible(true);
        } else {
            tabsMI.setTitle("Enable tabs");
            viewMI.setVisible(true);
            checkTrafficMI.setVisible(false);
        }

        //return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.stopbookmarks_menu_bustimes:
                doEnterStopcode();
                return true;

            case R.id.stopbookmarks_menu_backup:
                doBookmarksBackup();
                return true;

            case R.id.stopbookmarks_menu_restore:
                doBookmarksRestore();
                return true;

            case R.id.stopbookmarks_menu_checktraffic:
            case R.id.stopbookmarks_menu_checktraffic2:
                doCheckTraffic();
                return true;

            case R.id.stopbookmarks_menu_checkupdates:
                doCheckStopDbUpdate();
                return true;

            case R.id.stopbookmarks_menu_tabs:
                doTabsSetting();
                return true;

            case R.id.stopbookmarks_menu_viewmap:
                startActivity(new Intent().setClass(getActivity(), StopMapActivity.class));
                return true;

            case R.id.stopbookmarks_menu_viewnearby:
                startActivity(new Intent().setClass(getActivity(), NearbyBookmarkedArrivalTimeActivity.class));
                return true;
        }

        return false;
    }

    private void doShowArrivalTimes(int stopCode) {
        ArrivalTimeActivity.showActivity(getActivity(), stopCode);
    }

    private void doShowOnMap(int stopCode) {
        StopDbHelper pt = StopDbHelper.Load(getActivity());
        int stopNodeIdx = pt.lookupStopNodeIdxByStopCode(stopCode);
        if (stopNodeIdx != -1)
            StopMapActivity.showActivity(getActivity(), pt.lat[stopNodeIdx], pt.lon[stopNodeIdx]);
    }

    private void doEnterStopcode() {
        LayoutInflater layoutInflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View stopCodeDialogView = layoutInflater.inflate(R.layout.enterstopcode, null);

        final EditText stopCodeText = (EditText) stopCodeDialogView.findViewById(R.id.enterstopcode_code);
        final CheckBox addbookmarkCb = (CheckBox) stopCodeDialogView.findViewById(R.id.enterstopcode_addbookmark);
        stopCodeText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(8), new DigitsKeyListener() } );

        // show the dialog!
        new AlertDialog.Builder(getActivity())
                .setView(stopCodeDialogView)
                .setTitle("Enter Stopcode")
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                long stopCode = -1;
                                try {
                                    stopCode = Long.parseLong(stopCodeText.getText().toString());
                                } catch (Exception ex) {
                                    new AlertDialog.Builder(getActivity())
                                            .setTitle("Error")
                                            .setMessage("The stopcode was invalid; please try again using only numbers")
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();
                                    return;
                                }

                                doHandleEnterStopcode(stopCode, addbookmarkCb.isChecked());
                            }
                        })
                .setNeutralButton("Scan",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                try {
                                    Intent intent = new Intent("com.google.zxing.client.android.SCAN");
                                    intent.setPackage("com.google.zxing.client.android");
                                    intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
                                    startActivityForResult(intent, addbookmarkCb.isChecked() ? 1 : 0);
                                } catch (ActivityNotFoundException ex) {
                                    new AlertDialog.Builder(getActivity())
                                            .setTitle("Barcode Scanner required")
                                            .setMessage("You will need Barcode Scanner installed for this to work. Would you like to go to the Android Market to install it?")
                                            .setPositiveButton(android.R.string.ok,
                                                    new DialogInterface.OnClickListener() {
                                                        public void onClick(DialogInterface dialog, int whichButton) {
                                                            try {
                                                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.zxing.client.android")));
                                                            } catch (Throwable t) {
                                                                Toast.makeText(getActivity(), "Sorry, I couldn't find the Android Market either!", 5000).show();
                                                            }
                                                        }
                                                    })
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show();
                                }
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        boolean addBookmark = requestCode == 1;

        if ((requestCode == 0) || (requestCode == 1)) {
            if (resultCode == getActivity().RESULT_OK) {
                String contents = intent.getStringExtra("SCAN_RESULT");
                try {
                    if (!contents.startsWith("http://mobile.mybustracker.co.uk/?busStopCode="))
                        throw new RuntimeException();

                    long stopCode = Long.parseLong(contents.substring(contents.indexOf('=') + 1));
                    doHandleEnterStopcode(stopCode, addBookmark);
                } catch (Throwable t) {
                    Toast.makeText(getActivity(), "That doesn't look like a bus stop barcode", 5000).show();
                }
            }
        }
    }

    private void doHandleEnterStopcode(long stopCode, boolean addBookmark) {
        StopDbHelper pt = StopDbHelper.Load(getActivity());
        int stopNodeIdx = pt.lookupStopNodeIdxByStopCode((int) stopCode);
        if (stopNodeIdx == -1) {
            new AlertDialog.Builder(getActivity())
                    .setTitle("Error")
                    .setMessage("The stopcode was invalid; please try again")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        ArrivalTimeActivity.showActivity(getActivity(), (int) stopCode);
        if (addBookmark) {
            String stopName = pt.lookupStopNameByStopNodeIdx(stopNodeIdx);
            Common.doAddBookmark(getActivity(), (int) stopCode, stopName);
        }
    }

    private void doBookmarksBackup() {
        SettingsHelper db = new SettingsHelper(getActivity());
        if (db.backup(bookmarksXmlFile))
            Toast.makeText(getActivity(), "Bookmarks saved to " + bookmarksXmlFile, Toast.LENGTH_SHORT).show();
    }

    private void doBookmarksRestore() {
        SettingsHelper db = new SettingsHelper(getActivity());
        if (db.restore(bookmarksXmlFile)) {
            Toast.makeText(getActivity(), "Bookmarks restored from " + bookmarksXmlFile, Toast.LENGTH_SHORT).show();
            // TODO: Force cursor to re-read data
            /* SettingsHelper tmp = Common.updateBookmarksListAdaptor(getActivity());
            if (tmp != null)
                listDb = tmp;
                */
        }
    }

    private void doCheckStopDbUpdate() {
        // display changes popup
        SettingsHelper db = new SettingsHelper(getActivity());
        long lastUpdateDate = -1;
        try {
            lastUpdateDate = Long.parseLong(db.getGlobalSetting("LASTUPDATE", "-1"));
        } catch (Exception e) {
            return;
        } finally {
            db.close();
        }

        if (busyDialog != null)
            busyDialog.show(this, "Checking for updates...");
        isManualUpdateCheck = true;
        stopDbExpectedRequestId = StopDbUpdateHelper.checkForUpdates(lastUpdateDate, this);
    }

    private void doCheckTraffic() {
        TrafficInfoActivity.showActivity(getActivity());
    }

    private void doTabsSetting() {
        SettingsHelper db = new SettingsHelper(getActivity());
        if (db.getGlobalSetting("TABSENABLED", "true").equals("true")) {
            db.setGlobalSetting("TABSENABLED", "false");
        } else {
            db.setGlobalSetting("TABSENABLED", "true");
        }

        Intent intent = new Intent(getActivity(), RedbusTabView.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
