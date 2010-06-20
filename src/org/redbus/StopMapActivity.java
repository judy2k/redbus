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
import java.util.List;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.location.Address;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class StopMapActivity extends MapActivity implements GeocodingResponseListener  {

	private MapView mapView;
	private MapController mapController;
	private MyLocationOverlay myLocationOverlay;
	private StopOverlay stopOverlay;

	private ProgressDialog busyDialog = null;
	private int expectedRequestId = -1;

	public class StopOverlay extends Overlay {

		private static final int stopRadius = 5;

		// state remembered from previous draw() calls
		GeoPoint oldtl;
		GeoPoint oldbr;
		GeoPoint oldbl;
		GeoPoint oldtr;		
		private float oldProjectionCheck = -1;
		
		// used during recursive drawStops() to control stack allocation size
		private boolean drawGray;
		private boolean showServiceLabels;
		private Canvas bitmapGreyCanvas;
		private Canvas bitmapRedCanvas;
		private Projection projection;
		private PointTree pointTree;
		private Point stopCircle = new Point();

		private BusServiceMap serviceFilter = new BusServiceMap();
		
		private Paint blackBrush;
		private Bitmap normalStopBitmap;
		private Paint normalStopPaint;
		private Bitmap filteredStopBitmap;
		private Paint filteredStopPaint;
		
		private Bitmap showMoreStopsBitmap;
		private Bitmap showServicesBitmap;
		
		// the double buffering buffers
		private Bitmap bitmapBufferGrey1;
		private Bitmap bitmapBufferRed1;
		private Bitmap bitmapBufferGrey2;
		private Bitmap bitmapBufferRed2;
		private Bitmap oldBitmapGreyBuffer;
		private Bitmap oldBitmapRedBuffer;

		private static final String showMoreStopsText = "Zoom in to see more stops";
		private static final String showMoreServicesText = "Zoom in to see services";

		public StopOverlay(MapView view) {
			blackBrush = new Paint();
			blackBrush.setARGB(180,0,0,0);

			normalStopPaint = new Paint();
			normalStopPaint.setARGB(250, 187, 39, 66); // rEdB[r]us[h] ;-)
			normalStopPaint.setAntiAlias(true);
			normalStopBitmap = Bitmap.createBitmap(stopRadius * 2, stopRadius * 2, Config.ARGB_8888);
			normalStopBitmap.eraseColor(Color.TRANSPARENT);
			Canvas stopCanvas = new Canvas(normalStopBitmap);
			stopCanvas.drawOval(new RectF(0,0,stopRadius*2,stopRadius*2), normalStopPaint);
			
			filteredStopPaint = new Paint();
			filteredStopPaint.setARGB(250, 195, 195, 195);
			filteredStopPaint.setAntiAlias(true);
			filteredStopBitmap = Bitmap.createBitmap(stopRadius*2, stopRadius*2, Config.ARGB_8888);
			filteredStopBitmap.eraseColor(Color.TRANSPARENT);
			stopCanvas = new Canvas(filteredStopBitmap);
			stopCanvas.drawOval(new RectF(0, 0, stopRadius*2, stopRadius*2), filteredStopPaint);

			Rect bounds = new Rect();
			normalStopPaint.getTextBounds(showMoreStopsText, 0, showMoreStopsText.length(), bounds);
			showMoreStopsBitmap = Bitmap.createBitmap(bounds.right + 20, Math.abs(bounds.bottom) + Math.abs(bounds.top) + 20, Config.ARGB_8888);
			showMoreStopsBitmap.eraseColor(blackBrush.getColor());
			Canvas tmpCanvas = new Canvas(showMoreStopsBitmap);
			tmpCanvas.drawText(showMoreStopsText, 10, Math.abs(bounds.top) + 10, normalStopPaint);
			
			normalStopPaint.getTextBounds(showMoreServicesText, 0, showMoreServicesText.length(), bounds);
			showServicesBitmap = Bitmap.createBitmap(bounds.right + 20, Math.abs(bounds.bottom) + Math.abs(bounds.top) + 20, Config.ARGB_8888);
			showServicesBitmap.eraseColor(blackBrush.getColor());
			tmpCanvas = new Canvas(showServicesBitmap);
			tmpCanvas.drawText(showMoreServicesText, 10, Math.abs(bounds.top) + 10, normalStopPaint);
		}

		public void draw(Canvas canvas, MapView view, boolean shadow) {
			super.draw(canvas, view,shadow);

			if (shadow)
				return;

			// create the bitmaps now we know the size of what we're drawing into!
			if (bitmapBufferRed1 == null) {
				bitmapBufferGrey1 = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
				bitmapBufferRed1 = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
				bitmapBufferGrey2 = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
				bitmapBufferRed2 = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
			}

			// get other necessaries
			this.pointTree = PointTree.getPointTree(StopMapActivity.this);
			this.projection = view.getProjection();
			this.showServiceLabels = view.getZoomLevel() > 16;				
			boolean hasSomeGrey = !serviceFilter.areAllSet;
			int canvasWidth = canvas.getWidth();
			int canvasHeight = canvas.getHeight();
			GeoPoint tl = projection.fromPixels(0, canvasHeight);
			GeoPoint br = projection.fromPixels(canvasWidth, 0);
			GeoPoint bl = projection.fromPixels(0,0);

			// figure out which is the current buffer
			Bitmap curBitmapGreyBuffer = bitmapBufferGrey1;
			Bitmap curBitmapRedBuffer = bitmapBufferRed1;
			if (oldBitmapRedBuffer == bitmapBufferRed1) {
				curBitmapGreyBuffer = bitmapBufferGrey2;	
				curBitmapRedBuffer = bitmapBufferRed2;	
			}
			if (hasSomeGrey) {
				bitmapGreyCanvas = new Canvas(curBitmapGreyBuffer);
				curBitmapGreyBuffer.eraseColor(Color.TRANSPARENT);
			}
			bitmapRedCanvas = new Canvas(curBitmapRedBuffer);
			curBitmapRedBuffer.eraseColor(Color.TRANSPARENT);

			// check if the projection has radically changed
			float projectionCheck = projection.metersToEquatorPixels(20);
			if (projectionCheck != oldProjectionCheck) {
				oldBitmapGreyBuffer = null;
				oldBitmapRedBuffer = null;
			}
			oldProjectionCheck = projectionCheck;
			
			// if we're showing service labels, just draw directly onto the supplied canvas
			if (showServiceLabels) {
				this.bitmapGreyCanvas = canvas;
				this.bitmapRedCanvas = canvas;

				if (hasSomeGrey) {
					drawGray = true;
					drawStops(tl.getLatitudeE6(), tl.getLongitudeE6(), br.getLatitudeE6(), br.getLongitudeE6(), pointTree.rootRecordNum, 0);
				}
				drawGray = false;
				drawStops(tl.getLatitudeE6(), tl.getLongitudeE6(), br.getLatitudeE6(), br.getLongitudeE6(), pointTree.rootRecordNum, 0);
				return;
			}

			// draw the old bitmap onto the new one in the right place
			if (oldBitmapRedBuffer != null) {
				Point oldBlPix = projection.toPixels(oldbl, null);
				if (hasSomeGrey)
					this.bitmapGreyCanvas.drawBitmap(oldBitmapGreyBuffer, oldBlPix.x, oldBlPix.y, null);
				this.bitmapRedCanvas.drawBitmap(oldBitmapRedBuffer, oldBlPix.x, oldBlPix.y, null);
			}
			
			// draw!
			if (oldBitmapRedBuffer == null) {
				if (hasSomeGrey) {
					drawGray = true;
					drawStops(tl.getLatitudeE6(), tl.getLongitudeE6(), br.getLatitudeE6(), br.getLongitudeE6(), pointTree.rootRecordNum, 0);
				}
				drawGray = false;
				drawStops(tl.getLatitudeE6(), tl.getLongitudeE6(), br.getLatitudeE6(), br.getLongitudeE6(), pointTree.rootRecordNum, 0);
			} else {
				Point oldTlPix = projection.toPixels(oldtl, null);
				Point oldBrPix = projection.toPixels(oldbr, null);

				// handle latitude changes
				if (oldTlPix.x > 0) { // moving to the left
					int x = oldTlPix.x;
					if (x > canvasWidth)
						x = canvasWidth;
					x += stopRadius;

					GeoPoint _tl = projection.fromPixels(-stopRadius, canvasHeight);
					GeoPoint _br = projection.fromPixels(x, 0);

					if (hasSomeGrey) {
						drawGray = true;
						drawStops(_tl.getLatitudeE6(), _tl.getLongitudeE6(), _br.getLatitudeE6(), _br.getLongitudeE6(), pointTree.rootRecordNum, 0);
					}
					drawGray = false;
					drawStops(_tl.getLatitudeE6(), _tl.getLongitudeE6(), _br.getLatitudeE6(), _br.getLongitudeE6(), pointTree.rootRecordNum, 0);
				} else if (oldBrPix.x < canvasWidth) { // moving to the right
					int x = oldBrPix.x;
					if (x < 0)
						x = 0;
					x -= stopRadius;

					GeoPoint _tl = projection.fromPixels(x, canvasHeight);
					GeoPoint _br = projection.fromPixels(canvasWidth + stopRadius, 0);
					if (hasSomeGrey) {
						drawGray = true;
						drawStops(_tl.getLatitudeE6(), _tl.getLongitudeE6(), _br.getLatitudeE6(), _br.getLongitudeE6(), pointTree.rootRecordNum, 0);
					}
					drawGray = false;
					drawStops(_tl.getLatitudeE6(), _tl.getLongitudeE6(), _br.getLatitudeE6(), _br.getLongitudeE6(), pointTree.rootRecordNum, 0);
				}

				// FIXME: can also skip drawing the overlapped X area!
				
				// handle longitude changes
				if (oldBrPix.y > 0) { // moving down
					int y = oldBrPix.y;
					if (y > canvasHeight)
						y = canvasHeight;
					y += stopRadius;

					GeoPoint _tl = projection.fromPixels(0, y);
					GeoPoint _br = projection.fromPixels(canvasWidth + stopRadius, 0);
					if (hasSomeGrey) {
						drawGray = true;
						drawStops(_tl.getLatitudeE6(), _tl.getLongitudeE6(), _br.getLatitudeE6(), _br.getLongitudeE6(), pointTree.rootRecordNum, 0);
					}
					drawGray = false;
					drawStops(_tl.getLatitudeE6(), _tl.getLongitudeE6(), _br.getLatitudeE6(), _br.getLongitudeE6(), pointTree.rootRecordNum, 0);
				} else if (oldTlPix.y < canvasHeight) { // moving up
					int y = oldTlPix.y;
					if (y < 0)
						y = 0;
					y -= stopRadius;

					GeoPoint _tl = projection.fromPixels(-stopRadius, canvasHeight);
					GeoPoint _br = projection.fromPixels(canvasWidth, y);

					if (hasSomeGrey) {
						drawGray = true;
						drawStops(_tl.getLatitudeE6(), _tl.getLongitudeE6(), _br.getLatitudeE6(), _br.getLongitudeE6(), pointTree.rootRecordNum, 0);
					}
					drawGray = false;
					drawStops(_tl.getLatitudeE6(), _tl.getLongitudeE6(), _br.getLatitudeE6(), _br.getLongitudeE6(), pointTree.rootRecordNum, 0);
				}
			}

			// blit the final bitmap onto the destination canvas
			if (hasSomeGrey)
				canvas.drawBitmap(curBitmapGreyBuffer, 0, 0, null);
			canvas.drawBitmap(curBitmapRedBuffer, 0, 0, null);
			oldBitmapGreyBuffer = curBitmapGreyBuffer;
			oldBitmapRedBuffer = curBitmapRedBuffer;
			oldtl = tl;
			oldbr = br;
			oldbl = bl;
			
			// draw service label info text last
			if (!showServiceLabels)
				canvas.drawBitmap(showServicesBitmap, 0, 0, null);

			
			/*

			// if we're zoomed out too far, switch to just iterating all stops and skipping to preserve speed.
			if (view.getZoomLevel() < 15) {
				int skip = 2;
				if (view.getZoomLevel() == 13)
					skip = 4;
				if (view.getZoomLevel() == 12)
					skip = 6;
				if (view.getZoomLevel() < 12)
					skip = 8;

				Point stopCircle = new Point();
				int redIdx = 0;
				int greyIdx = 0;
				int redSkip = skip;
				int greySkip = skip;
				final int stopCount = pt.lat.length;
				for(int stopNodeIdx = 0; stopNodeIdx < stopCount; stopNodeIdx++) {
					boolean validServices = ((pt.serviceMap0[stopNodeIdx] & serviceFilter.bits0) != 0) ||
											((pt.serviceMap1[stopNodeIdx] & serviceFilter.bits1) != 0);

					Bitmap bmp = normalStopBitmap;
					if (validServices) {
						if (shadow)
							continue;

						if ((redIdx++ % redSkip) != 0)
							continue;
					} else {
						if (!shadow)
							continue;

						if ((greyIdx++ % greySkip) != 0)
							continue;

						bmp = filteredStopBitmap;
					}
					
					int lat = pt.lat[stopNodeIdx];
					int lon = pt.lon[stopNodeIdx];
					if ((lat < tlx) || (lon < tly) || (lat > brx) || (lon > bry))
						continue;

					projection.toPixels(new GeoPoint(lat, lon), stopCircle);
					canvas.drawBitmap(bmp, (float) stopCircle.x - stopRadius, (float) stopCircle.y - stopRadius, null);
				}
				
				if (!shadow)
					canvas.drawBitmap(showMoreStopsBitmap, 0, 0, nullPaint);
				return;
			}

			// For some reason, draw is called LOTS of times. Only requery the DB if
			// the co-ords change, OR if the point tree itself has changed
			if ((pt != cachedPt) || (tlx != oldtlx) || (tly != oldtly) || (brx != oldbrx) || (bry != oldbry)) {
				cachedStopNodeIdxs = pt.findRect(tlx,tly,brx,bry);
				cachedPt = pt;
				oldtlx = tlx; oldtly = tly; oldbrx = brx; oldbry = bry;
			}

			// Prevent zoomed out view looking like abstract art
			// with too many labels drawn...				

			// For each node, draw a circle and optionally service number list
			Point stopCircle = new Point();
			for (int stopNodeIdx: cachedStopNodeIdxs) {
				boolean validServices = ((pt.serviceMap0[stopNodeIdx] & serviceFilter.bits0) != 0) ||
										((pt.serviceMap1[stopNodeIdx] & serviceFilter.bits1) != 0);
				
				Bitmap bmp = normalStopBitmap;
				boolean showService = showServiceLabels;
				if (validServices) {
					if (shadow)
						continue;
				} else {
					if (!shadow)
						continue;

					bmp = filteredStopBitmap;
					showService = false;
				}

				projection.toPixels(new GeoPoint(pt.lat[stopNodeIdx],pt.lon[stopNodeIdx]), stopCircle);				
				canvas.drawBitmap(bmp, (float) stopCircle.x - stopRadius, (float) stopCircle.y - stopRadius, null);
				if (showService) {
					BusServiceMap nodeServiceMap = pt.lookupServiceMapByStopNodeIdx(stopNodeIdx);
					canvas.drawText(formatServices(pt, nodeServiceMap.andWith(serviceFilter), 3), stopCircle.x+stopRadius, stopCircle.y+stopRadius, normalStopPaint);
				}
			}

			// draw service label info text last
			if ((!showServiceLabels) && (!shadow))
				canvas.drawBitmap(showServicesBitmap, 0, 0, null);
			*/
		}
		
		private void drawStops(int lat_tl, int lon_tl, int lat_br, int lon_br, int stopNodeIdx, int depth) {
			if (stopNodeIdx==-1) 
				return;
			
			int tl, br, here, lat, lon;
			
			lat=pointTree.lat[stopNodeIdx];
			lon=pointTree.lon[stopNodeIdx];
			
			if (depth % 2 == 0) {
				here = lat;
				tl = lat_tl;
				br = lat_br;
			}
			else {
				here = lon;
				tl = lon_tl;
				br = lon_br;
			}
			
			if (tl > br) {
				Log.println(Log.ERROR,"redbus", "co-ord error!");
			}
			
			if (br > here)
				drawStops(lat_tl,lon_tl,lat_br,lon_br,pointTree.right[stopNodeIdx],depth+1);
			
			if (tl < here)
				drawStops(lat_tl,lon_tl,lat_br,lon_br,pointTree.left[stopNodeIdx],depth+1);
			
			// If this node falls within range, add it
			if (lat_tl <= lat && lat_br >= lat && lon_tl <= lon && lon_br >= lon) {
				boolean validServices = ((pointTree.serviceMap0[stopNodeIdx] & serviceFilter.bits0) != 0) ||
										((pointTree.serviceMap1[stopNodeIdx] & serviceFilter.bits1) != 0);
				
				Bitmap bmp = normalStopBitmap;
				Canvas canvas = bitmapRedCanvas;
				boolean showService = showServiceLabels;
				if (validServices) {
					if (drawGray)
						return;
				} else {
					if (!drawGray)
						return;
				
					bmp = filteredStopBitmap;
					showService = false;
					canvas = bitmapGreyCanvas;
				}
				
				projection.toPixels(new GeoPoint(lat, lon), stopCircle);				
				canvas.drawBitmap(bmp, (float) stopCircle.x - stopRadius, (float) stopCircle.y - stopRadius, null);
				if (showService) {
					BusServiceMap nodeServiceMap = pointTree.lookupServiceMapByStopNodeIdx(stopNodeIdx);
					canvas.drawText(formatServices(pointTree, nodeServiceMap.andWith(serviceFilter), 3), stopCircle.x+stopRadius, stopCircle.y+stopRadius, normalStopPaint);
				}
			}
		}
		
		@Override
		public boolean onTap(GeoPoint point, MapView mapView)
		{
			PointTree pt = PointTree.getPointTree(StopMapActivity.this);
			final int nearestStopNodeIdx = pt.findNearest(point.getLatitudeE6(), point.getLongitudeE6());
			final int stopCode = pt.lookupStopCodeByStopNodeIdx(nearestStopNodeIdx);
			final double stopLat = pt.lat[nearestStopNodeIdx] / 1E6;
			final double stopLon = pt.lon[nearestStopNodeIdx] / 1E6;
			final BusServiceMap nodeServiceMap = pt.lookupServiceMapByStopNodeIdx(nearestStopNodeIdx);

			// Yuk - there must be a better way to convert GeoPoint->Point than this?			
			Location touchLoc = new Location("");
			touchLoc.setLatitude(point.getLatitudeE6() / 1E6);
			touchLoc.setLongitude(point.getLongitudeE6() / 1E6);

			Location stopLoc = new Location("");
			stopLoc.setLatitude(stopLat);
			stopLoc.setLongitude(stopLon);

			// Use distance of 50metres - ignore out of range touches
			if (touchLoc.distanceTo(stopLoc) < 50) {
				View v = StopMapActivity.this.getLayoutInflater().inflate(R.layout.stoppopup, null);
				
				final AlertDialog d = new AlertDialog.Builder(StopMapActivity.this).
	    			setTitle(pt.lookupStopNameByStopNodeIdx(nearestStopNodeIdx) + " (" + stopCode + ")").
	    			setView(v).
	    			create();
	
				((TextView) v.findViewById(R.id.stoppopup_services)).setText("Services from this stop:\n" + formatServices(pt, nodeServiceMap, -1));
				((Button) v.findViewById(R.id.stoppopup_streetview)).setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
			            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("google.streetview:cbll=" + stopLat + "," + stopLon + "&cbp=1,180,,0,2.0")));
					}
				});
				((Button) v.findViewById(R.id.stoppopup_filter)).setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						stopOverlay.serviceFilter.setTo(nodeServiceMap);
						d.dismiss();
						// Zoom out map to show a larger part of the city
						mapController.setZoom(12);
						StopMapActivity.this.invalidate();
					}
				});
				((Button) v.findViewById(R.id.stoppopup_viewtimes)).setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						BusTimesActivity.showActivity(StopMapActivity.this, stopCode);
					}
				});
				((Button) v.findViewById(R.id.stoppopup_cancel)).setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						d.dismiss();
					}
				});
				d.show();
				return true;
			}
			return false;
		}
		
		@Override
		public boolean onTouchEvent(MotionEvent e, MapView mapView) {
			// disable my location if user drags the map
			if (e.getAction() == MotionEvent.ACTION_MOVE)
				myLocationOverlay.disableMyLocation();
				
			return super.onTouchEvent(e, mapView);
		}
	}
	
	public String formatServices(PointTree pt, BusServiceMap servicesMap, int maxServices)
	{
		ArrayList<String> services = pt.getServiceNames(servicesMap);

		// Where is string.join()?
		StringBuilder sb = new StringBuilder();
		for(int j = 0; j < services.size(); j++) {
			if ((maxServices != -1) && (j >= maxServices)) {
				sb.append("...");
				break;
			}
			sb.append(services.get(j));
			sb.append(" ");
		}	
		
		return sb.toString();
	}
	
	public static void showActivity(Context context) {
		Intent i = new Intent(context, StopMapActivity.class);
		context.startActivity(i);
	}
	
	public static void showActivity(Context context, 
			int lat,
			int lng) {
		Intent i = new Intent(context, StopMapActivity.class);
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
		int lat = getIntent().getIntExtra("Lat", -1);
		int lng = getIntent().getIntExtra("Lng", -1);
		
		// Not been passed a location, so use GPS and default to centre
		if (lat == -1 && lng == -1) {
			// Default map to centre of Edinburgh
			lat = 55946052;
			lng = -3188879;
			
			myLocationOverlay.runOnFirstFix(new Runnable() {
				public void run() {
					mapController.animateTo(myLocationOverlay.getMyLocation());
				}
			});
			myLocationOverlay.enableMyLocation();
		} else {
			myLocationOverlay.disableMyLocation();
		}

		stopOverlay = new StopOverlay(mapView);
		mapView.getOverlays().add(stopOverlay);
		mapController.setCenter(new GeoPoint(lat, lng));
	}
	
	public void invalidate()
	{
		this.stopOverlay.oldBitmapGreyBuffer = null;
		this.stopOverlay.oldBitmapRedBuffer = null;
		this.mapView.invalidate();
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
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.stopmap_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (mapView.isSatellite())
			menu.findItem(R.id.stopmap_menu_satellite_or_map).setTitle("Map View");
		else
			menu.findItem(R.id.stopmap_menu_satellite_or_map).setTitle("Satellite View");		
		
		if (stopOverlay.serviceFilter.areAllSet)
			menu.findItem(R.id.stopmap_menu_showall).setEnabled(false);
		else
			menu.findItem(R.id.stopmap_menu_showall).setEnabled(true);
		
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.stopmap_menu_search: {
			final EditText input = new EditText(this);
			new AlertDialog.Builder(this)
				.setTitle("Enter a location or postcode")
				.setView(input)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								displayBusy("Finding location...");
								StopMapActivity.this.expectedRequestId = GeocodingHelper.geocode(StopMapActivity.this, input.getText().toString(), StopMapActivity.this);
							}
						})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
			return true;				
		}

		case R.id.stopmap_menu_showall:
			stopOverlay.serviceFilter.setAll();
			StopMapActivity.this.invalidate();
			return true;

		case R.id.stopmap_menu_filterservices: {
			final EditText input = new EditText(this);

			new AlertDialog.Builder(this)
				.setTitle("Enter services separated by spaces")
				.setView(input)
				.setPositiveButton(android.R.string.ok, 
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							BusServiceMap serviceFilter = new BusServiceMap().clearAll();
							PointTree pt = PointTree.getPointTree(StopMapActivity.this);
							for(String serviceStr: input.getText().toString().split("[ ]+")) {
								if (pt.serviceNameToServiceBit.containsKey(serviceStr.toUpperCase()))
									serviceFilter.setBit(pt.serviceNameToServiceBit.get(serviceStr.toUpperCase()));
							}
							stopOverlay.serviceFilter.setTo(serviceFilter);
							// Zoom out map to show a larger part of the city
							mapController.setZoom(12);
							StopMapActivity.this.invalidate();
						}
					})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
			return true;
		}

		case R.id.stopmap_menu_satellite_or_map:
			mapView.setSatellite(!mapView.isSatellite());
			return true;
			
		case R.id.stopmap_menu_mylocation:
			myLocationOverlay.enableMyLocation();
			if (myLocationOverlay.getMyLocation() != null)
				mapController.animateTo(myLocationOverlay.getMyLocation());
			return true;
		}
		
		return false;
	}
	
	@Override
	protected void onDestroy() {
		busyDialog = null;
		super.onDestroy();
	}

	private void displayBusy(String reason) {
		dismissBusy();

		busyDialog = ProgressDialog.show(this, "", reason, true, true, new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				StopMapActivity.this.expectedRequestId = -1;
			}
		});
	}

	private void dismissBusy() {
		if (busyDialog != null) {
			try {
				busyDialog.dismiss();
			} catch (Throwable t) {
			}
			busyDialog = null;
		}
	}

	public void geocodeResponseError(int requestId, String message) {
		if (requestId != expectedRequestId)
			return;
		
		dismissBusy();
		
		new AlertDialog.Builder(this).setTitle("Error").
			setMessage("Unable to find location: " + message).
			setPositiveButton(android.R.string.ok, null).
			show();
	}

	public void geocodeResponseSucccess(int requestId, List<Address> addresses_) {
		if (requestId != expectedRequestId)
			return;
		
		dismissBusy();
		if (addresses_.size() == 1) {
			Address address = addresses_.get(0);
			GeoPoint gp = new GeoPoint((int) (address.getLatitude() * 1E6), (int) (address.getLongitude() * 1E6));
			mapController.animateTo(gp);
			return;
		}
		
		final List<Address> addresses = addresses_;
		ArrayList<String> addressNames = new ArrayList<String>();
		for(Address a: addresses) {
			StringBuilder strb = new StringBuilder();
			for(int i =0; i< a.getMaxAddressLineIndex(); i++) {
				if (i > 0)
					strb.append(", ");
				strb.append(a.getAddressLine(i));
			}
			addressNames.add(strb.toString());
		}

		new AlertDialog.Builder(this)
  	       .setSingleChoiceItems(addressNames.toArray(new String[addressNames.size()]), -1, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					if (which < 0)
						return;
					
					Address address = addresses.get(which);
					GeoPoint gp = new GeoPoint((int) (address.getLatitude() * 1E6), (int) (address.getLongitude() * 1E6));
					mapController.animateTo(gp);
					dialog.dismiss();
				}
  	       })
  	       .show();
	}
}
