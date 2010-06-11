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

import java.util.ArrayList;
import org.redbus.PointTree.BusStopTreeNode;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.location.Location;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class StopMapActivity extends MapActivity {

	public class StopOverlay extends Overlay {
		private Projection projection;
		private PointTree busStopLocations;
		private Paint blackBrush;

		private ArrayList<BusStopTreeNode> nodes;
		private boolean showServiceLabels;

		private double tlx, oldtlx;
		private double tly, oldtly;
		private double brx, oldbrx;
		private double bry, oldbry;

		private long serviceFilter;
		private Bitmap normalStopBitmap;
		private Paint normalStopPaint;
		private Bitmap filteredStopBitmap;
		private Paint filteredStopPaint;
		private Paint nullPaint;
		private static final int stopRadius = 5;

		
		public StopOverlay(MapView view, long serviceFilter) {
			this.busStopLocations = PointTree.getPointTree(StopMapActivity.this);
			this.serviceFilter = serviceFilter;
			this.projection = view.getProjection();
			this.blackBrush = new Paint();
			blackBrush.setARGB(200,0,0,0);

			oldtlx = oldtly = oldbrx = oldbry = -1;

			normalStopPaint = new Paint();
			normalStopPaint.setARGB(250, 187, 39, 66); // rEdB[r]us[h] ;-)
			normalStopPaint.setAntiAlias(true);
			normalStopBitmap = Bitmap.createBitmap(stopRadius * 2, stopRadius * 2, Config.ARGB_4444);
			normalStopBitmap.eraseColor(Color.TRANSPARENT);
			Canvas stopCanvas = new Canvas(normalStopBitmap);
			stopCanvas.drawOval(new RectF(0,0,stopRadius*2,stopRadius*2), normalStopPaint);

			filteredStopPaint = new Paint();
			filteredStopPaint.setARGB(250, 195, 195, 195);
			filteredStopPaint.setAntiAlias(true);
			filteredStopBitmap = Bitmap.createBitmap(stopRadius*2, stopRadius*2, Config.ARGB_4444);
			filteredStopBitmap.eraseColor(Color.TRANSPARENT);
			stopCanvas = new Canvas(filteredStopBitmap);
			stopCanvas.drawOval(new RectF(0, 0, stopRadius*2, stopRadius*2), filteredStopPaint);

			nullPaint = new Paint();
		}

		private String getServicesForStop(PointTree.BusStopTreeNode node) {
			ArrayList<String> services = busStopLocations.lookupServices(node.servicesMap);

			// Where is string.join()?
			StringBuilder sb = new StringBuilder();
			for(String s: services) {
				sb.append(s);
				sb.append(" ");
			}
			return sb.toString();
		}

		public void draw(Canvas canvas, MapView view, boolean shadow) {
			super.draw(canvas, view,shadow);

			if ((shadow == true) && (serviceFilter == 0xffffffffffffL))
				return;
			
			GeoPoint tl = projection.fromPixels(0,canvas.getHeight());
			GeoPoint br = projection.fromPixels(canvas.getWidth(),0);
			tlx = tl.getLatitudeE6() / 1E6;
			tly = tl.getLongitudeE6() / 1E6;
			brx = br.getLatitudeE6() / 1E6;
			bry = br.getLongitudeE6() / 1E6;

			// if we're zoomed out too far, switch to just iterating all stops and skipping to preserve speed.
			if (view.getZoomLevel() < 15) {
				int skip = 1;
				if (view.getZoomLevel() < 14)
					skip = 2;
				if (view.getZoomLevel() < 13)
					skip = 4;
				if (view.getZoomLevel() < 12)
					skip = 8;

				Point stopCircle = new Point();
				int idx = 0;
				for (BusStopTreeNode node: busStopLocations.nodes) {
					if ((idx++ % skip) != 0)
						continue;
					if ((node.x < tlx) || (node.y < tly) || (node.x > brx) || (node.y > bry))
						continue;

					projection.toPixels(new GeoPoint((int)(node.x * 1E6),(int)(node.y * 1E6)), stopCircle);
					
					Bitmap bmp = normalStopBitmap;
					if ((serviceFilter & node.servicesMap) == 0) {
						if (!shadow)
							continue;
						bmp = filteredStopBitmap;
					}					
					canvas.drawBitmap(bmp, (float) stopCircle.x - stopRadius, (float) stopCircle.y - stopRadius, nullPaint);
				}  
				
				canvas.drawRect(0, 0, 170, 30, blackBrush);
				canvas.drawText("Zoom in to see more stops", 10, 15, normalStopPaint);
				return;
			}
			
			// For some reason, draw is called LOTS of times. Only requery the DB if
			// the co-ords change.
			if (tlx != oldtlx || tly != oldtly || brx != oldbrx || bry != oldbry) {
				oldtlx = tlx; oldtly = tly; oldbrx = brx; oldbry = bry;

				nodes = busStopLocations.findRect(tlx,tly,brx,bry);
				
				// Prevent zoomed out view looking like abstract art
				// with too many labels drawn...				
				showServiceLabels = view.getZoomLevel() > 16;
				
			}

			// For each node, draw a circle and optionally service number list
			Point stopCircle = new Point();
			for (BusStopTreeNode node: nodes) {
				projection.toPixels(new GeoPoint((int)(node.x * 1E6),(int)(node.y * 1E6)), stopCircle);

				Bitmap bmp = normalStopBitmap;
				boolean showService = true;
				if ((serviceFilter & node.servicesMap) == 0) {
					if (!shadow)
						continue;
					bmp = filteredStopBitmap;
					showService = false;
				}

				canvas.drawBitmap(bmp, (float) stopCircle.x - stopRadius, (float) stopCircle.y - stopRadius, nullPaint);
				if (showService)
					canvas.drawText(getServicesForStop(node), stopCircle.x+stopRadius, stopCircle.y+stopRadius, normalStopPaint);
			}  

			// draw service label info text last
			if (!showServiceLabels) {
				canvas.drawRect(0, 0, 150, 30, blackBrush);
				canvas.drawText("Zoom in to see services", 10, 15, normalStopPaint);
			}
		}
		
		
		@Override
		public boolean onTap(GeoPoint point, MapView mapView)
		{
			double lat = point.getLatitudeE6()/1E6;
			double lng = point.getLongitudeE6()/1E6;
			
			PointTree.BusStopTreeNode node = busStopLocations.findNearest(lat,lng);

			// Yuk - there must be a better way to convert GeoPoint->Point than this?			
			Location touchLoc = new Location("");
			touchLoc.setLatitude(lat);
			touchLoc.setLongitude(lng);

			Location stopLoc = new Location("");
			stopLoc.setLatitude(node.x);
			stopLoc.setLongitude(node.y);

			// Use distance of 50metres - ignore out of range touches
			if (touchLoc.distanceTo(stopLoc) < 50) {
				BusTimesActivity.showActivity(StopMapActivity.this, node.stopCode);
				return true; // handled
			}

			return false; // Not handled
		}
		
		@Override
		public boolean onTouchEvent(MotionEvent e, MapView mapView) {
			// disable my location if user drags the map
			if (e.getAction() == MotionEvent.ACTION_MOVE)
				myLocationOverlay.disableMyLocation();
				
			return super.onTouchEvent(e, mapView);
		}
	}

	private MapView mapView;
	private MapController mapController;
	private MyLocationOverlay myLocationOverlay;
	
	public static void showActivity(Context context) {
		Intent i = new Intent(context, StopMapActivity.class);
		context.startActivity(i);
	}
	
	public static void showActivity(Context context, 
			double lat,
			double lng) {
		Intent i = new Intent(context, StopMapActivity.class);
		i.putExtra("Lat", lat);
		i.putExtra("Lng", lng);
		context.startActivity(i);
	}
	
	public static void showActivityForServiceMap(Context context, 
			long serviceMap,
			double lat,
			double lng) {
		Intent i = new Intent(context, StopMapActivity.class);
		i.putExtra("StopFilter", serviceMap);
		i.putExtra("Lat", lat);
		i.putExtra("Lng", lng);
		context.startActivity(i);
	}
	
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
		mapView.getOverlays().add(myLocationOverlay);

		// Check to see if we've been passed data
		long stopFilter = getIntent().getLongExtra("StopFilter", 0xffffffff);
		double lat = getIntent().getDoubleExtra("Lat", -1);
		double lng = getIntent().getDoubleExtra("Lng", -1);
		
		// Not been passed a location, so use GPS and default to centre
		if (lat == -1 && lng == -1) {
			// Default map to centre of Edinburgh
			lat = 55.946052;
			lng = -3.188879;
			
			myLocationOverlay.runOnFirstFix(new Runnable() {
				public void run() {
					mapController.animateTo(myLocationOverlay.getMyLocation());
					
				}
			});
			myLocationOverlay.enableMyLocation();
		} else {
			myLocationOverlay.disableMyLocation();
		}
		
		mapView.getOverlays().add(new StopOverlay(mapView,stopFilter));
		mapController.setCenter(new GeoPoint((int)(lat*1E6),(int)(lng*1E6)));
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
		Toast.makeText(this, "Finding your location...", Toast.LENGTH_SHORT).show();
	}	
}
