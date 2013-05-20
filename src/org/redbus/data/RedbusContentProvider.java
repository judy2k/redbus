package org.redbus.data;


import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import org.redbus.settings.SettingsHelper;

public class RedbusContentProvider extends ContentProvider {
    public static final Uri BOOKMARKS_URI = Uri.parse("content://org.redbus/bookmarks");

    private static final int ALL_BOOKMARKS = 1;
    private static final int SINGLE_BOOKMARK = 2;

    private static UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        uriMatcher.addURI("org.redbus", "bookmarks", ALL_BOOKMARKS);
    }

    private SettingsHelper db;

    @Override
    public boolean onCreate() {
        db = new SettingsHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        int match = uriMatcher.match(uri);

        switch (match) {
            case ALL_BOOKMARKS:
                return db.getBookmarks();
            default:
                return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        int match = uriMatcher.match(uri);

        switch (match) {
            case ALL_BOOKMARKS:
                return "vnd.android.cursor.dir/vnd.redbus.bookmark";
            default:
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
