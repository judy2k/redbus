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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;

public class LocalDBHelper 
{
	public static final String BOOKMARKS = "Bookmarks";
	public static final String BOOKMARKS_ID = "_id";
	public static final String BOOKMARKS_STOPNAME = "StopName";
	private SQLiteDatabase db;
	
	public LocalDBHelper(Context context, boolean writeble)
	{
		if (writeble)
			db = new LocalDBOpenHelper(context).getWritableDatabase();
		else
			db = new LocalDBOpenHelper(context).getReadableDatabase();			
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
		db.execSQL("DELETE FROM Bookmarks WHERE _id = ?", new Object[] { bookmarkId });
	}
	
	public void AddBookmark(long bookmarkId, String stopName) {
		try {
			db.execSQL("INSERT INTO Bookmarks VALUES (?, ?)", new Object[] { bookmarkId, stopName });
		} catch (Exception ex) {
		}
	}
	
	
	private static class LocalDBOpenHelper extends SQLiteOpenHelper {
	    public static final String DATABASE_NAME = "rEdBusDB.db";
	    public static final int DATABASE_VERSION = 1;
	    
	    public static final String[] CREATE_TABLE_SQL = {
	    	"CREATE TABLE Bookmarks (_id integer primary key, StopName TEXT)",
	    };

	    public LocalDBOpenHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			int length = CREATE_TABLE_SQL.length;
			for (int i = 0; i < length; i++) {
				db.execSQL(CREATE_TABLE_SQL[i]);
			}
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		}
	}
}
