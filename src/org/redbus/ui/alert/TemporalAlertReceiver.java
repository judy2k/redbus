package org.redbus.ui.alert;

import java.util.HashMap;
import java.util.List;

import org.redbus.BusTimesActivity;
import org.redbus.R;
import org.redbus.R.drawable;
import org.redbus.arrivaltime.ArrivalTime;
import org.redbus.arrivaltime.ArrivalTimeAccessor;
import org.redbus.arrivaltime.IArrivalTimeResponseListener;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class TemporalAlertReceiver extends BroadcastReceiver implements
		IArrivalTimeResponseListener {

	private Context context;
	private Intent intent;
	private long stopCode;
	private String stopName;

	private static final int ALARM_MAX_TIMEOUT_MSEC = 40 * 60 * 1000;

	@Override
	public void onReceive(Context context, Intent intent) {
		this.context = context;
		this.intent = intent;

		stopCode = intent.getLongExtra("StopCode", -1);
		if (stopCode == -1)
			return;
		stopName = intent.getStringExtra("StopName");
		if (stopName == null)
			return;

		ArrivalTimeAccessor.getBusTimesAsync(stopCode, 0, null, this);
	}

	private void rescheduleAlarm() {
		// make sure alarms don't run forever
		long startTime = intent.getLongExtra("StartTime", -1);
		if ((startTime == -1) || ((startTime + ALARM_MAX_TIMEOUT_MSEC) < System.currentTimeMillis())) {
			Intent i = new Intent(context, BusTimesActivity.class);
			PendingIntent contentIntent = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);

			Notification notification = new Notification(R.drawable.tracker_24x24_masked, "Bus alarm aborted!", System.currentTimeMillis());
			notification.defaults |= Notification.DEFAULT_ALL;
			notification.flags |= Notification.FLAG_AUTO_CANCEL;
			notification.setLatestEventInfo(context, "Bus alarm aborted!", "Bus did not arrive within 40 mins; cancelling alarm", contentIntent);

			NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			nm.notify(BusTimesActivity.ALERT_NOTIFICATION_ID, notification);
			return;
		}

		// schedule it in 60 seconds
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, 0);
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60000, pi);
	}

	public void getBusTimesError(int requestId, int code, String message) {
		rescheduleAlarm();
	}

	public void getBusTimesSuccess(int requestId, List<ArrivalTime> busTimes) {
		String[] requestedServices = intent.getStringArrayExtra("Services");
		int timeout = intent.getIntExtra("TimeoutSecs", -1);
		if ((requestedServices == null) || (timeout == -1))
			return;
		HashMap<String, Boolean> requestedServicesLookup = new HashMap<String, Boolean>();
		for (String curService : requestedServices)
			requestedServicesLookup.put(curService.toLowerCase(), new Boolean(true));

		for (ArrivalTime curTime : busTimes) {
			if (requestedServicesLookup.containsKey(curTime.service.toLowerCase()) && 
					(curTime.arrivalAbsoluteTime == null) &&
					((curTime.arrivalMinutesLeft * 60) <= timeout)) {

				StringBuffer text = new StringBuffer();
				text.append("The ");
				text.append(curTime.service);
				text.append(" is due");
				if (curTime.arrivalMinutesLeft > 0) {
					text.append(" in ");
					text.append(curTime.arrivalMinutesLeft);
					text.append(" minutes");
				}
				text.append("!");

				Intent i = new Intent(context, BusTimesActivity.class);
				i.putExtra("StopCode", stopCode);
				i.putExtra("StopName", stopName);
				PendingIntent contentIntent = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);

				Notification notification = new Notification(R.drawable.tracker_24x24_masked, text, System.currentTimeMillis());
				notification.defaults |= Notification.DEFAULT_ALL;
				notification.flags |= Notification.FLAG_AUTO_CANCEL;
				notification.setLatestEventInfo(context, "Bus alarm!", text, contentIntent);

				NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				nm.notify(BusTimesActivity.ALERT_NOTIFICATION_ID, notification);
				return;
			}
		}

		// nothing matched, just reschedule it
		rescheduleAlarm();
	}
}
