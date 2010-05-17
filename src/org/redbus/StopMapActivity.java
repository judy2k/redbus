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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.location.Location;
import android.os.Bundle;
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
		private Paint brush;
		private Paint blackBrush;

		private ArrayList<BusStopTreeNode> nodes;
		private boolean showServiceLabels;
		private int numberOfStops;

		private double tlx, oldtlx;
		private double tly, oldtly;
		private double brx, oldbrx;
		private double bry, oldbry;
		
		public StopOverlay(MapView view) {
			this.busStopLocations = PointTree.getPointTree(StopMapActivity.this);			
			this.projection = view.getProjection();
			this.brush = new Paint();
			this.blackBrush = new Paint();
			brush.setARGB(250, 250, 0, 0); // rEdB[r]us[h] ;-)
			blackBrush.setARGB(200,0,0,0);
			brush.setAntiAlias(true);

			oldtlx = oldtly = oldbrx = oldbry = 0;
		}

		private String getServicesForStop(PointTree.BusStopTreeNode node) {
			ArrayList<String> services = busStopLocations.lookupServices(node.getServicesMap());

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

			GeoPoint tl = projection.fromPixels(0,canvas.getHeight());
			GeoPoint br = projection.fromPixels(canvas.getWidth(),0);

			tlx = tl.getLatitudeE6() / 1E6;
			tly = tl.getLongitudeE6() / 1E6;
			brx = br.getLatitudeE6() / 1E6;
			bry = br.getLongitudeE6() / 1E6;

			// For some reason, draw is called LOTS of times. Only requery the DB if
			// the co-ords change.
			if (tlx != oldtlx || tly != oldtly || brx != oldbrx || bry != oldbry) {
				oldtlx = tlx; oldtly = tly; oldbrx = brx; oldbry = bry;
				//Log.println(Log.DEBUG, "colin"," newpos "+tl.toString()+ " -> "+br.toString());

				nodes = busStopLocations.findRect(tlx,tly,brx,bry);

				// Prevent zoomed out view looking like abstract art
				// with too many labels drawn...
				numberOfStops = nodes.size();
				
				showServiceLabels = numberOfStops < 20;
			}

			if (shadow == true)
				return;
			
			// Prevent maps slowing down with too many stops
			if (numberOfStops >= 200)
			{
				canvas.drawRect(0, 0, 130, 30, blackBrush);
				canvas.drawText("Zoom in to see stops", 10, 15, brush);
				return;
			}

			// For each node, draw a circle and optionally service number list
			for (BusStopTreeNode node: nodes) {
				int radius = 5;

				double lat = node.getX() * 1E6;
				double lng = node.getY() * 1E6;
				Point stopCircle = new Point();
				projection.toPixels(new GeoPoint((int)lat,(int)lng), stopCircle);

				canvas.drawOval(new RectF(stopCircle.x-radius, stopCircle.y-radius,
						stopCircle.x+radius, stopCircle.y+radius), brush);

				if (showServiceLabels)
					canvas.drawText(getServicesForStop(node), stopCircle.x+radius, stopCircle.y+radius, brush);
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
			stopLoc.setLatitude(node.getX());
			stopLoc.setLongitude(node.getY());

			// Use distance of 50metres - ignore out of range touches
			if (touchLoc.distanceTo(stopLoc) < 50) {
				BusTimesActivity.showActivity(StopMapActivity.this, node.getStopCode(), node.getStopName());
				return true; // handled
			}

			return false; // Not handled
		}
	}

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

		mapView.getOverlays().add(new StopOverlay(mapView));
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
