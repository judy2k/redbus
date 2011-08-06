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

import org.redbus.R;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

public class AlertUtils {
	
	public static final int ALERT_NOTIFICATION_ID = 1;
	public static final int TRAFFIC_NOTIFICATION_ID = 2;

	public static void addOngoingNotification(Context ctx)
	{
		Intent i = new Intent(ctx, AlertNotificationPressedReceiver.class);		
		PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT); 

		Notification notification = new Notification(R.drawable.icon38, "Bus alarm active", System.currentTimeMillis());
		notification.defaults = 0;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.setLatestEventInfo(ctx, "Bus alarm active", "Press to cancel", pi);

		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(ALERT_NOTIFICATION_ID, notification);
	}

	public static void cancelAlerts(Context ctx) {
		// cancel any temporal alert
		Intent i = new Intent(ctx, TemporalAlert.class);
		PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_NO_CREATE);
		if (pi != null) {
			AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
			am.cancel(pi);
			pi.cancel();
		}

		// cancel any proximity alert
		i = new Intent(ctx, ProximityAlert.class);
		pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_NO_CREATE);
		if (pi != null) {
			LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
			lm.removeUpdates(pi);
			lm.removeProximityAlert(pi);
			pi.cancel();
		}
		
		// cancel any ongoing alerts
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(ALERT_NOTIFICATION_ID);
	}
}
