package org.redbus;

import android.content.Context;
import android.graphics.Canvas;
import android.location.Location;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

public class WorkaroundMyLocationOverlay extends MyLocationOverlay {
	
	public WorkaroundMyLocationOverlay(Context ctx, MapView mapView) {
		super(ctx, mapView);
	}

	@Override
	protected void drawMyLocation(Canvas canvas, MapView mapView,
			Location lastFix, GeoPoint myLocation, long when) {
		try {
			super.drawMyLocation(canvas, mapView, lastFix, myLocation, when);
		} catch (Throwable t) {
			
		}
	}
}
