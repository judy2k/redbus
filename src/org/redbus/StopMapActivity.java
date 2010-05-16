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


import android.os.Bundle;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

public class StopMapActivity extends MapActivity {

	private MapView mapView;
	private MapController mapController;
	private MyLocationOverlay myLocationOverlay;
	
	@Override
    public void onCreate(Bundle savedInstanceState) { 
       super.onCreate(savedInstanceState); 
       setContentView(R.layout.stop_map);
       
       mapView = (MapView) findViewById(R.id.mapview);
       mapView.setBuiltInZoomControls(true);
       mapController = mapView.getController();
       
       mapController.setZoom(17);
       
       // Make map update automatically as user moves around
       myLocationOverlay = new MyLocationOverlay(this, mapView);
       
       myLocationOverlay.runOnFirstFix(new Runnable() {
           public void run() {
               mapController.animateTo(myLocationOverlay.getMyLocation());
           }
       });
          
       mapView.getOverlays().add(myLocationOverlay);
       myLocationOverlay.enableCompass();
       
       //int latSpan = mapView.getLatitudeSpan();
       //int longSpan = mapView.getLongitudeSpan();
       
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}
	
	@Override
	public void onPause() {
	       myLocationOverlay.disableMyLocation();
	       super.onPause();
	}
	
	@Override
	public void onResume() {
	       myLocationOverlay.enableMyLocation();
	       super.onResume();
	}
	
	
}
