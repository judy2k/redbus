/*
 * Copyright 2010, 2011 Andrew De Quincey -  adq@lidskialf.net
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

package org.redbus.ui.trafficinfo;

import java.util.ArrayList;
import java.util.List;

import org.redbus.R;
import org.redbus.settings.SettingsHelper;
import org.redbus.trafficnews.ITrafficNewsResponseListener;
import org.redbus.trafficnews.NewsItem;
import org.redbus.trafficnews.TrafficNewsHelper;
import org.redbus.ui.BusyDialog;


import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

public class TrafficInfoActivity extends ListActivity implements ITrafficNewsResponseListener, OnCancelListener {
	
	private BusyDialog busyDialog = null;
	private int expectedRequestId = -1;
	
	public static void showActivity(Context context) {
		Intent i = new Intent(context, TrafficInfoActivity.class);
		context.startActivity(i);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.trafficinfo);
		registerForContextMenu(getListView());
		busyDialog = new BusyDialog(this);
		
		doRefreshTrafficInfo();
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
		inflater.inflate(R.menu.trafficinfo_menu, menu);		
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.trafficinfo_menu_refresh:
			doRefreshTrafficInfo();
			return true;
		}
		
		return false;
	}
	
	
	
	
	private void doRefreshTrafficInfo() {
		if (busyDialog != null)
			busyDialog.show(this, "Retrieving traffic information");
		expectedRequestId = TrafficNewsHelper.getTrafficNewsAsync(null, this);
	}

	public void onAsyncGetTrafficNewsError(int requestId, int code, String message) {
		if (requestId != expectedRequestId)
			return;
		
		if (busyDialog != null)
			busyDialog.dismiss();

		setListAdapter(new TrafficInfoArrayAdapter(this, R.layout.trafficinfo_item, new ArrayList<NewsItem>()));
		findViewById(R.id.trafficinfo_error).setVisibility(View.VISIBLE);

		new AlertDialog.Builder(this).setTitle("Error").
			setMessage("Unable to download traffic info: " + message).
			setPositiveButton(android.R.string.ok, null).
			show();
	}

	public void onAsyncGetTrafficNewsSuccess(int requestId, List<NewsItem> newsItems) {
		if (requestId != expectedRequestId)
			return;
		
		if (busyDialog != null)
			busyDialog.dismiss();
		
		setListAdapter(new TrafficInfoArrayAdapter(this, R.layout.trafficinfo_item, newsItems));
		if (newsItems.isEmpty())
			findViewById(R.id.trafficinfo_none).setVisibility(View.VISIBLE);

		// store last tweet id so we don't warn about ones they've already looked at from a manual request
		SettingsHelper db = null;
		try {
			db = new SettingsHelper(this);
			String lastTweetId = db.getGlobalSetting("trafficLastTweetId", null);
			if (newsItems.size() > 0)
				lastTweetId = newsItems.get(0).tweetId;
			if (lastTweetId != null)
				db.setGlobalSetting("trafficLastTweetId", lastTweetId);
		} finally {
			db.close();
		}
	}
}

