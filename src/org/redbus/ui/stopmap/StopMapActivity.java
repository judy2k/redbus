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

import java.util.*;

import android.support.v4.app.FragmentActivity;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import org.redbus.R;
import org.redbus.geocode.GeocodingHelper;
import org.redbus.geocode.IGeocodingResponseListener;
import org.redbus.stopdb.ServiceBitmap;
import org.redbus.stopdb.StopDbHelper;
import org.redbus.ui.BusyDialog;
import org.redbus.ui.Common;
import org.redbus.ui.arrivaltime.ArrivalTimeActivity;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;

public class StopMapActivity extends FragmentActivity implements IGeocodingResponseListener, OnCancelListener,
        GoogleMap.OnCameraChangeListener {
    private static final String TAG = "StopMapActivity";

    private GoogleMap map;
	private ServiceBitmap serviceFilter = new ServiceBitmap();

	private BusyDialog busyDialog = null;
	private int expectedRequestId = -1;
	
	private boolean isFirstResume = true;

    private Map<Integer, Marker> visibleMarkers = new HashMap<Integer, Marker>();

    private StopDbHelper pointTree;

    private BitmapDescriptor unknownStopBitmap;
    private Map<Integer,BitmapDescriptor> compassBitmaps;

    public static void showActivity(Context context, int lat, int lng) {
		Intent i = new Intent(context, StopMapActivity.class);
		i.putExtra("Lat", lat);
		i.putExtra("Lng", lng);
		context.startActivity(i);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) { 
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stop_map);
		busyDialog = new BusyDialog(this);

        this.pointTree = StopDbHelper.Load(this);

        // Load in all the required BitmapDescriptors:
        unknownStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.stop_unknown);
        compassBitmaps = new HashMap<Integer, BitmapDescriptor>();
        compassBitmaps.put(StopDbHelper.STOP_FACING_N, BitmapDescriptorFactory.fromResource(R.drawable.compass_n));
        compassBitmaps.put(StopDbHelper.STOP_FACING_NE, BitmapDescriptorFactory.fromResource(R.drawable.compass_ne));
        compassBitmaps.put(StopDbHelper.STOP_FACING_E, BitmapDescriptorFactory.fromResource(R.drawable.compass_e));
        compassBitmaps.put(StopDbHelper.STOP_FACING_SE, BitmapDescriptorFactory.fromResource(R.drawable.compass_se));
        compassBitmaps.put(StopDbHelper.STOP_FACING_S, BitmapDescriptorFactory.fromResource(R.drawable.compass_s));
        compassBitmaps.put(StopDbHelper.STOP_FACING_SW, BitmapDescriptorFactory.fromResource(R.drawable.compass_sw));
        compassBitmaps.put(StopDbHelper.STOP_FACING_W, BitmapDescriptorFactory.fromResource(R.drawable.compass_w));
        compassBitmaps.put(StopDbHelper.STOP_FACING_NW, BitmapDescriptorFactory.fromResource(R.drawable.compass_nw));
        compassBitmaps.put(StopDbHelper.STOP_OUTOFORDER, BitmapDescriptorFactory.fromResource(R.drawable.stop_outoforder));
        compassBitmaps.put(StopDbHelper.STOP_DIVERTED, BitmapDescriptorFactory.fromResource(R.drawable.stop_diverted));
        compassBitmaps = Collections.unmodifiableMap(compassBitmaps);

        // Get a reference to the map:
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        map = mapFragment.getMap();
        if (map != null) {
            map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker) {
                    LatLng point = marker.getPosition();
                    final int nearestStopNodeIdx = pointTree.findNearest((int)(point.latitude * 1E6),
                            (int)(point.longitude * 1E6));
                    final int stopCode = pointTree.stopCode[nearestStopNodeIdx];
                    new StopMapPopup(StopMapActivity.this, stopCode);
                    return true;
                }
            });
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            map.setIndoorEnabled(false);
            map.setMyLocationEnabled(true);
            UiSettings mapSettings = map.getUiSettings();
            mapSettings.setCompassEnabled(false);
            mapSettings.setRotateGesturesEnabled(false);
            mapSettings.setTiltGesturesEnabled(false);


            // mapController.setZoom(17);

            // Check to see if we've been passed data
            int lat = getIntent().getIntExtra("Lat", -1);
            int lng = getIntent().getIntExtra("Lng", -1);

            if (lat == -1 || lng == -1) {
                Log.d(TAG, "Not supplied with either lat or lng");
                // if we don't have a location supplied, try and use the last known one.
                LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                Location gpsLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location networkLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if ((gpsLocation != null) && (gpsLocation.getAccuracy() < 100)) {
                    Log.d(TAG, "Using GPS for location. " + gpsLocation);

                    zoomTo(new LatLng(gpsLocation.getLatitude(), gpsLocation.getLongitude()));
                } else if ((networkLocation != null) && (networkLocation.getAccuracy() < 100)) {
                    Log.d(TAG, "Using network for location.");
                    zoomTo(new LatLng(networkLocation.getLatitude(), networkLocation.getLongitude()));

                } else {
                    Log.d(TAG, "Using default location from db.");
                    StopDbHelper stopDb = StopDbHelper.Load(this);
                    zoomTo(new LatLng(stopDb.defaultMapLocationLat / 1E6, stopDb.defaultMapLocationLon / 1E6));
                }
                updateMyLocationStatus(true);
            } else {
                Log.d(TAG, "Using supplied lat and lng.");
                zoomTo(new LatLng(lat / 1E6, lng / 1E6));
                updateMyLocationStatus(false);
            }
            map.setOnCameraChangeListener(this);
        }
	}

    @Override
    public void onCameraChange(CameraPosition pos) {
        updateMap();
    }

    private void updateMap() {
        LatLngBounds bounds = this.map.getProjection().getVisibleRegion().latLngBounds;

        // Build up a list of indexes to remove, to avoid concurrent
        // modification exceptions:
        List<Integer> toRemove = new ArrayList<Integer>();
        for (Map.Entry<Integer, Marker> entry : visibleMarkers.entrySet()) {
            Marker m = entry.getValue();
            if (!bounds.contains(m.getPosition())) {
                toRemove.add(entry.getKey());
            }
        }
        for (int idx : toRemove) {
            Marker m = visibleMarkers.get(idx);
            visibleMarkers.remove(idx);
            m.remove();
        }

        List<Integer> markers = pointTree.findRect(
                (int)(bounds.southwest.latitude * 1E6),
                (int)(bounds.southwest.longitude * 1E6),
                (int)(bounds.northeast.latitude * 1E6),
                (int)(bounds.northeast.longitude * 1E6));
        for (int stopNodeIdx : markers) {
            addMarker(stopNodeIdx);
        }
    }

    private void addMarker(int stopNodeIdx) {
        if (visibleMarkers.containsKey(stopNodeIdx)) {
            return;
        }

        boolean validServices = ((pointTree.serviceMap0[stopNodeIdx] & serviceFilter.bits0) != 0) ||
                ((pointTree.serviceMap1[stopNodeIdx] & serviceFilter.bits1) != 0);

        BitmapDescriptor bmp = compassBitmaps.get(Integer.valueOf(pointTree.facing[stopNodeIdx]));
        if (bmp == null) {
            bmp = unknownStopBitmap;
        }

        LatLng location = new LatLng(pointTree.lat[stopNodeIdx]/1E6, pointTree.lon[stopNodeIdx]/1E6);
        Marker m = map.addMarker(new MarkerOptions()
                .icon(bmp)
                .position(location)
                .title(pointTree.lookupStopNameByStopNodeIdx(stopNodeIdx))
                .anchor(0.5f, 0.5f)
        );
        Log.d(TAG, "Marker title: " + pointTree.lookupStopNameByStopNodeIdx(stopNodeIdx));
        visibleMarkers.put(stopNodeIdx, m);
    }
	
	private void invalidate()
	{
		// this.stopOverlay.invalidate();
		//this.mapView.invalidate();
	}

	@Override
	public void onPause() {
		updateMyLocationStatus(false);
		// stopOverlay.onPause();
		super.onPause();
	}

	@Override
	public void onResume() {
		if (!isFirstResume)
			updateMyLocationStatus(true);
		isFirstResume = false;
		super.onResume();
	}	
	
	@Override
	protected void onDestroy() {
		if (busyDialog != null)
			busyDialog.dismiss();
		busyDialog = null;
		super.onDestroy();
	}

	public void onCancel(DialogInterface dialog) {
		expectedRequestId = -1;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.stopmap_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
        if (this.map.getMapType() == GoogleMap.MAP_TYPE_NORMAL)
			menu.findItem(R.id.stopmap_menu_satellite_or_map).setTitle("Satellite View");
		else
			menu.findItem(R.id.stopmap_menu_satellite_or_map).setTitle("Map View");
		
		if (serviceFilter.areAllSet)
			menu.findItem(R.id.stopmap_menu_showall).setEnabled(false);
		else
			menu.findItem(R.id.stopmap_menu_showall).setEnabled(true);
		
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.stopmap_menu_search:
			doSearchForLocation();
			return true;

		case R.id.stopmap_menu_showall:
			doShowAllServices();
			return true;

		case R.id.stopmap_menu_filterservices:
			doFilterServices();
			return true;

		case R.id.stopmap_menu_satellite_or_map:
			doSetMapType();
			return true;
			
		case R.id.stopmap_menu_mylocation:
			doSetMyLocation();
			return true;
		}
		
		return false;
	}
