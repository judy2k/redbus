package org.redbus.ui.stopmap;

import org.redbus.R;
import org.redbus.stopdb.ServiceBitmap;
import org.redbus.stopdb.StopDbAccessor;

import android.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class StopMapPopup implements OnClickListener {

	private StopMapActivity stopMapActivity;
	private int stopCode;
	private AlertDialog dialog;
	
	public StopMapPopup(StopMapActivity stopMapActivity, int stopCode) {
		if (stopCode == -1)
			return;
		
		this.stopMapActivity = stopMapActivity;
		this.stopCode = stopCode;
		
		StopDbAccessor pt = StopDbAccessor.Load(stopMapActivity);
		int nodeIdx = pt.lookupStopNodeIdxByStopCode(stopCode);	
		if (nodeIdx == -1)
			return;
		ServiceBitmap serviceBitmap = pt.lookupServiceBitmapByStopNodeIdx(nodeIdx);
		String formattedServices = pt.formatServices(serviceBitmap, -1);
		String stopName = pt.lookupStopNameByStopNodeIdx(nodeIdx);
		
		View v = stopMapActivity.getLayoutInflater().inflate(R.layout.stoppopup, null);
		dialog = new AlertDialog.Builder(stopMapActivity).
			setTitle(stopName + " (" + stopCode + ")").
			setView(v).
			create();
		
		((TextView) v.findViewById(R.id.stoppopup_services)).setText("Services from this stop:\n" + formattedServices);
		((Button) v.findViewById(R.id.stoppopup_streetview)).setOnClickListener(this);
		((Button) v.findViewById(R.id.stoppopup_filter)).setOnClickListener(this);
		((Button) v.findViewById(R.id.stoppopup_viewtimes)).setOnClickListener(this);
		((Button) v.findViewById(R.id.stoppopup_cancel)).setOnClickListener(this);
		((Button) v.findViewById(R.id.stoppopup_addbookmark)).setOnClickListener(this);
		dialog.show();
	}
	
	public void onClick(View v) {
		switch(v.getId()) {
		case R.id.stoppopup_streetview:
			stopMapActivity.doStreetView(stopCode);
			break;
			
		case R.id.stoppopup_filter:
			stopMapActivity.doFilterServices(stopCode);
			dialog.dismiss();
			break;
			
		case R.id.stoppopup_viewtimes:
			stopMapActivity.doShowArrivalTimes(stopCode);
			break;
			
		case R.id.stoppopup_cancel:
			dialog.dismiss();
			break;
			
		case R.id.stoppopup_addbookmark:
			stopMapActivity.doAddBookmark(stopCode);
			break;
		}
	}
}
