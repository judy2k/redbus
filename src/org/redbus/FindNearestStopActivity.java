/*
 * Copyright 2010 Colin Paton - cozzarp@googlemail.com
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

package org.redbus;

import java.io.IOException;
import java.io.InputStream;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;

public class FindNearestStopActivity extends Activity {
	private LocationManager lm;
	private LocationListener locationListener;
	private PointTree busStopLocations;
	
	private TextView txtStopName;
	private TextView txtStopCode;
	private TextView txtStopDistance;
	
	private PointTree.BusStopTreeNode nearestStop;
	
	@Override
	public void onPause() {
	       lm.removeUpdates(locationListener);
	       super.onStop();
	}
	
	private void locationChanged(Location location)
	{
		double lat = location.getLatitude();
		double lng = location.getLongitude();
		/*
		Toast.makeText(getBaseContext(), 
                "Location changed : Lat: " + lat + 
                " Lng: " + lng, 
                Toast.LENGTH_SHORT).show();	
        */
		
		nearestStop = busStopLocations.findNearest(lat,lng);
		
		Location stopLocation = new Location("busstop");
		stopLocation.setLatitude(nearestStop.getX());
		stopLocation.setLongitude(nearestStop.getY());
		int distanceFromHere = (int)location.distanceTo(stopLocation);
		

		txtStopCode.setText(Long.toString(nearestStop.getStopCode()));
		txtStopName.setText(nearestStop.getStopName());
		txtStopDistance.setText(Integer.toString(distanceFromHere) + "m away");
	}
	

	
	private void startGPS()
	{
        lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 
                5000, // min time
                1, // min distance
                locationListener);  
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.findneareststop);

        txtStopCode = (TextView)findViewById(R.id.txtStopCode);
        txtStopName = (TextView)findViewById(R.id.txtStopName);
        txtStopDistance = (TextView)findViewById(R.id.txtStopDistance);
        // Load the stops data
        InputStream stopsFile = getResources().openRawResource(R.raw.stops);
        try {
			this.busStopLocations = new PointTree(stopsFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
        
		
        // Register for GPS location updates
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);    
        
        locationListener = new LocationListener() {
        	public void onLocationChanged(Location location)
        	{
        		locationChanged(location);
        	}

        	public void onProviderDisabled(String provider) {}
			public void onProviderEnabled(String provider) {}
			public void onStatusChanged(String provider, int status,
					Bundle extras) {}
			
        };
        

        startGPS();
        
        // Go to button
        Button btnGoto = (Button) findViewById(R.id.BtnGoto);
        btnGoto.setOnClickListener(new View.OnClickListener() {
           public void onClick(View arg0) {
        	   // FIXME - Make this a proper map with pins etc
        	   String url = "geo:"+
        	   	Double.toString(nearestStop.getX())+","+Double.toString(nearestStop.getY());
        	   startActivity(new Intent("android.intent.action.VIEW", Uri.parse(url)));
           }
        });
        
        // Bus times button
        Button btnTimes = (Button) findViewById(R.id.BtnTimes);
        btnTimes.setOnClickListener(new View.OnClickListener() {
           public void onClick(View arg0) {
	          BusTimesActivity.showActivity(FindNearestStopActivity.this,
	        		  nearestStop.getStopCode(),
	        		  nearestStop.getStopName());
           } 
        });
    }
    
    @Override
    public void onResume()
    {
    	super.onResume();
    	startGPS();   	
    }
}