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

import org.redbus.stopdb.ServiceBitmap;
import org.redbus.stopdb.StopDbHelper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.util.Log;
import android.view.MotionEvent;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class StopMapOverlay extends Overlay {

	private static final int stopRadius = 5;
	private static final String showMoreStopsText = "Zoom in to see more stops";
	private static final String showMoreServicesText = "Zoom in to see services";

	private StopMapActivity stopMapActivity;
	private ServiceBitmap serviceFilter;

	// state remembered from previous draw() calls
	GeoPoint oldtl;
	GeoPoint oldbr;
	GeoPoint oldbl;
	GeoPoint oldtr;		
	private float oldProjectionCheck = -1;
	
	// used during recursive drawStops() to control stack allocation size
	private boolean showServiceLabels;
	private Canvas bitmapRedCanvas;
	private Projection projection;
	private StopDbHelper pointTree;
	private Point stopCircle = new Point();
	private int lat_tl;
	private int lon_tl;
	private int lat_br;
	private int lon_br;
	
	private Paint blackBrush;
	private Bitmap normalStopBitmap;
	private Paint normalStopPaint;
	
	private Bitmap showMoreStopsBitmap;
	private Bitmap showServicesBitmap;
	
	// the double buffering buffers
	private Bitmap bitmapBufferRed1;
	private Bitmap bitmapBufferRed2;
	private Bitmap oldBitmapRedBuffer;

	public StopMapOverlay(StopMapActivity stopMapActivity) {
		this.stopMapActivity = stopMapActivity;
		this.serviceFilter = stopMapActivity.getServiceFilter();
		
		blackBrush = new Paint();
		blackBrush.setARGB(180,0,0,0);

		normalStopPaint = new Paint();
		normalStopPaint.setARGB(250, 187, 39, 66); // rEdB[r]us[h] ;-)
		normalStopPaint.setAntiAlias(true);
		normalStopBitmap = Bitmap.createBitmap(stopRadius * 2, stopRadius * 2, Config.ARGB_8888);
		normalStopBitmap.eraseColor(Color.TRANSPARENT);
		Canvas stopCanvas = new Canvas(normalStopBitmap);
		stopCanvas.drawOval(new RectF(0,0,stopRadius*2,stopRadius*2), normalStopPaint);

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
	
	public void invalidate() {
		oldBitmapRedBuffer = null;
	}

	public void draw(Canvas canvas, MapView view, boolean shadow) {
		super.draw(canvas, view,shadow);

		if (shadow)
			return;

		// create the bitmaps now we know the size of what we're drawing into!
		if (bitmapBufferRed1 == null) {
			bitmapBufferRed1 = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
			bitmapBufferRed2 = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
		}

		// get other necessaries
		this.pointTree = StopDbHelper.Load(stopMapActivity);
		this.projection = view.getProjection();
		this.showServiceLabels = view.getZoomLevel() > 16;				
		int canvasWidth = canvas.getWidth();
		int canvasHeight = canvas.getHeight();
		GeoPoint tl = projection.fromPixels(0, canvasHeight);
		GeoPoint br = projection.fromPixels(canvasWidth, 0);
		GeoPoint bl = projection.fromPixels(0,0);

		// figure out which is the current buffer
		Bitmap curBitmapRedBuffer = bitmapBufferRed1;
		if (oldBitmapRedBuffer == bitmapBufferRed1)
			curBitmapRedBuffer = bitmapBufferRed2;	
		bitmapRedCanvas = new Canvas(curBitmapRedBuffer);
		curBitmapRedBuffer.eraseColor(Color.TRANSPARENT);

		// check if the projection has radically changed
		float projectionCheck = projection.metersToEquatorPixels(20);
		if (projectionCheck != oldProjectionCheck)
			oldBitmapRedBuffer = null;
		oldProjectionCheck = projectionCheck;
		
		// if we're showing service labels, just draw directly onto the supplied canvas
		if (showServiceLabels) {
			this.bitmapRedCanvas = canvas;
			drawStops(tl, br);
			return;
		}

		// draw the old bitmap onto the new one in the right place
		if (oldBitmapRedBuffer != null) {
			Point oldBlPix = projection.toPixels(oldbl, null);
			this.bitmapRedCanvas.drawBitmap(oldBitmapRedBuffer, oldBlPix.x, oldBlPix.y, null);
		}
		
		// draw!
		if (oldBitmapRedBuffer == null) {
			drawStops(tl, br);
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
				drawStops(_tl, _br);
			} else if (oldBrPix.x < canvasWidth) { // moving to the right
				int x = oldBrPix.x;
				if (x < 0)
					x = 0;
				x -= stopRadius;

				GeoPoint _tl = projection.fromPixels(x, canvasHeight);
				GeoPoint _br = projection.fromPixels(canvasWidth + stopRadius, 0);
				drawStops(_tl, _br);
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
				drawStops(_tl, _br);
			} else if (oldTlPix.y < canvasHeight) { // moving up
				int y = oldTlPix.y;
				if (y < 0)
					y = 0;
				y -= stopRadius;

				GeoPoint _tl = projection.fromPixels(-stopRadius, canvasHeight);
				GeoPoint _br = projection.fromPixels(canvasWidth, y);
				drawStops(_tl, _br);
			}
		}

		// blit the final bitmap onto the destination canvas
		canvas.drawBitmap(curBitmapRedBuffer, 0, 0, null);
		oldBitmapRedBuffer = curBitmapRedBuffer;
		oldtl = tl;
		oldbr = br;
		oldbl = bl;
		
		// draw service label info text last
		if (!showServiceLabels)
			canvas.drawBitmap(showServicesBitmap, 0, 0, null);
	}
	
	private void drawStops(GeoPoint tl, GeoPoint br) {
		this.lat_tl = tl.getLatitudeE6();
		this.lon_tl = tl.getLongitudeE6();
		this.lat_br = br.getLatitudeE6();
		this.lon_br = br.getLongitudeE6();
		drawStops(pointTree.rootRecordNum, 0);
	}
	
	private void drawStops(int stopNodeIdx, int depth) {
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
			drawStops(pointTree.right[stopNodeIdx],depth+1);
		
		if (tl < here)
			drawStops(pointTree.left[stopNodeIdx],depth+1);
		
		// If this node falls within range, add it
		if (lat_tl <= lat && lat_br >= lat && lon_tl <= lon && lon_br >= lon) {
			boolean validServices = ((pointTree.serviceMap0[stopNodeIdx] & serviceFilter.bits0) != 0) ||
									((pointTree.serviceMap1[stopNodeIdx] & serviceFilter.bits1) != 0);
			
			Bitmap bmp = normalStopBitmap;
			Canvas canvas = bitmapRedCanvas;
			if (validServices) {				
				projection.toPixels(new GeoPoint(lat, lon), stopCircle);				
				canvas.drawBitmap(bmp, (float) stopCircle.x - stopRadius, (float) stopCircle.y - stopRadius, null);
				if (showServiceLabels) {
					ServiceBitmap nodeServiceMap = pointTree.lookupServiceBitmapByStopNodeIdx(stopNodeIdx);
					canvas.drawText(pointTree.formatServices(nodeServiceMap.andWith(serviceFilter), 3), stopCircle.x+stopRadius, stopCircle.y+stopRadius, normalStopPaint);
				}
			}
		}
	}
	
	@Override
	public boolean onTap(GeoPoint point, MapView mapView)
	{
		return this.stopMapActivity.onStopMapTap(point, mapView);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent e, MapView mapView) {
		this.stopMapActivity.onStopMapTouchEvent(e, mapView);
		return super.onTouchEvent(e, mapView);
	}
}
