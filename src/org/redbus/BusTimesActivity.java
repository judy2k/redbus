package org.redbus;

import java.util.List;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

public class BusTimesActivity extends ListActivity implements BusDataResponseListener {
	
	private long StopCode = -1;
	private String StopName = "";
	private ProgressDialog busyDialog = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.bustimes);
        registerForContextMenu(getListView());

        StopCode = getIntent().getLongExtra("StopCode", -1);
        StopName = "";
        CharSequence tmp = getIntent().getCharSequenceExtra("StopName");
        if (tmp != null)
        	StopName =  tmp.toString();
	}

	@Override
	protected void onStart() 
	{
		super.onStart();		
		Update();
	}
	
	public void Update()
	{
		if (StopCode != -1) {
			setTitle(StopName + " (" + StopCode + ")");
			BusDataHelper.GetBusTimesAsync(StopCode, this);
			busyDialog = ProgressDialog.show(this, "Busy", "Getting BusStop times");
		} else {
			setTitle("Unknown BusStop");
		}
	}

	public void getBusTimesError(int code, String message) {
		if (busyDialog != null) {
			busyDialog.dismiss();
			busyDialog = null;
		}

		new AlertDialog.Builder(this).
			setTitle("Error").
			setMessage("Unable to download stop times: " + message).
			setPositiveButton(android.R.string.ok, null).
	        show();
	}

	public void getBusTimesSuccess(List<BusTime> busTimes) {
		if (busyDialog != null) {
			busyDialog.dismiss();
			busyDialog = null;
		}
		
		int x = 1;
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.bustimes_menu, menu);
	    return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch(item.getItemId()) {
		case R.id.bustimes_menu_refresh:
			Update();
			return true;

		case R.id.bustimes_menu_enterstopcode:
			final EditText input = new EditText(this);

			new AlertDialog.Builder(this)
				.setTitle("Enter BusStop code")
				.setView(input)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {  
						public void onClick(DialogInterface dialog, int whichButton) {
						  String value = input.getText().toString();
						  try {
							long newStopCode = Long.parseLong(value);
							busyDialog = ProgressDialog.show(BusTimesActivity.this, "Busy", "Validating BusStop code");
							BusDataHelper.GetStopNameAsync(newStopCode, BusTimesActivity.this);

						  } catch (Exception ex) {
								new AlertDialog.Builder(BusTimesActivity.this).
									setTitle("Invalid BusStop code").
									setMessage("The code was invalid; please try again using only numbers").
									setPositiveButton(android.R.string.ok, null).
							        show();
						  }
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
		if (busyDialog != null) {
			busyDialog.dismiss();
			busyDialog = null;
		}

		new AlertDialog.Builder(this).
			setTitle("Error").
			setMessage("Unable to validate BusStop code: " + message).
			setPositiveButton(android.R.string.ok, null).
	        show();
	}

	public void getStopNameSuccess(long stopCode, String stopName) {
		if (busyDialog != null) {
			busyDialog.dismiss();
			busyDialog = null;
		}
		
		StopCode = stopCode;
		StopName = stopName;
		Update();
	}
}
