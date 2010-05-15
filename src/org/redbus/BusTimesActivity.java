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

package org.redbus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
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
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

public class BusTimesActivity extends ListActivity implements
		BusDataResponseListener {

	private long StopCode = -1;
	private String StopName = "";
	private ProgressDialog busyDialog = null;
	private SimpleDateFormat titleDateFormat = new SimpleDateFormat("EEE dd MMM HH:mm");
	private SimpleDateFormat advanceDateFormat = new SimpleDateFormat("EEE dd MMM yyyy");
	private int expectedRequestId = -1;

	public static void showActivity(Context context, long stopCode,
			String stopName) {
		Intent i = new Intent(context, BusTimesActivity.class);
		i.putExtra("StopCode", stopCode);
		i.putExtra("StopName", stopName);
		context.startActivity(i);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bustimes);
		registerForContextMenu(getListView());

		StopCode = getIntent().getLongExtra("StopCode", -1);
		if (StopCode != -1)
			findViewById(android.R.id.empty).setVisibility(View.GONE);

		StopName = "";
		CharSequence tmp = getIntent().getCharSequenceExtra("StopName");
		if (tmp != null)
			StopName = tmp.toString();

		Update();
	}

	public void Update() {
		Update(0, null);
	}

	public void Update(int daysInAdvance, Date timeInAdvance) {
		if (StopCode != -1) {
			Date displayDate = timeInAdvance;
			if (displayDate == null)
				displayDate = new Date();
	
			setTitle(StopName + " (" + titleDateFormat.format(displayDate) + ")");
			DisplayBusy("Getting BusStop times");

			expectedRequestId = BusDataHelper.GetBusTimesAsync(StopCode, daysInAdvance, timeInAdvance, this);
		} else {
			setTitle("Unknown BusStop");
			findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
		}
	}

	private void DisplayBusy(String reason) {
		DismissBusy();

		busyDialog = ProgressDialog.show(this, "", reason, true, true, new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				BusTimesActivity.this.expectedRequestId = -1;
			}
		});
	}

	private void DismissBusy() {
		if (busyDialog != null) {
			try {
				busyDialog.dismiss();
			} catch (Throwable t) {
			}
			busyDialog = null;
		}
	}

	private void HideStatusBoxes() {
		findViewById(R.id.bustimes_nodepartures).setVisibility(View.GONE);
		findViewById(R.id.bustimes_error).setVisibility(View.GONE);
		findViewById(android.R.id.empty).setVisibility(View.GONE);
	}

	public void getBusTimesError(int requestId, int code, String message) {
		if (requestId != expectedRequestId)
			return;
		
		DismissBusy();
		HideStatusBoxes();

		setListAdapter(new BusTimesAdapter(this, R.layout.bustimes_item, new ArrayList<BusTime>()));
		findViewById(R.id.bustimes_error).setVisibility(View.VISIBLE);

		new AlertDialog.Builder(this).setTitle("Error").
			setMessage("Unable to download stop times: " + message).
			setPositiveButton(android.R.string.ok, null).
			show();
	}

	public void getBusTimesSuccess(int requestId, List<BusTime> busTimes) {
		if (requestId != expectedRequestId)
			return;
		
		DismissBusy();
		HideStatusBoxes();

		setListAdapter(new BusTimesAdapter(this, R.layout.bustimes_item, busTimes));
		if (busTimes.isEmpty())
			findViewById(R.id.bustimes_nodepartures).setVisibility(View.VISIBLE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.bustimes_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.bustimes_menu_refresh:
			Update();
			return true;

		case R.id.bustimes_menu_enterstopcode:
			final EditText input = new EditText(this);

			new AlertDialog.Builder(this)
					.setTitle("Enter BusStop code")
					.setView(input)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int whichButton) {
									long stopCode = -1;
									try {
										stopCode = Long.parseLong(input.getText().toString());
									} catch (Exception ex) {
										new AlertDialog.Builder(BusTimesActivity.this)
												.setTitle("Invalid BusStop code")
												.setMessage("The code was invalid; please try again using only numbers")
												.setPositiveButton(android.R.string.ok, null)
												.show();
										return;
									}
									
									DisplayBusy("Validating BusStop code");
									BusTimesActivity.this.expectedRequestId = BusDataHelper.GetStopNameAsync(stopCode, BusTimesActivity.this);
								}
							})
					.setNegativeButton(android.R.string.cancel, null)
					.show();
			return true;

		case R.id.bustimes_menu_addbookmark:
			if (StopCode != -1) {
				LocalDBHelper db = new LocalDBHelper(this, false);
				try {
					db.AddBookmark(StopCode, StopName);
				} finally {
					db.close();
				}
				Toast.makeText(this, "Added bookmark", Toast.LENGTH_SHORT).show();
			}
			return true;

		case R.id.bustimes_menu_viewonmap:
			// FIXME: implement
			return true;

		case R.id.bustimes_menu_futuredepartures:
			LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View v = vi.inflate(R.layout.futuredepartures, null);

			final GregorianCalendar calendar = new GregorianCalendar();
			final TimePicker timePicker = (TimePicker) v.findViewById(R.id.futuredepartures_time);
			timePicker.setCurrentHour(calendar.get(Calendar.HOUR_OF_DAY));
			timePicker.setCurrentMinute(calendar.get(Calendar.MINUTE));
			timePicker.setIs24HourView(true);

			final Spinner datePicker = (Spinner) v.findViewById(R.id.futuredepartures_date);
			ArrayList<String> dates = new ArrayList<String>();
			for(int i=0; i < 4; i++) {
				dates.add(advanceDateFormat.format(calendar.getTime()));
				calendar.add(Calendar.DAY_OF_MONTH, 1);
			}
			calendar.add(Calendar.DAY_OF_MONTH, -4);
			datePicker.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, dates));

			new AlertDialog.Builder(this)
				.setTitle("Choose the desired date/time")
				.setView(v)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								calendar.add(Calendar.DAY_OF_MONTH, datePicker.getSelectedItemPosition());
								calendar.set(Calendar.HOUR_OF_DAY, timePicker.getCurrentHour());
								calendar.set(Calendar.MINUTE, timePicker.getCurrentMinute());
								Update(datePicker.getSelectedItemPosition(), calendar.getTime());
							}
						})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
			return true;
			
		case R.id.bustimes_menu_settings_autorefresh:
			// FIXME: implement
			return true;

		case R.id.bustimes_menu_settings_sorting:
			// FIXME: implement
			return true;

		case R.id.bustimes_menu_settings_numdepartures:
			// FIXME: implement
			return true;
		}

		return false;
	}

	public void getStopNameError(int requestId, int code, String message) {
		if (requestId != expectedRequestId)
			return;

		DismissBusy();

		new AlertDialog.Builder(this).setTitle("Error")
			.setMessage("Unable to validate BusStop code: " + message)
			.setPositiveButton(android.R.string.ok, null)
			.show();
	}

	public void getStopNameSuccess(int requestId, long stopCode, String stopName) {
		if (requestId != expectedRequestId)
			return;
		
		DismissBusy();

		StopCode = stopCode;
		StopName = stopName;
		Update();
	}

	private class BusTimesAdapter extends ArrayAdapter<BusTime> {
		private List<BusTime> items;
		private int textViewResourceId;

		public BusTimesAdapter(Context context, int textViewResourceId, List<BusTime> items) {
			super(context, textViewResourceId, items);

			this.textViewResourceId = textViewResourceId;
			this.items = items;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(textViewResourceId, null);
			}

			BusTime busTime = items.get(position);
			if (busTime != null) {
				TextView serviceView = (TextView) v.findViewById(R.id.bustimes_service);
				TextView destinationView = (TextView) v.findViewById(R.id.bustimes_destination);
				TextView timeView = (TextView) v.findViewById(R.id.bustimes_time);

				serviceView.setText(busTime.service);
				destinationView.setText(busTime.destination);

				if (busTime.arrivalIsDue)
					timeView.setText("Due");
				else if (busTime.arrivalAbsoluteTime != null)
					timeView.setText(busTime.arrivalAbsoluteTime);
				else
					timeView.setText(Integer.toString(busTime.arrivalMinutesLeft));
				
				if (busTime.arrivalEstimated)
					timeView.setTextColor(getResources().getColor(R.color.bustime_estimated));
			}

			return v;
		}
	}
}
