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

package org.redbus.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;

public class BusyDialog {
	
	private ProgressDialog pd;
	private Context ctx;
	
	public BusyDialog(Context ctx) {
		this.ctx = ctx;
	}
	
	public void show(OnCancelListener onCancel, String reason) {
		dismiss();
		pd = ProgressDialog.show(ctx, "", reason, true, true, onCancel);
	}

	public void dismiss() {
		if (pd != null) {
			try {
				pd.dismiss();
			} catch (Throwable t) {
			}
		}
		pd = null;
	}
}
