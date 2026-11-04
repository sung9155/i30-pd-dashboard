package com.i30.installer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lends one file from files/diag to the mail app the user picks (read only, by a one-time grant; not exported).
 * Does what androidx FileProvider would, without a Gradle build.
 */
public final class DiagProvider extends ContentProvider {
    static final String AUTHORITY = "com.i30.installer.diag";

    static Uri uri(File f) { return Uri.parse("content://" + AUTHORITY + "/" + Uri.encode(f.getName())); }

    private File file(Uri u) throws FileNotFoundException {
        File dir = new File(getContext().getFilesDir(), "diag"), f = new File(dir, String.valueOf(u.getLastPathSegment()));
        if (!dir.equals(f.getParentFile()) || !f.isFile()) throw new FileNotFoundException(String.valueOf(u));
        return f;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri u) { return String.valueOf(u.getLastPathSegment()).endsWith(".zip") ? "application/zip" : "text/plain"; }

    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(u), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** Mail apps ask for the attachment's name and size first. */
    @Override public Cursor query(Uri u, String[] projection, String selection, String[] args, String order) {
        try {
            File f = file(u);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) { return null; }
    }

    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