/*
	public boolean onStopMapTap(GeoPoint point, MapView mapView)
	{
		StopDbHelper pt = StopDbHelper.Load(this);
		final int nearestStopNodeIdx = pt.findNearest(point.getLatitudeE6(), point.getLongitudeE6());
		final int stopCode = pt.stopCode[nearestStopNodeIdx];
		final double stopLat = pt.lat[nearestStopNodeIdx] / 1E6;
		final double stopLon = pt.lon[nearestStopNodeIdx] / 1E6;

		// Yuk - there must be a better way to convert GeoPoint->Point than this?
		Location touchLoc = new Location("");
		touchLoc.setLatitude(point.getLatitudeE6() / 1E6);
		touchLoc.setLongitude(point.getLongitudeE6() / 1E6);

		Location stopLoc = new Location("");
		stopLoc.setLatitude(stopLat);
		stopLoc.setLongitude(stopLon);

		if (touchLoc.distanceTo(stopLoc) >= StopTapRadiusMetres)
			return false;

		new StopMapPopup(this, stopCode);
		return true;
	}*/
	
/*	public boolean onStopMapTouchEvent(MotionEvent e, MapView mapView) {
        // disable my location if user drags the map
		if (e.getAction() == MotionEvent.ACTION_MOVE)
			updateMyLocationStatus(false);			
		return false;
	}*/

	private void doSearchForLocation() {
		final EditText input = new EditText(this);
		new AlertDialog.Builder(this)
			.setTitle("Enter a location or postcode")
			.setView(input)
			.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							busyDialog.show(StopMapActivity.this, "Finding location...");
							StopMapActivity.this.expectedRequestId = GeocodingHelper.geocode(StopMapActivity.this, input.getText().toString(), StopMapActivity.this);
						}
					})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}
	
	private void doShowAllServices() {
		serviceFilter.setAll();
		StopMapActivity.this.invalidate();
	}
	
	private void doFilterServices() {
		final EditText input = new EditText(this);

		new AlertDialog.Builder(this)
			.setTitle("Enter services separated by spaces")
			.setView(input)
			.setPositiveButton(android.R.string.ok, 
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						ServiceBitmap filter = new ServiceBitmap().clearAll();
						StopDbHelper pt = StopDbHelper.Load(StopMapActivity.this);
						for(String serviceStr: input.getText().toString().split("[ ]+")) {
							if (pt.serviceNameToServiceBit.containsKey(serviceStr.toUpperCase()))
								filter.setBit(pt.serviceNameToServiceBit.get(serviceStr.toUpperCase()));
						}
						
						updateServiceFilter(filter);
					}
				})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}
	
	public void doFilterServices(int stopCode) {
		StopDbHelper pt = StopDbHelper.Load(this);		
		int nodeIdx = pt.lookupStopNodeIdxByStopCode(stopCode);
		if (nodeIdx == -1)
			return;
		
		updateServiceFilter(pt.lookupServiceBitmapByStopNodeIdx(nodeIdx));
	}
	
	private void doSetMapType() {
        map.setMapType(map.getMapType() == GoogleMap.MAP_TYPE_NORMAL ? GoogleMap.MAP_TYPE_SATELLITE : GoogleMap.MAP_TYPE_NORMAL);
	}
	
	private void doSetMyLocation() {
		updateMyLocationStatus(true);
	}
	
	public void doStreetView(int stopCode) {
		StopDbHelper pt = StopDbHelper.Load(this);		
		int nodeIdx = pt.lookupStopNodeIdxByStopCode(stopCode);
		if (nodeIdx == -1)
			return;
		double stopLat = pt.lat[nodeIdx] / 1E6;
		double stopLon = pt.lon[nodeIdx] / 1E6;

		try {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("google.streetview:cbll=" + stopLat + "," + stopLon + "&cbp=1,180,,0,2.0")));
		} catch (ActivityNotFoundException ex) {
			new AlertDialog.Builder(this)
			.setTitle("Google StreetView required")
			.setMessage("You will need Google StreetView installed for this to work. Would you like to go to the Android Market to install it?")
			.setPositiveButton(android.R.string.ok, 
					new DialogInterface.OnClickListener() {
	                    public void onClick(DialogInterface dialog, int whichButton) {
	                    	try {
	                    		startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.street")));
	                    	} catch (Throwable t) {
	                    		Toast.makeText(StopMapActivity.this, "Sorry, I couldn't find the Android Market either!", 5000).show();
	                    	}
	                    }
					})
			.setNegativeButton(android.R.string.cancel, null)
	        .show();
		}
	}
	
	public void doShowArrivalTimes(int stopCode) {
		ArrivalTimeActivity.showActivity(StopMapActivity.this, stopCode);
	}
	
	public void doAddBookmark(int stopCode) {
		StopDbHelper pt = StopDbHelper.Load(this);		
		int nodeIdx = pt.lookupStopNodeIdxByStopCode(stopCode);
		if (nodeIdx == -1)
			return;
		String stopName = pt.lookupStopNameByStopNodeIdx(nodeIdx);

		Common.doAddBookmark(this, stopCode, stopName);
	}
	
	private void updateServiceFilter(ServiceBitmap filter) {
		serviceFilter.setTo(filter);
		// mapController.setZoom(12);
		StopMapActivity.this.invalidate();		
	}
	
	private void updateMyLocationStatus(boolean status) {
		if (status) {
			// myLocationOverlay.enableMyLocation();
			Toast.makeText(this, "Finding your location...", Toast.LENGTH_SHORT).show();
		} else {
			// myLocationOverlay.disableMyLocation();
		}
	}	

	public void onAsyncGeocodeResponseError(int requestId, String message) {
        Log.w(TAG, "Geocode response error!");
		if (requestId != expectedRequestId)
			return;
		
		if (busyDialog != null)
			busyDialog.dismiss();
		
		new AlertDialog.Builder(this).setTitle("Error").
			setMessage("Unable to find location: " + message).
			setPositiveButton(android.R.string.ok, null).
			show();
	}

    private void zoomTo(LatLng pos) {
        Log.i(TAG, "Zooming from " + map.getCameraPosition().target + " to: " + pos);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 17));
    }

	public void onAsyncGeocodeResponseSuccess(int requestId, List<Address> addresses_) {
        Log.i(TAG, "Async Geocode success!");
		if (requestId != expectedRequestId)
			return;
		
		if (busyDialog != null)
			busyDialog.dismiss();
		
		if (addresses_.size() == 1) {
			Address address = addresses_.get(0);
            LatLng pos = new LatLng(address.getLatitude(), address.getLongitude());
            zoomTo(pos);
			return;
		}
		
		final List<Address> addresses = addresses_;
		List<String> addressNames = new ArrayList<String>();
		for(Address a: addresses) {
			StringBuilder strb = new StringBuilder();
			for(int i =0; i< a.getMaxAddressLineIndex(); i++) {
				if (i > 0)
					strb.append(", ");
				strb.append(a.getAddressLine(i));
			}
			addressNames.add(strb.toString());
		}

        String[] addressArray = addressNames.toArray(new String[addressNames.size()]);
        DialogInterface.OnClickListener onClickSetPosition = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which < 0)
                    return;

                Address address = addresses.get(which);
                LatLng pos = new LatLng(address.getLatitude(), address.getLongitude());
                zoomTo(pos);
                dialog.dismiss();
            }
        };
        new AlertDialog.Builder(this).setSingleChoiceItems(addressArray, -1, onClickSetPosition).show();
    }
}
	