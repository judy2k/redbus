/*
 * Copyright 2011 Colin Paton - cozzarp@googlemail.com
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

package org.redbus.ui.arrivaltime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.redbus.R;
import org.redbus.arrivaltime.ArrivalTime;
import org.redbus.arrivaltime.ArrivalTimeHelper;
import org.redbus.arrivaltime.IArrivalTimeResponseListener;
import org.redbus.settings.SettingsHelper;
import org.redbus.stopdb.StopDbHelper;
import org.redbus.ui.BusyDialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

public class NearbyBookmarkedArrivalTimeActivity extends Activity implements IArrivalTimeResponseListener, OnCancelListener {

	private LocationManager locationManager;
    private LocationListener locationListener;
    private ArrayList<Integer> bookmarkIds; // Stop codes of current bookmarks
    private List<ArrivalTime> arrivalTimes; // The actual retrieved times
    private Iterator<Integer> nextStopToDownload;
    private BusyDialog busyDialog = null;
	private int expectedRequestId = -1;
	private ArrivalTimeArrayAdapter lvAdapter;
	private StopDbHelper pt;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	setTitle("Nearby services");
    	
    	busyDialog = new BusyDialog(this);
    	arrivalTimes = new ArrayList<ArrivalTime>();
		lvAdapter = new ArrivalTimeArrayAdapter(this, R.layout.bustimes_item, arrivalTimes);		
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		setContentView(R.layout.nearbybookmarkedarrivaltime);
		
		ListView lv = (ListView)findViewById(R.id.nearbystopslist);
		lv.setAdapter(lvAdapter);

    	pt = StopDbHelper.Load(this);
    	
        // Define a listener that responds to location updates
        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
	            // Called when a new location is found by the network location provider.
	            // FIXME - check location accuracy + error if too great
            	try {
            		locationUpdated(location);
            	} catch (Throwable t) {
            	}
            }

            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
			public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}
          };
          
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	refresh();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        stopLocationListener();
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	busyDialog.dismiss();
    }
    
    private void refresh() {
		showBlank();
    	bookmarkIds = getBookmarks();
    	arrivalTimes.clear();
    	lvAdapter.notifyDataSetChanged();
    }
    
    private void stopLocationListener() {
    	locationManager.removeUpdates(locationListener);	
    }
    
    private void startLocationListener() {
    	// Use network updates for quick, but rough updates

    	locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, // min time
    	
    	//locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, // min time
    			1, // min distance
    			locationListener);
    }

    private ArrayList<Integer> getBookmarks() {
        SettingsHelper db = new SettingsHelper(this);
        Cursor bookmarks = db.getBookmarks();
        ArrayList<Integer>bookmarkIds = new ArrayList<Integer>();
        
         bookmarks.moveToFirst();
         while (bookmarks.isAfterLast() == false) {
            bookmarkIds.add(bookmarks.getInt(0));
            bookmarks.moveToNext();
        }
     
        db.close();

        if (!bookmarkIds.isEmpty()) {
        	startLocationListener();
        	busyDialog.show(this, "Waiting for GPS to find bookmarked stops nearby");
        }
    	return bookmarkIds;
    }
    
    private void locationUpdated(Location location) {
    	stopLocationListener(); // For now only update once

    	// Now get the ones near us
    	int x = (int)(location.getLatitude() * 1E6);
    	int y = (int)(location.getLongitude() * 1E6);

    	// 0.008 empirically determined for now!
    	ArrayList<Integer> nearby = pt.getStopsWithinRadius((int)x, (int)y, bookmarkIds, 0.008);
    	
    	// Nearby names toast
    	String toastTxt = "Nearby:";
    	for(Integer stopCode : nearby) {
    		String name = pt.lookupStopNameByStopNodeIdx(pt.lookupStopNodeIdxByStopCode(stopCode));
    		toastTxt += "\n"+name;
    	}

    	if (nearby.isEmpty())
    		showNoStopsMessage();
    		
    	busyDialog.dismiss();
    	Toast.makeText(getBaseContext(), 
    			toastTxt, 
    			Toast.LENGTH_SHORT).show(); 

    	// Download the first stop
    	nextStopToDownload = nearby.iterator();
    	triggerDownloadNextStop();
    }

    private void triggerDownloadNextStop() {
    	if (nextStopToDownload.hasNext()) {
    		Integer nextStop = nextStopToDownload.next();

    		busyDialog.dismiss();
    		busyDialog.show(this, "Getting times for "+pt.lookupStopNameByStopNodeIdx(pt.lookupStopNodeIdxByStopCode(nextStop)));
    		
    		expectedRequestId = ArrivalTimeHelper.getBusTimesAsync(nextStop, 0, null, NearbyBookmarkedArrivalTimeActivity.this);
    	}
    }

    // FIXME - this is common with ArrivalTimeActivity
	public void onAsyncGetBusTimesError(int requestId, int code, String message) {
		if (requestId != expectedRequestId)
			return;
		
		busyDialog.dismiss();

		showNoServicesMessage();
		
		new AlertDialog.Builder(this).setTitle("Error").
			setMessage("Unable to download bus times: " + message).
			setPositiveButton(android.R.string.ok, null).
			show();
	}

	public void onAsyncGetBusTimesSuccess(int requestId, List<ArrivalTime> busTimes) {
		if (requestId != expectedRequestId)
			return;
		
		arrivalTimes.addAll(busTimes);
		triggerDownloadNextStop();
		busyDialog.dismiss();

		// FIXME - this is common with ArrivalTimeActivity
		Collections.sort(arrivalTimes, new Comparator<ArrivalTime>() {
			public int compare(ArrivalTime arg0, ArrivalTime arg1) {
				if ((arg0.arrivalAbsoluteTime != null) && (arg1.arrivalAbsoluteTime != null)) {
					// bus data never seems to span to the next day, so this string comparison should always work
					return arg0.arrivalAbsoluteTime.compareTo(arg1.arrivalAbsoluteTime);
				}
				return arg0.arrivalSortingIndex - arg1.arrivalSortingIndex;
			}
		});
		
		lvAdapter.notifyDataSetChanged();
		showData();
	}
	
	
	// Don't like the way this is done; would rather set the appropriate
	// single item as visible. However, need to change view parents
	// if letting view inflater provided views do this..this actually
	// seems simpler for now
	
	private void showNoStopsMessage()
	{
		findViewById(R.id.nearbynobookmarks).setVisibility(View.VISIBLE);
		findViewById(R.id.nearbybookmarkednoservices).setVisibility(View.GONE);
		findViewById(R.id.nearbystopslist).setVisibility(View.GONE);
	}
	
	private void showNoServicesMessage()
	{
		findViewById(R.id.nearbynobookmarks).setVisibility(View.GONE);
		findViewById(R.id.nearbybookmarkednoservices).setVisibility(View.VISIBLE);
		findViewById(R.id.nearbystopslist).setVisibility(View.GONE);
	}
	
	private void showData()
	{
		findViewById(R.id.nearbynobookmarks).setVisibility(View.GONE);
		findViewById(R.id.nearbybookmarkednoservices).setVisibility(View.GONE);
		findViewById(R.id.nearbystopslist).setVisibility(View.VISIBLE);
	}
	
	private void showBlank()
	{
		findViewById(R.id.nearbynobookmarks).setVisibility(View.GONE);
		findViewById(R.id.nearbybookmarkednoservices).setVisibility(View.GONE);
		findViewById(R.id.nearbystopslist).setVisibility(View.GONE);
	}

	public void onCancel(DialogInterface arg0) {
		expectedRequestId = -1;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add("Refresh");
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		refresh();
		return true;
	}
}
