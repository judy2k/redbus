package org.redbus.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;

public class BusyDialog {
	
	public static ProgressDialog show(Context ctx, OnCancelListener onCancel, ProgressDialog oldPd, String reason) {
		dismiss(oldPd);
		return ProgressDialog.show(ctx, "", reason, true, true, onCancel);
	}

	public static void dismiss(ProgressDialog pd) {
		if (pd != null) {
			try {
				pd.dismiss();
			} catch (Throwable t) {
			}
		}
	}
}
