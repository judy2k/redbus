/*
 * Copyright 2010 Andrew De Quincey -  adq@lidskialf.net
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

package org.redbus;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class StopBookmarksActivity extends ListActivity 
{	
	private static final String[] columnNames = new String[] { LocalDBHelper.BOOKMARKS_ID, LocalDBHelper.BOOKMARKS_STOPNAME };
	private static final int[] listViewIds = new int[] { R.id.stopbookmarks_stopcode, R.id.stopbookmarks_name };
	private Cursor listContentsCursor = null;
	private long BookmarkId = -1;
	private String BookmarkName = null;

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stopbookmarks);
        registerForContextMenu(getListView());
	}

	@Override
	protected void onStart() 
	{
		super.onStart();
		
		UpdateBookmarksList();
	}
	
	public void UpdateBookmarksList()
	{
		if (listContentsCursor != null) {
			stopManagingCursor(listContentsCursor);
			listContentsCursor.close();
			listContentsCursor = null;
		}

        LocalDBHelper db = new LocalDBHelper(this, false);
        try {
	        listContentsCursor = db.GetBookmarks();
	        startManagingCursor(listContentsCursor);
	        setListAdapter(new SimpleCursorAdapter(this, R.layout.stopbookmarks_item, listContentsCursor, columnNames, listViewIds));
        } finally {
        	db.close();
        }
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {		
		String stopName = ((TextView) v.findViewById(R.id.stopbookmarks_name)).getText().toString();
		BusTimesActivity.showActivity(this, id, stopName);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.stopbookmarks_item_menu, menu);	    
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		BookmarkId = menuInfo.id;
		BookmarkName = ((TextView) menuInfo.targetView.findViewById(R.id.stopbookmarks_name)).getText().toString();
		
		switch(item.getItemId()) {
		case R.id.stopbookmarks_item_menu_bustimes:
			Intent i = new Intent(this, BusTimesActivity.class);
			i.putExtra("StopCode", BookmarkId);
			i.putExtra("StopName", BookmarkName);
			startActivity(i);
			return true;

		case R.id.stopbookmarks_item_menu_showonmap:
			// FIXME: implement
			return true;

		case R.id.stopbookmarks_item_menu_edit:
			// FIXME: implement
			return true;

		case R.id.stopbookmarks_item_menu_delete:
			new AlertDialog.Builder(this).
				setMessage("Are you sure you want to delete this bookmark?").
				setNegativeButton(android.R.string.cancel, null).
				setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        LocalDBHelper db = new LocalDBHelper(StopBookmarksActivity.this, true);
                        try {
                        	db.DeleteBookmark(StopBookmarksActivity.this.BookmarkId);
                        } finally {
                        	db.close();
                        }
                        StopBookmarksActivity.this.UpdateBookmarksList();
                    }
				}).
                show();
			return true;		
		}

		return super.onContextItemSelected(item);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {		
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.stopbookmarks_menu, menu);
	    return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.stopbookmarks_menu_nearby_stops:
			startActivity(new Intent(this, FindNearestStopActivity.class));
			return true;

		case R.id.stopbookmarks_menu_bustimes:
			startActivity(new Intent(this, BusTimesActivity.class));
			return true;
		}
		
		return false;
	}
}
