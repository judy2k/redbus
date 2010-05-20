package org.redbus;

import java.util.HashMap;
import java.util.List;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class TemporalAlarmReceiver extends BroadcastReceiver implements
		BusDataResponseListener {

	private Context context;
	private Intent intent;

	private static final int TEMPORAL_NOTIFICATION_ID = 1;

	private static final int ALARM_MAX_TIMEOUT_MSEC = 20 * 60 * 1000;
	
	@Override
	public void onReceive(Context context, Intent intent) {
		this.context = context;
		this.intent = intent;

		long stopCode = intent.getLongExtra("StopCode", -1);
		if (stopCode == -1)
			return;

		BusDataHelper.getBusTimesAsync(stopCode, 0, null, this);
	}

	private void rescheduleAlarm() {
		// make sure alarms don't run forever
		long startTime = intent.getLongExtra("StartTime", -1);
		if ((startTime == -1) || ((startTime + ALARM_MAX_TIMEOUT_MSEC) < System.currentTimeMillis()))
				return;
		// FIXME: should warn the user the alarm is giving up

		// schedule it in 60 seconds
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, 0);
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60000, pi);
	}

	public void getBusTimesError(int requestId, int code, String message) {
		rescheduleAlarm();
	}

	public void getBusTimesSuccess(int requestId, List<BusTime> busTimes) {
		String[] requestedServices = intent.getStringArrayExtra("Services");
		int timeout = intent.getIntExtra("TimeoutSecs", -1);
		if ((requestedServices == null) || (timeout == -1))
			return;
		HashMap<String, Boolean> requestedServicesLookup = new HashMap<String, Boolean>();
		for (String curService : requestedServices)
			requestedServicesLookup.put(curService.toLowerCase(), new Boolean(
					true));

		for (BusTime curTime : busTimes) {
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
					text.append("minutes");
				}
				text.append("!");
				
				Intent notificationIntent = new Intent("REDBUS_DONOTHING");
				PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
				
				Notification notification = new Notification(R.drawable.tracker_24x24_masked, text, System.currentTimeMillis());
				notification.defaults |= Notification.DEFAULT_ALL;
				notification.flags |= Notification.FLAG_AUTO_CANCEL;
				notification.setLatestEventInfo(context, "Bus alert!", text, contentIntent);

				NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				nm.notify(TEMPORAL_NOTIFICATION_ID, notification);
				return;
			}
		}
		
		// nothing matched, just reschedule it
		rescheduleAlarm();
	}
}
