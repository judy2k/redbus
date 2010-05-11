package org.redbus;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class StopBookmarks extends ListActivity 
{	
	private static final String[] columnNames = new String[] { rEdBusDBHelper.BOOKMARKS_ID, rEdBusDBHelper.BOOKMARKS_STOPNAME };
	private static final int[] listViewIds = new int[] { R.id.stopbookmarks_stopcode, R.id.stopbookmarks_name };
	private Cursor listContentsCursor = null;

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
		
		if (listContentsCursor != null) {
			stopManagingCursor(listContentsCursor);
			listContentsCursor.close();
		}
		
        rEdBusDBHelper db = new rEdBusDBHelper(this, false);
        try {
	        listContentsCursor = db.GetBookmarks();
	        startManagingCursor(listContentsCursor);
	        setListAdapter(new SimpleCursorAdapter(this, R.layout.stopbookmarks_entry, listContentsCursor, columnNames, listViewIds));
        } finally {
        	db.close();
        }
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {		
		Toast.makeText(this, "HELLO", Toast.LENGTH_SHORT).show();
		
		// FIXME: go to (view stop times activity)
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		Toast.makeText(this, "HELLOCTX", Toast.LENGTH_SHORT).show();

		// FIXME: view next bus times for this stop (view stop times activity)
		// FIXME: view this stop on map
		// FIXME: edit this bookmark
		// FIXME: remove this bookmark
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Toast.makeText(this, "HELLOMENU", Toast.LENGTH_SHORT).show();

		// FIXME: show stops near me on map
		// FIXME: add stop bookmark by stopcode		
		// FIXME: view bus times for stopcode (view stop times activity)
		// ???
		// Profit!

		return true;
	}
}
