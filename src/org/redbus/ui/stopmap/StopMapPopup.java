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

package org.redbus.ui.stopmap;

import org.redbus.R;
import org.redbus.stopdb.ServiceBitmap;
import org.redbus.stopdb.StopDbHelper;

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
		
		StopDbHelper pt = StopDbHelper.Load(stopMapActivity);
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
			// Do not dismiss - perhaps user wants further action
			break;
			
		case R.id.stoppopup_filter:
			stopMapActivity.doFilterServices(stopCode);
			dialog.dismiss();
			break;
			
		case R.id.stoppopup_viewtimes:
			stopMapActivity.doShowArrivalTimes(stopCode);
			dialog.dismiss();
			break;
			
		case R.id.stoppopup_cancel:
			dialog.dismiss();
			break;
			
		case R.id.stoppopup_addbookmark:
			stopMapActivity.doAddBookmark(stopCode);
			dialog.dismiss();
			break;
		}
	}
}
