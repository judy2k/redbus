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

package org.redbus.settings;

import java.io.FileReader;
import java.io.FileWriter;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.util.Log;
import android.util.Xml;

public class SettingsAccessor 
{
	public static final String BOOKMARKS_TABLE = "Bookmarks";
	public static final String SETTINGS_TABLE = "Settings";
	
	public static final String ID = "_id";
	public static final String BOOKMARKS_COL_STOPNAME = "StopName";

	public static final String SETTINGS_COL_STOPCODE = "StopCode";
	public static final String SETTINGS_COL_SETTINGNAME = "SettingName";
	public static final String SETTINGS_COL_SETTINGVALUE = "SettingValue";

	private SQLiteDatabase db;
	
	public SettingsAccessor(Context context)
	{
		db = new SettingsDbOpenHelper(context).getWritableDatabase();
	}
	
	public void close()
	{
		if (db != null)
			db.close();
		db = null;
	}


	public boolean isBookmark(long stopCode)
	{
		Cursor c = null;
		try {
			c = db.query(BOOKMARKS_TABLE, 
						new String[] { ID }, 
						"_id = ?",
						new String[] { Long.toString(stopCode) }, 
						null, 
						null, 
						null);
			return c.moveToNext();
		} finally {
			if (c != null)
				c.close();
		}
	}

	public String getBookmarkName(long stopCode)
	{
		Cursor c = null;
		try {
			c = db.query(BOOKMARKS_TABLE, 
						new String[] { BOOKMARKS_COL_STOPNAME }, 
						"_id = ?",
						new String[] { Long.toString(stopCode) }, 
						null, 
						null, 
						null);
			if (c.moveToNext())
				return c.getString(0);
			else
				return null;
		} finally {
			if (c != null)
				c.close();
		}
	}

	public Cursor getBookmarks()
	{
		return db.query(BOOKMARKS_TABLE, null, null, null, null, null, BOOKMARKS_COL_STOPNAME);
	}

	public void deleteBookmark(long bookmarkId) {
		db.execSQL("DELETE FROM Bookmarks WHERE _id = ?", new Object[] { bookmarkId });
	}
	
	public void deleteBookmarks() {
		db.execSQL("DELETE FROM Bookmarks");
	}
	
	public void addBookmark(long bookmarkId, String stopName) {
		try {
			db.execSQL("INSERT INTO Bookmarks VALUES (?, ?)", new Object[] { bookmarkId, stopName });
		} catch (Exception ex) {
		}
	}
	
	public void renameBookmark(long bookmarkId, String stopName) {
		try {
			db.execSQL("UPDATE Bookmarks SET StopName=? WHERE _id = ?", new Object[] { stopName, bookmarkId });
		} catch (Exception ex) {
		}
	}
	
	public String getGlobalSetting(String name, String defaultValue)
	{
		return getBusStopSetting(-1, name, defaultValue);
	}
	
	public void setGlobalSetting(String name, String value)
	{
		setBusStopSetting(-1, name, value);
	}
	
	public void deleteGlobalSetting(String name)
	{
		deleteBusStopSetting(-1, name);
	}

	public String getBusStopSetting(long stopCode, String name, String defaultValue)
	{
		String result = defaultValue;
		
		Cursor c = null;
		try {
			c = db.query(SETTINGS_TABLE, 
						new String[] { SETTINGS_COL_SETTINGVALUE }, 
						"StopCode = ? AND SettingName = ?",
						new String[] { Long.toString(stopCode), name }, 
						null, 
						null, 
						null);
			if (c.moveToNext())
				result = c.getString(0);
		} finally {
			if (c != null)
				c.close();
		}
		
		return result;
	}

	public void setBusStopSetting(long stopCode, String name, String value)
	{
		deleteBusStopSetting(stopCode, name);
		db.execSQL("INSERT INTO Settings (StopCode, SettingName, SettingValue) VALUES (?, ?, ?)", new Object[] {stopCode, name, value});
	}

	public void deleteBusStopSetting(long stopCode, String name)
	{
		db.execSQL("DELETE FROM Settings WHERE StopCode = ? AND SettingName = ?", new Object[] { stopCode, name });
	}

	public void deleteBusStopSettings(long stopCode)
	{
		db.execSQL("DELETE FROM Settings WHERE StopCode = ?", new Object[] { stopCode});
	}
	
	public boolean backup(String filename) {
        Cursor c = null;
        FileWriter output = null;
        try {
        	output = new FileWriter(filename);
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(output);
            serializer.startDocument("UTF-8", true);
            serializer.startTag("", "redbus");

	        c = getBookmarks();
	        while(c.moveToNext()) {
                serializer.startTag("", "busstop");
                serializer.attribute("", "stopcode", Long.toString(c.getLong(0)));
                serializer.attribute("", "name", c.getString(1));
                serializer.endTag("", "busstop");
	        }
            serializer.endTag("", "redbus");
            serializer.endDocument();
        } catch (Throwable t) {
        	Log.e("StopBookmarks.Backup", "Backup failed", t);
        	return false;
        } finally {
        	if (output != null) {
        		try {
	        		output.flush();
        		} catch (Throwable t) {}
        		try {
	        		output.close();
        		} catch (Throwable t) {}
        	}
        	if (c != null)
        		c.close();
        	db.close();
        }
        
        return true;
	}
	
	public boolean restore(String filename) {
        FileReader inputFile = null;
        try {
        	inputFile = new FileReader(filename);
        	
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(inputFile);
			deleteBookmarks();

			while(parser.next() != XmlPullParser.END_DOCUMENT) {
				switch(parser.getEventType()) {
				case XmlPullParser.START_TAG:
					String tagName = parser.getName();
					if (tagName == "busstop") {
						long stopCode = Long.parseLong(parser.getAttributeValue(null, "stopcode"));
						String stopName = parser.getAttributeValue(null, "name");
						addBookmark(stopCode, stopName);
					}
				}
			}
        } catch (Throwable t) {
        	Log.e("StopBookmarks.Restore", "Restore failed", t);
        	return false;
        } finally {
        	if (inputFile != null) {
        		try {
        			inputFile.close();
        		} catch (Throwable t) {}
        	}
        	db.close();
        }
        
        return true;
	}



	private static class SettingsDbOpenHelper extends SQLiteOpenHelper {
	    public static final String DATABASE_NAME = "rEdBusDB.db";
	    public static final int DATABASE_VERSION = 2;
	    
	    public static final String CREATE_BOOKMARKS_TABLE_SQL = 
	    	"CREATE TABLE Bookmarks (_id integer primary key, StopName TEXT)";
	    public static final String CREATE_SETTINGS_TABLE_SQL = 
	    	"CREATE TABLE Settings (_id integer primary key autoincrement, StopCode integer, SettingName TEXT, SettingValue TEXT)";

	    public static final String[] CREATE_TABLE_SQL = {
	    	CREATE_BOOKMARKS_TABLE_SQL,
	    	CREATE_SETTINGS_TABLE_SQL
	    };

	    public SettingsDbOpenHelper(Context context) {
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
			if ((oldVersion == 1) && (newVersion == 2))
				db.execSQL(CREATE_SETTINGS_TABLE_SQL);
		}
	}
}
