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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

public class NearbyBookmarkedArrivalTimeActivity extends ListActivity implements IArrivalTimeResponseListener, OnCancelListener {

    private LocationManager locationManager;
    private LocationListener locationListener;
    private ArrayList<Integer> bookmarkIds; // Stop codes of current bookmarks
    private List<ArrivalTime> times; // The actual retrieved times
    private Iterator<Integer> nextStopToDownload;
	private BusyDialog busyDialog = null;
	private int expectedRequestId = -1;
	private ArrivalTimeArrayAdapter lvAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
    	super.onCreate(savedInstanceState);
    	setTitle("Nearby");
    	busyDialog = new BusyDialog(this);
    	this.times = new ArrayList<ArrivalTime>();
		lvAdapter = new ArrivalTimeArrayAdapter(this, R.layout.bustimes_item, this.times);
		setListAdapter(lvAdapter);
    }
    
    @Override
    protected void onResume() 
    {
            super.onResume();
            this.bookmarkIds = getBookmarks();
            this.times.clear();
            this.lvAdapter.notifyDataSetChanged();
            startLocationListener();
            busyDialog.show(this, "Finding bookmarked stops nearby");
    }
    
    @Override
    public void onPause() 
    {
        super.onPause();
        stopLocationListener();
    }
    
    @Override
    public void onDestroy()
    {
    	super.onDestroy();
    	busyDialog.dismiss();
    }
    
    private void stopLocationListener()
    {
    	locationManager.removeUpdates(locationListener);	
    }
    
    private void startLocationListener()
    {
            // Acquire a reference to the system Location Manager
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

            // Define a listener that responds to location updates
            locationListener = new LocationListener() {
                public void onLocationChanged(Location location) {
                  // Called when a new location is found by the network location provider.
                 locationUpdated(location);
                }

                public void onProviderEnabled(String provider) {}
                public void onProviderDisabled(String provider) {}
				public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}
              };

            // Use network updates for quick, but rough updates
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, // min time
            1, // min distance
            locationListener);
    }

    private ArrayList<Integer> getBookmarks()
    {
        SettingsHelper db = new SettingsHelper(this);
        
        Cursor bookmarks = db.getBookmarks();
        
        ArrayList<Integer>bookmarkIds = new ArrayList<Integer>();
        
         bookmarks.moveToFirst();
         while (bookmarks.isAfterLast() == false) {
            bookmarkIds.add(bookmarks.getInt(0));
            bookmarks.moveToNext();
        }
     
        db.close();
    	return bookmarkIds;
    }
    
    private void locationUpdated(Location location)
    {
    	stopLocationListener(); // For now only update once

    	// Now get the ones near us
    	int x = (int)(location.getLatitude() * 1E6);
    	int y = (int)(location.getLongitude() * 1E6);

    	StopDbHelper pt = StopDbHelper.Load(this);

    	// 0.008 empirically determined for now!
    	ArrayList<Integer> nearby = pt.getStopsWithinRadius((int)x, (int)y, bookmarkIds, 0.008);

    	// Nearby names toast
    	String toastTxt = "Nearby:";
    	for(Integer stopCode : nearby)
    	{
    		String name = pt.lookupStopNameByStopNodeIdx(pt.lookupStopNodeIdxByStopCode(stopCode));
    		toastTxt += "\n"+name;
    	}

    	Toast.makeText(getBaseContext(), 
    			toastTxt, 
    			Toast.LENGTH_SHORT).show(); 

    	// Download the first stop
    	nextStopToDownload = nearby.iterator();
    	triggerDownloadNextStop();
    }

    private void triggerDownloadNextStop()
    {
    	if (nextStopToDownload.hasNext())
    	{
    		Integer nextStop = nextStopToDownload.next();

    		busyDialog.dismiss();
    		busyDialog.show(this, "Downloading "+nextStop);
    		
    		expectedRequestId = ArrivalTimeHelper.getBusTimesAsync(nextStop, 0, null, NearbyBookmarkedArrivalTimeActivity.this);
    	}
    }

    // FIXME - this is common with ArrivalTimeActivity
	public void getBusTimesError(int requestId, int code, String message) {
		if (requestId != expectedRequestId)
			return;
		
		busyDialog.dismiss();

		setListAdapter(new ArrivalTimeArrayAdapter(this, R.layout.bustimes_item, new ArrayList<ArrivalTime>()));
		findViewById(R.id.bustimes_error).setVisibility(View.VISIBLE);

		new AlertDialog.Builder(this).setTitle("Error").
			setMessage("Unable to download bus times: " + message).
			setPositiveButton(android.R.string.ok, null).
			show();
	}

	public void getBusTimesSuccess(int requestId, List<ArrivalTime> busTimes) {
		if (requestId != expectedRequestId)
			return;
		this.times.addAll(busTimes);
		triggerDownloadNextStop();
		busyDialog.dismiss();

		// FIXME - this is common with ArrivalTimeActivity
		Collections.sort(this.times, new Comparator<ArrivalTime>() {
			public int compare(ArrivalTime arg0, ArrivalTime arg1) {
				if ((arg0.arrivalAbsoluteTime != null) && (arg1.arrivalAbsoluteTime != null)) {
					// bus data never seems to span to the next day, so this string comparison should always work
					return arg0.arrivalAbsoluteTime.compareTo(arg1.arrivalAbsoluteTime);
				}
				return arg0.arrivalSortingIndex - arg1.arrivalSortingIndex;
			}
		});
		
		lvAdapter.notifyDataSetChanged();
	}

	public void onCancel(DialogInterface arg0) {
		expectedRequestId = -1;
	}

}
