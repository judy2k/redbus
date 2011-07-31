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

import java.util.List;

import org.redbus.R;
import org.redbus.R.id;
import org.redbus.arrivaltime.ArrivalTime;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;


public class ArrivalTimeArrayAdapter extends ArrayAdapter<ArrivalTime> {
	private List<ArrivalTime> items;
	private int textViewResourceId;
	private Context ctx;

	public ArrivalTimeArrayAdapter(Context context, int textViewResourceId, List<ArrivalTime> items) {
		super(context, textViewResourceId, items);

		this.ctx = context;
		this.textViewResourceId = textViewResourceId;
		this.items = items;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		if (v == null) {
			LayoutInflater vi = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = vi.inflate(textViewResourceId, null);
		}

		ArrivalTime arrivalTime = items.get(position);
		if (arrivalTime == null)
			return v;
		
		TextView serviceView = (TextView) v.findViewById(R.id.bustimes_service);
		TextView destinationView = (TextView) v.findViewById(R.id.bustimes_destination);
		TextView timeView = (TextView) v.findViewById(R.id.bustimes_time);

		serviceView.setText(arrivalTime.service);
		
		if (arrivalTime.isDiverted) {
			destinationView.setText("DIVERTED");
			timeView.setText("");
		} else {
			destinationView.setText(arrivalTime.destination);

			if (arrivalTime.arrivalIsDue)
				timeView.setText("Due");
			else if (arrivalTime.arrivalAbsoluteTime != null)
				timeView.setText(arrivalTime.arrivalAbsoluteTime);
			else
				timeView.setText(Integer.toString(arrivalTime.arrivalMinutesLeft));
			
			if (arrivalTime.arrivalEstimated)
				timeView.setText("~" + timeView.getText());
		}
		return v;
	}
}
