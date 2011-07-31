/*
 * Copyright 2010 Andrew De Quincey -  adq@lidskialf.net
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

package org.redbus.ui.arrivaltime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.redbus.R;
import org.redbus.StopBookmarksActivity;
import org.redbus.arrivaltime.ArrivalTime;
import org.redbus.arrivaltime.ArrivalTimeAccessor;
import org.redbus.arrivaltime.IArrivalTimeResponseListener;
import org.redbus.settings.SettingsAccessor;
import org.redbus.stopdb.StopDbAccessor;
import org.redbus.ui.BusyDialog;
import org.redbus.ui.alert.ProximityAlert;
import org.redbus.ui.alert.TemporalAlert;
import org.redbus.ui.stopmap.StopMapActivity;


import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class ArrivalTimeActivity extends ListActivity implements IArrivalTimeResponseListener, OnCancelListener {

	private int stopCode = -1;
	private String stopName = "";	
	private String sorting = "";

	private ProgressDialog busyDialog = null;
	private int expectedRequestId = -1;

	private static final SimpleDateFormat titleDateFormat = new SimpleDateFormat("EEE dd MMM HH:mm");

	private static final String[] hourStrings = new String[] { "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23" };
	private static final String[] minStrings = new String[] { "00", "15", "30", "45" };
	
	public static void showActivity(Context context, long stopCode) {
		Intent i = new Intent(context, ArrivalTimeActivity.class);
		i.putExtra("StopCode", stopCode);
		context.startActivity(i);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.bustimes);
		registerForContextMenu(getListView());
		
		stopCode = (int) getIntent().getLongExtra("StopCode", -1);
		if (stopCode != -1)
			findViewById(android.R.id.empty).setVisibility(View.GONE);
		
		SettingsAccessor db = new SettingsAccessor(this);
		try {
			sorting = db.getGlobalSetting("bustimesort", "arrival");
			stopName = db.getBookmarkName(stopCode);
		} finally {
			db.close();
		}
		
		StopDbAccessor pt = StopDbAccessor.Load(this);
		int stopNodeIdx = pt.lookupStopNodeIdxByStopCode((int) stopCode);
		if (stopNodeIdx != -1) {
			stopName = pt.lookupStopNameByStopNodeIdx(stopNodeIdx);
		} else {
			stopName = "";
		}
		
		update();
	}
	
	@Override
	protected void onDestroy() {
		busyDialog = null;
		super.onDestroy();		
	}
	
	public void onCancel(DialogInterface dialog) {
		expectedRequestId = -1;
	}
	
	private void update() {
		update(0, null);
	}

	private void update(int daysInAdvance, Date timeInAdvance) {
		if (stopCode != -1) {
			Date displayDate = timeInAdvance;
			if (displayDate == null)
				displayDate = new Date();
	
			setTitle(stopName + " (" + titleDateFormat.format(displayDate) + ")");
        	busyDialog = BusyDialog.show(this, this, busyDialog, "Retrieving bus times");
			expectedRequestId = ArrivalTimeAccessor.getBusTimesAsync(stopCode, daysInAdvance, timeInAdvance, this);
		} else {
			setTitle("Unknown bus stop");
			findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
		}
	}

	private void hideStatusBoxes() {
		findViewById(R.id.bustimes_nodepartures).setVisibility(View.GONE);
		findViewById(R.id.bustimes_error).setVisibility(View.GONE);
		findViewById(android.R.id.empty).setVisibility(View.GONE);
	}

	public void getBusTimesError(int requestId, int code, String message) {
		if (requestId != expectedRequestId)
			return;
		
		BusyDialog.dismiss(busyDialog);
		hideStatusBoxes();

		setListAdapter(new ArrivalTimeArrayAdapter(this, R.layout.bustimes_item, new ArrayList<ArrivalTime>()));
		findViewById(R.id.bustimes_error).setVisibility(View.VISIBLE);

		new AlertDialog.Builder(this).setTitle("Error").
			setMessage("Unable to download bus times: " + message).
			setPositiveButton(android.R.string.ok, null).
			show();
	}

	public void getBusTimesSuccess(int requestId, List<ArrivalTime> busTimes) {
		if (requestId != expectedRequestId)
			return;
		
		BusyDialog.dismiss(busyDialog);
		hideStatusBoxes();
		
		if (sorting.equalsIgnoreCase("service")) {
			Collections.sort(busTimes, new Comparator<ArrivalTime>() {
				public int compare(ArrivalTime arg0, ArrivalTime arg1) {
					if (arg0.baseService != arg1.baseService)
						return arg0.baseService - arg1.baseService;
					return arg0.service.compareTo(arg1.service);
				}
			});
		} else if (sorting.equalsIgnoreCase("arrival")) {
			Collections.sort(busTimes, new Comparator<ArrivalTime>() {
				public int compare(ArrivalTime arg0, ArrivalTime arg1) {
					if ((arg0.arrivalAbsoluteTime != null) && (arg1.arrivalAbsoluteTime != null)) {
						// bus data never seems to span to the next day, so this string comparison should always work
						return arg0.arrivalAbsoluteTime.compareTo(arg1.arrivalAbsoluteTime);
					}
					return arg0.arrivalSortingIndex - arg1.arrivalSortingIndex;
				}
			});
		}

		setListAdapter(new ArrivalTimeArrayAdapter(this, R.layout.bustimes_item, busTimes));
		if (busTimes.isEmpty())
			findViewById(R.id.bustimes_nodepartures).setVisibility(View.VISIBLE);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		TextView clickedService = (TextView) v.findViewById(R.id.bustimes_service);
		new TemporalAlert(this, stopCode, clickedService.getText().toString());
	}

	



	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.bustimes_menu, menu);
		
        SettingsAccessor db = new SettingsAccessor(this);
        try {
        	if (db.isBookmark(stopCode)) {
        		menu.findItem(R.id.bustimes_menu_addbookmark).setEnabled(false);
        	} else {
        		menu.findItem(R.id.bustimes_menu_renamebookmark).setEnabled(false);
        		menu.findItem(R.id.bustimes_menu_deletebookmark).setEnabled(false);
        	}
        } finally {
        	db.close();
        }
		
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.bustimes_menu_refresh:
			doRefreshArrivalTimes();
			return true;

		case R.id.bustimes_menu_addbookmark:
			doAddBookmark();
			return true;
			
		case R.id.bustimes_menu_renamebookmark:
			doRenameBookmark();
			return true;

		case R.id.bustimes_menu_deletebookmark:
			doDeleteBookmark();
			return true;

		case R.id.bustimes_menu_viewonmap:
			doViewOnMap();
			return true;

		case R.id.bustimes_menu_futuredepartures:
			doFutureDepartures();
			return true;

		case R.id.bustimes_menu_sorting_arrival:
			doSorting("arrival");
			return true;

		case R.id.bustimes_menu_sorting_service:
			doSorting("service");
			return true;
		
		case R.id.bustimes_menu_proximityalert:
			doProximityAlert();
			return true;
		
		case R.id.bustimes_menu_temporalalert:
			doTemporalAlert();
			return true;			
		}

		return false;
	}
	
	private void doRefreshArrivalTimes() {
		update();		
	}
	
	private void doAddBookmark() {
		if (stopCode == -1) 
			return;
		
		SettingsAccessor db = new SettingsAccessor(this);
		try {
			db.addBookmark(stopCode, stopName);
		} finally {
			db.close();
		}
		Toast.makeText(this, "Added bookmark", Toast.LENGTH_SHORT).show();
	}
	
	private void doRenameBookmark() {
		 final EditText input = new EditText(this);
			input.setText(stopName);

			new AlertDialog.Builder(this)
					.setTitle("Rename bookmark")
					.setView(input)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int whichButton) {
			                        SettingsAccessor db = new SettingsAccessor(ArrivalTimeActivity.this);
			                        try {
			                        	db.renameBookmark(ArrivalTimeActivity.this.stopCode, input.getText().toString());
			                        } finally {
			                        	db.close();
			                        }
			                        stopName = input.getText().toString();
			                        update();
								}
							})
					.setNegativeButton(android.R.string.cancel, null)
					.show();
	}
	
	private void doDeleteBookmark() {
		new AlertDialog.Builder(this)
		.setTitle("Delete bookmark")
		.setMessage("Are you sure you want to delete this bookmark?")
		.setPositiveButton(android.R.string.ok, 
				new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	                    SettingsAccessor db = new SettingsAccessor(ArrivalTimeActivity.this);
	                    try {
	                    	db.deleteBookmark(ArrivalTimeActivity.this.stopCode);
	                    } finally {
	                    	db.close();
	                    }
	                    ArrivalTimeActivity.this.update();
	                }
				})
		.setNegativeButton(android.R.string.cancel, null)
        .show();
	}
	
	private void doViewOnMap() {
		StopDbAccessor pt = StopDbAccessor.Load(ArrivalTimeActivity.this);
		int stopNodeIdx = pt.lookupStopNodeIdxByStopCode((int) stopCode);
		if (stopNodeIdx != -1)
			StopMapActivity.showActivity(this, pt.lat[stopNodeIdx], pt.lon[stopNodeIdx]);
	}
	
	private void doFutureDepartures() {
		LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = vi.inflate(R.layout.futuredepartures, null);

		final GregorianCalendar calendar = new GregorianCalendar();
		
		final Spinner hourPicker = (Spinner) v.findViewById(R.id.futuredepartures_time_hour);
		ArrayAdapter<String> hourAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, hourStrings);
		hourAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		hourPicker.setAdapter(hourAdapter);
		hourPicker.setSelection(12);
		
		final Spinner minPicker = (Spinner) v.findViewById(R.id.futuredepartures_time_min);
		ArrayAdapter<String> minAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, minStrings);
		minAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		minPicker.setAdapter(minAdapter);

		new AlertDialog.Builder(this)
			.setTitle("Choose the desired date/time")
			.setView(v)
			.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							calendar.set(Calendar.HOUR_OF_DAY, hourPicker.getSelectedItemPosition());
							calendar.set(Calendar.MINUTE, minPicker.getSelectedItemPosition() * 15);
							update(0, calendar.getTime());
						}
					})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}
	
	private void doSorting(String newSorting) {
		this.sorting = newSorting;
		
		SettingsAccessor db = new SettingsAccessor(this);
		try {
			db.setGlobalSetting("bustimesort",  this.sorting);
		} finally {
			db.close();
		}
		update();
	}
	
	private void doProximityAlert() {
		new ProximityAlert(this, stopCode);
	}
	
	private void doTemporalAlert() {
		new TemporalAlert(this, stopCode, null);
	}
}

