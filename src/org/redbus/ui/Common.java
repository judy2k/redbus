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

import org.redbus.R;
import org.redbus.settings.SettingsHelper;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.view.View;
import android.widget.EditText;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class Common 
{
	private static final String[] columnNames = new String[] { SettingsHelper.ID, SettingsHelper.BOOKMARKS_COL_STOPNAME, SettingsHelper.ID };
	private static final int[] listViewIds = new int[] { R.id.stopbookmarks_stopcode, R.id.stopbookmarks_name, R.id.stopbookmarks_edit };
	
	public static SettingsHelper updateBookmarksListAdaptor(ListActivity la, View.OnClickListener editClickListener)
	{
		final View.OnClickListener localEditClickListener = editClickListener;
		
    	SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter) la.getListAdapter();
    	if (cursorAdapter == null) {
            SettingsHelper db = new SettingsHelper(la);
	        Cursor listContentsCursor = db.getBookmarks();
	        la.startManagingCursor(listContentsCursor);
	        SimpleCursorAdapter sca = new SimpleCursorAdapter(la, R.layout.stopbookmarks_item, listContentsCursor, columnNames, listViewIds);
	        sca.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
	    		public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
	    			if ((view.getId() == R.id.stopbookmarks_edit) && (localEditClickListener != null)) {
	    				view.setOnClickListener(localEditClickListener);
	    				return true;
	    			}
	    			return false;
	    		}
	        });
	        la.setListAdapter(sca);	        	
	        return db;
    	} else {
    		cursorAdapter.getCursor().requery();
    		return null;
    	}
	}
	
	public static void destroyBookmarksListAdaptor(ListActivity la, SettingsHelper db)
	{
		try {
	    	SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter) la.getListAdapter();
	    	if (cursorAdapter != null)
	    		la.stopManagingCursor(cursorAdapter.getCursor());
	    	if (db != null)
	    		db.close();
		} catch (Throwable t) {
		}
	}	
	
	
	public static void doAddBookmark(Context ctx, int stopCode, String stopName) {
		if (stopCode == -1) 
			return;
		
		SettingsHelper db = new SettingsHelper(ctx);
		try {
			db.addBookmark(stopCode, stopName);
		} finally {
			db.close();
		}
		Toast.makeText(ctx, "Added bookmark", Toast.LENGTH_SHORT).show();
	}

	public static void doRenameBookmark(Context ctx, int stopCode, String bookmarkName, ICommonResultReceiver result) {
		final int localStopCode = stopCode;
		final EditText input = new EditText(ctx);
		final Context localCtx = ctx;
		final ICommonResultReceiver localResult = result;
		input.setText(bookmarkName);

		new AlertDialog.Builder(localCtx)
				.setTitle("Rename bookmark")
				.setView(input)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
		                        SettingsHelper db = new SettingsHelper(localCtx);
		                        try {
		                        	db.renameBookmark(localStopCode, input.getText().toString());
		                        } finally {
		                        	db.close();
		                        }
		                        try {
		                        	localResult.OnBookmarkRenamedOK(localStopCode);
		                        } catch (Throwable t) {
		                        	
		                        }
							}
						})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}
	
	public static void doDeleteBookmark(Context ctx, int stopCode, ICommonResultReceiver result) {
		final int localStopCode = stopCode;
		final Context localCtx = ctx;
		final ICommonResultReceiver localResult = result;

		new AlertDialog.Builder(ctx)
				.setTitle("Delete bookmark")
				.setMessage("Are you sure you want to delete this bookmark?")
				.setPositiveButton(android.R.string.ok, 
						new DialogInterface.OnClickListener() {
		                    public void onClick(DialogInterface dialog, int whichButton) {
		                        SettingsHelper db = new SettingsHelper(localCtx);
		                        try {
		                        	db.deleteBookmark(localStopCode);
		                        } finally {
		                        	db.close();
		                        }
		                        try {
		                        	localResult.OnBookmarkDeletedOK(localStopCode);
		                        } catch (Throwable t) {
		                        	
		                        }
		                    }
						})
				.setNegativeButton(android.R.string.cancel, null)
		        .show();
	}
}
