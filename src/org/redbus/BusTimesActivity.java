package org.redbus;

import android.app.Activity;
import android.os.Bundle;

public class BusTimesActivity extends Activity implements BusDataResponseListener {
	
	private long StopCode = -1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.bustimes);
        StopCode = getIntent().getLongExtra("StopCode", -1);
	}
	
	@Override
	protected void onStart() 
	{
		super.onStart();
		
		UpdateBusTimes();
	}
	
	public void UpdateBusTimes()
	{
		if (StopCode != -1)
			BusDataHelper.GetBusTimesAsync(StopCode, this);
	}

	public void onBusTimesReceived() {
		// FIXME: do something with them!
	}
}
