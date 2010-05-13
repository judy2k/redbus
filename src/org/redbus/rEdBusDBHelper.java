package org.redbus;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

public class rEdBusDBHelper 
{
	public static final String BOOKMARKS = "Bookmarks";
	public static final String BOOKMARKS_ID = "_id";
	public static final String BOOKMARKS_STOPNAME = "StopName";
	private SQLiteDatabase db;
	
	public rEdBusDBHelper(Context context, boolean writeble)
	{
		if (writeble)
			db = new rEdBusDBOpenHelper(context).getWritableDatabase();
		else
			db = new rEdBusDBOpenHelper(context).getReadableDatabase();			
	}
	
	public void close()
	{
		if (db != null)
			db.close();
		db = null;
	}
	
	public Cursor GetBookmarks()
	{
		return db.query(BOOKMARKS, null, null, null, null, null, BOOKMARKS_STOPNAME);
	}

	public void DeleteBookmark(long bookmarkId) {
		db.execSQL("DELETE FROM Bookmarks WHERE _id = " + bookmarkId);
	}
}
