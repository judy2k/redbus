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

import org.redbus.settings.SettingsHelper;
import org.redbus.stopdb.StopDbHelper;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Toast;

public class NearbyBookmarkedArrivalTimeActivity extends Activity {

    private LocationManager locationManager;
    private LocationListener locationListener;
    private ArrayList<Integer> bookmarkIds; // Stop codes of current bookmarks
    

    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
    	super.onCreate(savedInstanceState);
    	setTitle("Nearby");
    }
    
    @Override
    protected void onResume() 
    {
            super.onResume();
            this.bookmarkIds = getBookmarks();
            startLocationListener();
    }
    
    @Override
    public void onPause() 
    {
        super.onPause();
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
            // Now get the ones near us
            int x = (int)(location.getLatitude() * 1E6);
            int y = (int)(location.getLongitude() * 1E6);
            
            StopDbHelper pt = StopDbHelper.Load(this);
            
            ArrayList<Integer> nearby = pt.getStopsWithinRadius((int)x, (int)y, bookmarkIds, 0.008);
            
            // Just put in a debug string for now
            String toastTemp = "Nearby:";
            for(Integer stop : nearby)
            {
            	String name = pt.lookupStopNameByStopNodeIdx(pt.lookupStopNodeIdxByStopCode(stop));
            	toastTemp = toastTemp + "\n"+name;
            }
            
            Toast.makeText(getBaseContext(), 
                    toastTemp, 
                    Toast.LENGTH_SHORT).show(); 
            
    }

}
