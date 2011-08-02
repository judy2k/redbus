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

package org.redbus.ui.stopmap;

import android.content.Context;
import android.graphics.Canvas;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

/* 
 * This is a quick hack fix for some buggy Android versions which throw exceptions from the drawMyLocation method from inside Android's code
 */
public class WorkaroundMyLocationOverlay extends MyLocationOverlay {
	
	private Context ctx;
	
	public WorkaroundMyLocationOverlay(Context ctx, MapView mapView) {
		super(ctx, mapView);
		this.ctx = ctx;
	}

	@Override
	protected void drawMyLocation(Canvas canvas, MapView mapView,
			Location lastFix, GeoPoint myLocation, long when) {
		try {
			super.drawMyLocation(canvas, mapView, lastFix, myLocation, when);
		} catch (Throwable t) {
			
		}
	}

	@Override
	public synchronized void onLocationChanged(Location location) {
		// ignore android's wild guesses!
		if (location.getAccuracy() > 100)
			return;
		
		super.onLocationChanged(location);
	}
}
