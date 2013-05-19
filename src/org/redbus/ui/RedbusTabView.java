/*
 * Copyright 2011 Colin Paton - cozzarp@googlemail.com
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

import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTabHost;
import android.util.Log;
import org.redbus.R;
import org.redbus.settings.SettingsHelper;
import org.redbus.ui.arrivaltime.NearbyBookmarkedArrivalTimeActivity;
import org.redbus.ui.stopmap.StopMapActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

public class RedbusTabView extends FragmentActivity {
    private static final String TAG = "RedbusTabView";
    private FragmentTabHost tabHost;

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

        setContentView(R.layout.redbustablayout);

        tabHost = (FragmentTabHost)findViewById(android.R.id.tabhost);
        tabHost.setup(this, getSupportFragmentManager(), R.id.realtabcontent);
	    
		SettingsHelper db = new SettingsHelper(this);
		boolean tabsEnabled = db.getGlobalSetting("TABSENABLED", "true").equals("true");
		db.close();
		if (!tabsEnabled) {
			startActivity(new Intent().setClass(this, BookmarksActivity.class));
			finish();
			return;
		}

	    tabHost.addTab(tabHost.newTabSpec("bookmarks").setIndicator("Bookmarks"),
                BookmarksFragment.class, null);
            //.setContent(new Intent().setClass(this, BookmarksActivity.class)));

//	    tabHost.addTab(tabHost.newTabSpec("map").setIndicator("Map"),
//                StopMapActivity.class, null);
//	                  //.setContent(new Intent().setClass(this, StopMapActivity.class)));
//
//	    tabHost.addTab(tabHost.newTabSpec("nearby").setIndicator("Nearby"),
//                NearbyBookmarkedArrivalTimeActivity.class, null);
//                //.setContent(new Intent().setClass(this, NearbyBookmarkedArrivalTimeActivity.class)));
	}

    private FragmentTabHost getTabHost() {
        return this.tabHost;
    }
	
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		// make the back button switch to bookmarks tab unless we're already on bookmarks tab
	    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
	    	if (getTabHost().getCurrentTab() != 0) {
	    		getTabHost().setCurrentTab(0);
		        return true;	    		
	    	}
	    }
		
		return super.dispatchKeyEvent(event);
	}
}
