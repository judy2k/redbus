package org.redbus;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class rEdBusDBOpenHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "rEdBusDB.db";
    public static final int DATABASE_VERSION = 1;

    public static final String[] TABLES = {
      "Bookmarks",
    };
    
    public static final String[] CREATE_TABLE_SQL = {
    	"CREATE TABLE Bookmarks (_id integer primary key, StopName TEXT)",
    };

    public rEdBusDBOpenHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		// create the tables
		int length = CREATE_TABLE_SQL.length;
		for (int i = 0; i < length; i++) {
			db.execSQL(CREATE_TABLE_SQL[i]);
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}
}
