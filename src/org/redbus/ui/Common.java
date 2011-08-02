package org.redbus.ui;

import org.redbus.R;
import org.redbus.settings.SettingsHelper;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.widget.EditText;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class Common 
{
	private static final String[] columnNames = new String[] { SettingsHelper.ID, SettingsHelper.BOOKMARKS_COL_STOPNAME };
	private static final int[] listViewIds = new int[] { R.id.stopbookmarks_stopcode, R.id.stopbookmarks_name };
	
	public static void updateBookmarksListAdaptor(ListActivity la)
	{
    	SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter) la.getListAdapter();
    	if (cursorAdapter == null) {
            SettingsHelper db = new SettingsHelper(la);
	        Cursor listContentsCursor = db.getBookmarks();
	        la.startManagingCursor(listContentsCursor);
	        la.setListAdapter(new SimpleCursorAdapter(la, R.layout.stopbookmarks_item, listContentsCursor, columnNames, listViewIds));
    	} else {
    		cursorAdapter.getCursor().requery();
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
