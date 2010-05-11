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
	
	public rEdBusDBHelper(Context context)
	{
		db = new rEdBusDBOpenHelper(context).getWritableDatabase();
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
}
