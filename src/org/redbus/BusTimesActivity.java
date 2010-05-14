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
import java.util.Date;
import java.util.List;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class BusTimesActivity extends ListActivity implements
		BusDataResponseListener {

	private long StopCode = -1;
	private String StopName = "";
	private ProgressDialog busyDialog = null;
	private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MMM/yyyy HH:mm");

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
		if (StopCode != -1) {
			setTitle(StopName + " (" + dateFormat.format(new Date()) + ")");
			DisplayBusy("Getting BusStop times", null);

			BusDataHelper.GetBusTimesAsync(StopCode, this);
		} else {
			setTitle("Unknown BusStop");
			findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
		}
	}

	private void DisplayBusy(String reason, OnCancelListener listener) {
		DismissBusy();

		if (listener != null)
			busyDialog = ProgressDialog.show(this, "", reason, true, true, listener);
		else
			busyDialog = ProgressDialog.show(this, "", reason, true);
	}

	private void DismissBusy() {
		if (busyDialog != null) {
			busyDialog.dismiss();
			busyDialog = null;
		}
	}

	private void HideStatusBoxes() {
		findViewById(R.id.bustimes_nodepartures).setVisibility(View.GONE);
		findViewById(R.id.bustimes_error).setVisibility(View.GONE);
		findViewById(android.R.id.empty).setVisibility(View.GONE);
	}

	public void getBusTimesError(int code, String message) {
		DismissBusy();
		HideStatusBoxes();

		setListAdapter(new BusTimesAdapter(this, R.layout.bustimes_item, new ArrayList<BusTime>()));
		findViewById(R.id.bustimes_error).setVisibility(View.VISIBLE);

		new AlertDialog.Builder(this).setTitle("Error").
			setMessage("Unable to download stop times: " + message).
			setPositiveButton(android.R.string.ok, null).
			show();
	}

	public void getBusTimesSuccess(List<BusTime> busTimes) {
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
									
									DisplayBusy("Validating BusStop code", null);
									BusDataHelper.GetStopNameAsync(stopCode, BusTimesActivity.this);
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

		case R.id.bustimes_menu_settings:
			// FIXME: implement
			return true;

		case R.id.bustimes_menu_viewonmap:
			// FIXME: implement
			return true;
		}

		return false;
	}

	public void getStopNameError(int code, String message) {
		DismissBusy();

		new AlertDialog.Builder(this).setTitle("Error")
			.setMessage("Unable to validate BusStop code: " + message)
			.setPositiveButton(android.R.string.ok, null)
			.show();
	}

	public void getStopNameSuccess(long stopCode, String stopName) {
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
