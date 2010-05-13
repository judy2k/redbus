package org.redbus;

import java.util.List;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class BusTimesActivity extends ListActivity implements BusDataResponseListener {
	
	private long StopCode = -1;
	private ProgressDialog busyDialog = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.bustimes);
        registerForContextMenu(getListView());

        StopCode = getIntent().getLongExtra("StopCode", -1);	
        setTitle(getIntent().getCharSequenceExtra("StopName"));
	}

	@Override
	protected void onStart() 
	{
		super.onStart();		
		UpdateBusTimes();
	}

	public void UpdateBusTimes()
	{
		if (StopCode != -1) {
			BusDataHelper.GetBusTimesAsync(StopCode, this);
			busyDialog = ProgressDialog.show(this, "Busy", "Getting stop times");
		}
	}

	public void getBusTimesError(int code, String message) {
		if (busyDialog != null)
			busyDialog.dismiss();

		new AlertDialog.Builder(this).
			setTitle("Error").
			setMessage("Unable to download stop times! (" + message + ")").
			setPositiveButton("OK", null).
	        show();
	}

	public void getBusTimesSuccess(List<BusTime> busTimes) {
		if (busyDialog != null)
			busyDialog.dismiss();
		
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
			UpdateBusTimes();
			return true;

		case R.id.bustimes_menu_enterstopcode:
			// FIXME: implement
			return true;

		case R.id.bustimes_menu_addbookmark:
			// FIXME: implement
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
}
