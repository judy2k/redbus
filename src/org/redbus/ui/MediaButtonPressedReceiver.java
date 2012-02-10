/*
 * Copyright 2012 Colin Paton - cozzarp@gmail.com
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Start speaking location on media button press
public class MediaButtonPressedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent receivedIntent) {
    	Intent startIntent = new Intent(context,SpeakWhereIamActivity.class);
    	startIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(startIntent);
    }
}
