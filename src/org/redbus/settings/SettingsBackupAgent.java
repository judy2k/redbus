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

package org.redbus.settings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;

public class SettingsBackupAgent extends BackupAgent {

	private static final String BOOKMARKS_KEY = "bookmarks";
	
	@Override
	public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {
		SettingsHelper db = null;
		try {
			db = new SettingsHelper(this);

			// write out the bookmarks
			ByteArrayOutputStream bookmarksStream = new ByteArrayOutputStream();
			OutputStreamWriter bookmarksWriter = new OutputStreamWriter(bookmarksStream);
			db.backup(bookmarksWriter);
			bookmarksWriter.flush();

			// save it to backup system
			byte[] buffer = bookmarksStream.toByteArray();
			data.writeEntityHeader(BOOKMARKS_KEY, buffer.length);
			data.writeEntityData(buffer, buffer.length);
		} finally {
			db.close();
		}
	}

	@Override
	public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {
		SettingsHelper db = null;
		try {
			db = new SettingsHelper(this);
	
		    while (data.readNextHeader()) {
		        String key = data.getKey();
		        int dataSize = data.getDataSize();
	
		        if (BOOKMARKS_KEY.equals(key)) {
		            byte[] dataBuf = new byte[dataSize];
		            data.readEntityData(dataBuf, 0, dataSize);
		            ByteArrayInputStream baStream = new ByteArrayInputStream(dataBuf);
		            InputStreamReader reader = new InputStreamReader(baStream);
	
		            db.restore(reader, true);
		        } else {
		            data.skipEntityData();
		        }
		    }
		} finally {
			db.close();
		}
	}
}
