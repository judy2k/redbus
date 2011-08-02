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
	
		        // If the key is ours (for saving top score). Note this key was used when
		        // we wrote the backup entity header
		        if (BOOKMARKS_KEY.equals(key)) {
		            // Create an input stream for the BackupDataInput
		            byte[] dataBuf = new byte[dataSize];
		            data.readEntityData(dataBuf, 0, dataSize);
		            ByteArrayInputStream baStream = new ByteArrayInputStream(dataBuf);
		            InputStreamReader reader = new InputStreamReader(baStream);
	
		            db.restore(reader, true);
		        } else {
		            // We don't know this entity key. Skip it. (Shouldn't happen.)
		            data.skipEntityData();
		        }
		    }
		} finally {
			db.close();
		}
	}
}
