package org.redbus.ui;


import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.redbus.R;
import org.redbus.settings.SettingsHelper;

public class BookmarksFragment extends ListFragment {
    private static final String bookmarksXmlFile = "/sdcard/redbus-stops.xml";

    private static final int TRAFFIC_CHECK_INTERVAL = 15 * 60;

    private BusyDialog busyDialog = null;
    private int stopDbExpectedRequestId = -1;

    private boolean isManualUpdateCheck = false;
    private SettingsHelper listDb;

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        //setTitle("Bookmarks");

        busyDialog = new BusyDialog(getActivity());
        registerForContextMenu(getListView());



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

        SettingsHelper tmp = Common.updateBookmarksListAdaptor(this);
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
}
