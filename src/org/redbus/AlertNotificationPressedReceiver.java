package org.redbus;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class AlertNotificationPressedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(BusTimesActivity.ALERT_NOTIFICATION_ID);

		BusTimesActivity.cancelAlerts(context);
		Toast.makeText(context, "Alarm cancelled!", Toast.LENGTH_SHORT).show();
	}
}
