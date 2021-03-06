/*
 * Copyright 2010, 2011 Andrew De Quincey -  adq@lidskialf.net
 * This file is part of rEdBus.
 *
 *  rEdBus is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  rEdBus is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with rEdBus.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.redbus.ui;

import java.util.Date;
import java.util.List;

import org.redbus.R;
import org.redbus.settings.SettingsHelper;
import org.redbus.stopdb.IStopDbUpdateResponseListener;
import org.redbus.stopdb.StopDbHelper;
import org.redbus.stopdb.StopDbUpdateHelper;
import org.redbus.trafficnews.ITrafficNewsResponseListener;
import org.redbus.trafficnews.NewsItem;
import org.redbus.trafficnews.TrafficNewsHelper;
import org.redbus.ui.alert.AlertUtils;
import org.redbus.ui.arrivaltime.ArrivalTimeActivity;
import org.redbus.ui.arrivaltime.NearbyBookmarkedArrivalTimeActivity;
import org.redbus.ui.stopmap.StopMapActivity;
import org.redbus.ui.trafficinfo.TrafficInfoActivity;


import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.method.DigitsKeyListener;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

public class BookmarksActivity extends ListActivity implements IStopDbUpdateResponseListener, OnCancelListener, ICommonResultReceiver, ITrafficNewsResponseListener
{	
	private static final String bookmarksXmlFile = "/sdcard/redbus-stops.xml";
	
	private static final int TRAFFIC_CHECK_INTERVAL = 15 * 60;
	
	private BusyDialog busyDialog = null;
	private int stopDbExpectedRequestId = -1;

    private boolean isManualUpdateCheck = false;
	private SettingsHelper listDb;

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
        super.onCreate(savedInstanceState);
        setTitle("Bookmarks");
        setContentView(R.layout.stopbookmarks);
        registerForContextMenu(getListView());
        busyDialog = new BusyDialog(this);

	    
	    SettingsHelper.triggerInitialGoogleBackup(this);
	}

	@Override
	protected void onResume() 
	{
		super.onResume();
        
        doSetupStuff();
        
		SettingsHelper tmp = Common.updateBookmarksListAdaptor(this);
		if (tmp != null)
			listDb = tmp;
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		Common.destroyBookmarksListAdaptor(this, listDb);
		
		if (busyDialog != null)
			busyDialog.dismiss();
		busyDialog = null;
	}

	public void onCancel(DialogInterface dialog) {
		stopDbExpectedRequestId = -1;
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		doShowArrivalTimes((int) id);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
	    MenuInflater inflater = getMenuInflater();
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
			Common.doRenameBookmark(this, (int) stopCode, bookmarkName, this);
			return true;

		case R.id.stopbookmarks_item_menu_delete:
			Common.doDeleteBookmark(this, (int) stopCode, this);
			return true;
		}

		return super.onContextItemSelected(item);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {		
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.stopbookmarks_menu, menu);			
	    return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
	    
	    // Update the tabs menu option
	    MenuItem settingsMI = menu.findItem(R.id.stopbookmarks_menu_settings);
	    MenuItem tabsMI = settingsMI.getSubMenu().findItem(R.id.stopbookmarks_menu_tabs);
	    MenuItem viewMI = menu.findItem(R.id.stopbookmarks_menu_view);
	    MenuItem checkTrafficMI = menu.findItem(R.id.stopbookmarks_menu_checktraffic);
		SettingsHelper settings = new SettingsHelper(this);
		if (settings.getGlobalSetting("TABSENABLED", "true").equals("true")) {
			tabsMI.setTitle("Disable tabs");
			viewMI.setVisible(false);
			checkTrafficMI.setVisible(true);
		} else {
			tabsMI.setTitle("Enable tabs");
			viewMI.setVisible(true);
			checkTrafficMI.setVisible(false);
		}
		
		return super.onPrepareOptionsMenu(menu);
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
			startActivity(new Intent().setClass(this, StopMapActivity.class));			
			return true;
			
		case R.id.stopbookmarks_menu_viewnearby:
			startActivity(new Intent().setClass(this, NearbyBookmarkedArrivalTimeActivity.class));			
			return true;
		}

		return false;
	}
	
	private void doShowArrivalTimes(int stopCode) {
		ArrivalTimeActivity.showActivity(this, stopCode);		
	}
	
	private void doShowOnMap(int stopCode) {
		StopDbHelper pt = StopDbHelper.Load(this);
		int stopNodeIdx = pt.lookupStopNodeIdxByStopCode(stopCode);
		if (stopNodeIdx != -1)
			StopMapActivity.showActivity(this, pt.lat[stopNodeIdx], pt.lon[stopNodeIdx]);
	}
	
	private void doEnterStopcode() {
		LayoutInflater layoutInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View stopCodeDialogView = layoutInflater.inflate(R.layout.enterstopcode, null);

		final EditText stopCodeText = (EditText) stopCodeDialogView.findViewById(R.id.enterstopcode_code);
		final CheckBox addbookmarkCb = (CheckBox) stopCodeDialogView.findViewById(R.id.enterstopcode_addbookmark);
		stopCodeText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(8), new DigitsKeyListener() } );

		// show the dialog!
		new AlertDialog.Builder(this)
			.setView(stopCodeDialogView)
			.setTitle("Enter Stopcode")
			.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						long stopCode = -1;
						try {
							stopCode = Long.parseLong(stopCodeText.getText().toString());
						} catch (Exception ex) {
							new AlertDialog.Builder(BookmarksActivity.this)
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
							new AlertDialog.Builder(BookmarksActivity.this)
								.setTitle("Barcode Scanner required")
								.setMessage("You will need Barcode Scanner installed for this to work. Would you like to go to the Android Market to install it?")
								.setPositiveButton(android.R.string.ok, 
										new DialogInterface.OnClickListener() {
						                    public void onClick(DialogInterface dialog, int whichButton) {
						                    	try {
						                    		startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.zxing.client.android")));
						                    	} catch (Throwable t) {
						                    		Toast.makeText(BookmarksActivity.this, "Sorry, I couldn't find the Android Market either!", 5000).show();
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
	        if (resultCode == RESULT_OK) {
	            String contents = intent.getStringExtra("SCAN_RESULT");
				try {	            
		            if (!contents.startsWith("http://mobile.mybustracker.co.uk/?busStopCode="))
		            	throw new RuntimeException();
		            
		            long stopCode = Long.parseLong(contents.substring(contents.indexOf('=') + 1));
		            doHandleEnterStopcode(stopCode, addBookmark);
				} catch (Throwable t) {
	            	Toast.makeText(this, "That doesn't look like a bus stop barcode", 5000).show();
				}
	        }
	    }
	}
	
	private void doHandleEnterStopcode(long stopCode, boolean addBookmark) {
		StopDbHelper pt = StopDbHelper.Load(BookmarksActivity.this);
		int stopNodeIdx = pt.lookupStopNodeIdxByStopCode((int) stopCode);
		if (stopNodeIdx == -1) {
			new AlertDialog.Builder(BookmarksActivity.this)
					.setTitle("Error")
					.setMessage("The stopcode was invalid; please try again")
					.setPositiveButton(android.R.string.ok, null)
					.show();
			return;
		}

		ArrivalTimeActivity.showActivity(BookmarksActivity.this, (int) stopCode);
		if (addBookmark) {
			String stopName = pt.lookupStopNameByStopNodeIdx(stopNodeIdx);
			Common.doAddBookmark(BookmarksActivity.this, (int) stopCode, stopName);
		}		
	}
	
	private void doBookmarksBackup() {
        SettingsHelper db = new SettingsHelper(this);
        if (db.backup(bookmarksXmlFile))
        	Toast.makeText(this, "Bookmarks saved to " + bookmarksXmlFile, Toast.LENGTH_SHORT).show();
	}
	
	private void doBookmarksRestore() {
        SettingsHelper db = new SettingsHelper(this);
        if (db.restore(bookmarksXmlFile)) {
	        Toast.makeText(this, "Bookmarks restored from " + bookmarksXmlFile, Toast.LENGTH_SHORT).show();
    		SettingsHelper tmp = Common.updateBookmarksListAdaptor(this);
    		if (tmp != null)
    			listDb = tmp;
        }
	}
	
	private void doCheckStopDbUpdate() {
        // display changes popup
        SettingsHelper db = new SettingsHelper(this);
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
		TrafficInfoActivity.showActivity(this);
	}
	
	private void doTabsSetting() {		
        SettingsHelper db = new SettingsHelper(this);
        if (db.getGlobalSetting("TABSENABLED", "true").equals("true")) {
        	db.setGlobalSetting("TABSENABLED", "false");
        } else {
        	db.setGlobalSetting("TABSENABLED", "true");
        }
        
    	Intent intent = new Intent(this, RedbusTabView.class);
    	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    	startActivity(intent);
	}

	
	
	
	
	
	
	private void doSetupStuff() {
        SettingsHelper db = new SettingsHelper(this);
        try {
        	long nowSecs = new Date().getTime() / 1000;
        	
        	// show the changelog if this is a version upgrade.
        	PackageInfo pi = getPackageManager().getPackageInfo("org.redbus", 0);
        	if (!db.getGlobalSetting("PREVIOUSVERSIONCODE", "").equals(Integer.toString(pi.versionCode))) {
        		new AlertDialog.Builder(this).
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
            if (getIntent().getBooleanExtra("DoManualUpdate", false)) {
            	if (busyDialog != null)
            		busyDialog.show(BookmarksActivity.this, "Checking for updates...");
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
					TrafficNewsHelper.getTrafficNewsAsync(db.getGlobalSetting("trafficLastTweetId", null), 1, this);
				}
    		}
            
        } catch (Throwable t) {
        	// ignore
        } finally {
        	db.close();
        }
	}

	private void setNextUpdateTime(boolean wasSuccessful) {	
        SettingsHelper db = new SettingsHelper(this);
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

	public void onAsyncCheckUpdateError(int requestId) {
		if (requestId != stopDbExpectedRequestId)
			return;

		if (busyDialog != null)
			busyDialog.dismiss();
		
		if (isManualUpdateCheck)
			Toast.makeText(this, "Failed to check for bus stop data updates; please try again later", Toast.LENGTH_SHORT).show();
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
				Toast.makeText(this, "No new bus stop data available", Toast.LENGTH_SHORT).show();
			return;
		}

		// if its an auto-update, download it right away, otherwise prompt user
		if (!isManualUpdateCheck) {
        	if (busyDialog != null)
        		busyDialog.show(BookmarksActivity.this, "Downloading bus data update...");
        	stopDbExpectedRequestId = StopDbUpdateHelper.getUpdate(updateDate, BookmarksActivity.this);
		} else {
			final long updateDateF = updateDate;
			new AlertDialog.Builder(this)
				.setTitle("New bus stops")
				.setMessage("New bus stop data is available; shall I download it now?")
				.setPositiveButton(android.R.string.ok, 
						new DialogInterface.OnClickListener() {
		                    public void onClick(DialogInterface dialog, int whichButton) {
		                    	if (busyDialog != null)
		                    		busyDialog.show(BookmarksActivity.this, "Downloading bus data update...");
		                    	stopDbExpectedRequestId = StopDbUpdateHelper.getUpdate(updateDateF, BookmarksActivity.this);
		                    }
						})
				.setNegativeButton(android.R.string.cancel, null)
		        .show();		
		}
	}

	public void onAsyncGetUpdateError(int requestId) {
		if (requestId != stopDbExpectedRequestId)
			return;

		if (busyDialog != null)
			busyDialog.dismiss();

		Toast.makeText(this, "Failed to download update; please try again later", Toast.LENGTH_SHORT).show();
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
			Toast.makeText(this, "Failed to download update; please try again later", Toast.LENGTH_SHORT).show();
			return;
		}

		Toast.makeText(this, "Update downloaded, and installed successfully...", Toast.LENGTH_SHORT).show();
		
        SettingsHelper db = new SettingsHelper(this);
        try {
        	db.setGlobalSetting("LASTUPDATE", Long.toString(updateDate));
        } catch (Exception e) {
        	// ignore
        } finally {
        	db.close();
        }
	}

	public void OnBookmarkRenamedOK(int stopCode) {
    	SettingsHelper tmp = Common.updateBookmarksListAdaptor(this);
		if (tmp != null)
			listDb = tmp;
	}

	public void OnBookmarkDeletedOK(int stopCode) {
		SettingsHelper tmp = Common.updateBookmarksListAdaptor(this);
		if (tmp != null)
			listDb = tmp;
	}

	public void onAsyncGetTrafficNewsError(int requestId, int code, String message) {
		// ignore
	}

	public void onAsyncGetTrafficNewsSuccess(int requestId, List<NewsItem> newsItems) {
		if (newsItems.size() == 0)
			return;

        SettingsHelper db = null;
        try {
        	db = new SettingsHelper(this);
        	
			// record the last tweet id
			String lastTweetId = db.getGlobalSetting("trafficLastTweetId", null);
			boolean hadLastTweetId = lastTweetId != null;
			if (newsItems.size() > 0)
				lastTweetId = newsItems.get(0).tweetId;
			if (lastTweetId != null)
				db.setGlobalSetting("trafficLastTweetId", lastTweetId);
	
			// if this is the first time we've recorded a tweet id, don't display the alert, to avoid spamming *everyone* on the first upgrade.
			if (!hadLastTweetId)
				return;

			// don't bother if its not for today.
			Date now = new Date();
			Date firstItemDate = newsItems.get(0).date;
			if ((now.getDate() != firstItemDate.getDate()) || (now.getMonth() != firstItemDate.getMonth()) || (now.getYear() != firstItemDate.getYear()))
				return;
        	
			Intent ui = new Intent(this, TrafficInfoActivity.class);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, ui, PendingIntent.FLAG_CANCEL_CURRENT);
	
			Notification notification = new Notification(R.drawable.trafficalert38, "New traffic information", System.currentTimeMillis());
			notification.defaults |= 0;
			notification.flags |= Notification.FLAG_AUTO_CANCEL;
			notification.setLatestEventInfo(this, "New traffic information available", "Press to view", contentIntent);
			
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			nm.notify(AlertUtils.TRAFFIC_NOTIFICATION_ID, notification);		
        } catch (Exception e) {
        	// ignore
        } finally {
        	if (db != null)
        		db.close();
        }
	}
}
