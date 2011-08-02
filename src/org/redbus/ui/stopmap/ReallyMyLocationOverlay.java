/*
 * Copyright 2010, 2011 Colin Paton - cozzarp@googlemail.com
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

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

public class ReallyMyLocationOverlay extends MyLocationOverlay {
	
	private MapView mapView;
	private final int MinLocationAccuracy = 100;
	
	public ReallyMyLocationOverlay(Context ctx, MapView mapView) {
		super(ctx, mapView);
		
		this.mapView = mapView;
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
		if (location.getAccuracy() > MinLocationAccuracy)
			return;
		super.onLocationChanged(location);

		if (isMyLocationEnabled()) {
			try {
				mapView.getController().animateTo(getMyLocation());
			} catch (Throwable t) {
			}
		}
	}
}
