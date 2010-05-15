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

import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class StopBookmarksActivity extends ListActivity implements BusDataResponseListener
{	
	private static final String[] columnNames = new String[] { LocalDBHelper.ID, LocalDBHelper.BOOKMARKS_COL_STOPNAME };
	private static final int[] listViewIds = new int[] { R.id.stopbookmarks_stopcode, R.id.stopbookmarks_name };
	private Cursor listContentsCursor = null;
	private long bookmarkId = -1;
	private String bookmarkName = null;
	private ProgressDialog busyDialog = null;
	private int expectedRequestId = -1;

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
        super.onCreate(savedInstanceState);
        setTitle("Bookmarks");
        setContentView(R.layout.stopbookmarks);
        registerForContextMenu(getListView());
	}

	@Override
	protected void onStart() 
	{
		super.onStart();
		
		update();
	}
	
	private void update()
	{
		if (listContentsCursor != null) {
			stopManagingCursor(listContentsCursor);
			listContentsCursor.close();
			listContentsCursor = null;
		}

        LocalDBHelper db = new LocalDBHelper(this, false);
        try {
	        listContentsCursor = db.getBookmarks();
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
		bookmarkId = menuInfo.id;
		bookmarkName = ((TextView) menuInfo.targetView.findViewById(R.id.stopbookmarks_name)).getText().toString();
		
		switch(item.getItemId()) {
		case R.id.stopbookmarks_item_menu_bustimes:
			Intent i = new Intent(this, BusTimesActivity.class);
			i.putExtra("StopCode", bookmarkId);
			i.putExtra("StopName", bookmarkName);
			startActivity(i);
			return true;

		case R.id.stopbookmarks_item_menu_showonmap:
			// FIXME: implement
			return true;

		case R.id.stopbookmarks_item_menu_edit:
			final EditText input = new EditText(this);
			input.setText(bookmarkName);

			new AlertDialog.Builder(this)
					.setTitle("Edit bookmark name")
					.setView(input)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int whichButton) {
			                        LocalDBHelper db = new LocalDBHelper(StopBookmarksActivity.this, true);
			                        try {
			                        	db.renameBookmark(StopBookmarksActivity.this.bookmarkId, input.getText().toString());
			                        } finally {
			                        	db.close();
			                        }
			                        StopBookmarksActivity.this.update();
								}
							})
					.setNegativeButton(android.R.string.cancel, null)
					.show();
			return true;

		case R.id.stopbookmarks_item_menu_delete:
			new AlertDialog.Builder(this).
				setMessage("Are you sure you want to delete this bookmark?").
				setNegativeButton(android.R.string.cancel, null).
				setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        LocalDBHelper db = new LocalDBHelper(StopBookmarksActivity.this, true);
                        try {
                        	db.deleteBookmark(StopBookmarksActivity.this.bookmarkId);
                        } finally {
                        	db.close();
                        }
                        StopBookmarksActivity.this.update();
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
			new AlertDialog.Builder(this).
				setMessage("This is an experimental unoptimised feature under heavy development; are you sure you wish to continue?").
				setNegativeButton(android.R.string.cancel, null).
				setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	        			startActivity(new Intent(StopBookmarksActivity.this, FindNearestStopActivity.class));
	                }
				}).
	            show();
			return true;

		case R.id.stopbookmarks_menu_bustimes:
			startActivity(new Intent(this, BusTimesActivity.class));
			return true;
			
		case R.id.stopbookmarks_menu_addbookmark:
			final EditText input = new EditText(this);

			new AlertDialog.Builder(this)
				.setTitle("Enter BusStop code for bookmark")
				.setView(input)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								long stopCode = -1;
								try {
									stopCode = Long.parseLong(input.getText().toString());
								} catch (Exception ex) {
									new AlertDialog.Builder(StopBookmarksActivity.this)
											.setTitle("Invalid BusStop code")
											.setMessage("The code was invalid; please try again using only numbers")
											.setPositiveButton(android.R.string.ok, null)
											.show();
									return;
								}
								
								displayBusy("Validating BusStop code");
								StopBookmarksActivity.this.expectedRequestId = BusDataHelper.getStopNameAsync(stopCode, StopBookmarksActivity.this);
							}
						})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
			break;
		}
		
		return false;
	}

	public void getBusTimesError(int requestId, int code, String message) {
		// unused
	}

	public void getBusTimesSuccess(int requestId, List<BusTime> busTimes) {
		// unused
	}

	public void getStopNameError(int requestId, int code, String message) {
		if (requestId != expectedRequestId)
			return;

		dismissBusy();

		new AlertDialog.Builder(this).setTitle("Error")
			.setMessage("Unable to validate BusStop code: " + message)
			.setPositiveButton(android.R.string.ok, null)
			.show();
	}

	public void getStopNameSuccess(int requestId, long stopCode, String stopName) {
		if (requestId != expectedRequestId)
			return;

		dismissBusy();

		LocalDBHelper db = new LocalDBHelper(this, false);
		try {
			db.addBookmark(stopCode, stopName);
		} finally {
			db.close();
		}
		update();
	}

	private void displayBusy(String reason) {
		dismissBusy();

		busyDialog = ProgressDialog.show(this, "", reason, true, true, new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				StopBookmarksActivity.this.expectedRequestId = -1;
			}
		});
	}

	private void dismissBusy() {
		if (busyDialog != null) {
			try {
				busyDialog.dismiss();
			} catch (Throwable t) {
			}
			busyDialog = null;
		}
	}
}
