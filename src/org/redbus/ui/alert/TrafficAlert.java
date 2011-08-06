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

package org.redbus.ui.alert;

import java.util.List;

import org.redbus.R;
import org.redbus.settings.SettingsHelper;
import org.redbus.trafficnews.ITrafficNewsResponseListener;
import org.redbus.trafficnews.NewsItem;
import org.redbus.trafficnews.TrafficNewsHelper;
import org.redbus.ui.trafficinfo.TrafficInfoActivity;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class TrafficAlert extends BroadcastReceiver implements ITrafficNewsResponseListener {
	
	// these are only used during broadcastreceiver implementation
	private Context broadcastContext;
	private Intent broadcastIntent;
	private SettingsHelper broadcastDb;

	
	public static void createTrafficAlert(Context ctx) {
		new TrafficAlert(ctx);
	}

	/**
	 * Empty constructor for BroadcastReceiver implementation
	 */
	public TrafficAlert() {
	}

	private TrafficAlert(Context ctx) {
		// create an intent
		Intent i = new Intent(ctx, TrafficAlert.class);
		PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_NO_CREATE);
		if (pi != null)
			pi.cancel();
//			return;
		
		// didn't already exist => create one!
		pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);
		AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
		am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 10000, AlarmManager.INTERVAL_FIFTEEN_MINUTES /*INTERVAL_HALF_HOUR */, pi);
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		this.broadcastContext = context;
		this.broadcastIntent = intent;
		this.broadcastDb = new SettingsHelper(context);
		
		TrafficNewsHelper.getTrafficNewsAsync(broadcastDb.getGlobalSetting("trafficLastTweetId", null), this);
	}

	public void onAsyncGetTrafficNewsError(int requestId, int code, String message) {
		broadcastDb.close();
	}

	public void onAsyncGetTrafficNewsSuccess(int requestId, List<NewsItem> newsItems) {
		
		if (newsItems == null)
			return;

		// record the last tweet id
		String lastTweetId = broadcastDb.getGlobalSetting("trafficLastTweetId", null);
		boolean hadLastTweetId = lastTweetId != null;
		if (newsItems.size() > 0)
			lastTweetId = newsItems.get(0).tweetId;
		if (lastTweetId != null)
			broadcastDb.setGlobalSetting("trafficLastTweetId", lastTweetId);
		broadcastDb.close();

		// if this is the first time we've recorded a tweet id, don't display the alert, to avoid spamming *everyone* on the first upgrade.
		if (!hadLastTweetId)
			return;

		Intent ui = new Intent(broadcastContext, TrafficInfoActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(broadcastContext, 0, ui, PendingIntent.FLAG_CANCEL_CURRENT);

		Notification notification = new Notification(R.drawable.icon38, "New traffic information", System.currentTimeMillis());
		notification.defaults |= 0;
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.setLatestEventInfo(broadcastContext, "New traffic information available", "Press to view", contentIntent);
		
		NotificationManager nm = (NotificationManager) broadcastContext.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(AlertUtils.TRAFFIC_NOTIFICATION_ID, notification);
	}
}
