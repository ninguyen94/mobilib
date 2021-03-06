package com.datdo.mobilib.imageinput;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.datdo.mobilib.util.MblUtils;

// reference: https://github.com/jerickson314/sdscanner
// work in 4.4 kitkat
class MblImagePickingScanEngine {

    private static final String TAG = MblUtils.getTag(MblImagePickingScanEngine.class);

    private static final String THUMBNAIL_FOLDER = ".thumbnails";

    private static Pattern sImagePattern;
    static {
        StringBuilder imageRegExBuilder = new StringBuilder();
        imageRegExBuilder.append("^.*\\.(?i)(");
        String[] extensions = new String[MblImageInput.sExtensionsOfPickedImages.length];
        for (int i = 0; i < MblImageInput.sExtensionsOfPickedImages.length; i++) {
            extensions[i] = Pattern.quote(MblImageInput.sExtensionsOfPickedImages[i]);
        }
        imageRegExBuilder.append(TextUtils.join("|", extensions));
        imageRegExBuilder.append(")$");

        sImagePattern = Pattern.compile(imageRegExBuilder.toString());
    }

    public static String buildMediaQuerySelection(String[] imageFolders) {
        StringBuilder ret = new StringBuilder();

        // filter folder
        ret.append(MediaStore.Images.Media.DATA + " NOT LIKE ?");
        ret.append(" AND (");
        for (int i = 0; i < imageFolders.length; i++) {
            if (i > 0) ret.append(" OR ");
            ret.append(MediaStore.Images.Media.DATA + " LIKE ?");
        }
        ret.append(")");

        // filter extentions
        ret.append(" AND (");
        for (int i = 0; i < MblImageInput.sExtensionsOfPickedImages.length; i++) {
            if (i > 0) ret.append(" OR ");
            ret.append("(" + MediaStore.Images.Media.DATA + " LIKE ? OR " + MediaStore.Images.Media.DATA + " LIKE ?)");
        }
        ret.append(")");

        return ret.toString();
    }

    public static String[] buildMediaQuerySelectionArgs(String[] imageFolders) {
        List<String> ret = new ArrayList<String>();

        // arguments for folder filter
        ret.add("%/" + THUMBNAIL_FOLDER + "/%");
        for (String folder : imageFolders) {
            ret.add(folder + "%");
        }

        // arguments for extension filter
        for (String ext : MblImageInput.sExtensionsOfPickedImages) {
            ret.add("%." + ext.toLowerCase(Locale.US));
            ret.add("%." + ext.toUpperCase(Locale.US));
        }

        return ret.toArray(new String[ret.size()]);
    }

    public static interface CmScanCallback {
        public void onFinish(int nUpdatedFiles);
        public void onFailure();
    }

    public static void scan(final String[] imageFolders, final CmScanCallback callback) {

        final Context context = MblUtils.getCurrentContext();

        MblUtils.executeOnAsyncThread(new Runnable() {

            private Set<File> mFilesToProcess = new TreeSet<File>();
            private int mNumberOfScannedFiles;

            private void recursiveAddFiles(File file) throws IOException {

                if (file.isDirectory()) {

                    // do not care ".thumbnails" folder
                    if (file.getPath().endsWith(THUMBNAIL_FOLDER)) return;

                    // only recurse downward if not blocked by nomedia.
                    boolean nomedia = new File(file, ".nomedia").exists();
                    if (!nomedia) {
                        for (File nextFile : file.listFiles()) {
                            recursiveAddFiles(nextFile);
                        }
                    }
                } else {
                    if (sImagePattern.matcher(file.getName()).matches()) {
                        mFilesToProcess.add(file.getCanonicalFile());
                    }
                }
            }

            @Override
            public void run() {
                try {

                    try {
                        for (String f : imageFolders) {
                            recursiveAddFiles(new File(f));
                        }
                    } catch (IOException ignored) {}

                    Cursor cursor = context.getContentResolver().query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[] {
                                    MediaStore.Images.Media.DATA,
                                    MediaStore.Images.Media.DATE_MODIFIED
                            },
                            buildMediaQuerySelection(imageFolders),
                            buildMediaQuerySelectionArgs(imageFolders),
                            null);
                    int dataColumn =
                            cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                    int modifiedColumn =
                            cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);

                    try {
                        while (cursor.moveToNext()) {

                            File file = new File(cursor.getString(dataColumn)).getCanonicalFile();

                            if (    !file.exists() ||
                                    file.lastModified() > 1000L * cursor.getLong(modifiedColumn)    ) {

                                // media scanner handles these cases.
                                mFilesToProcess.add(file);
                            } else {
                                // don't want to waste time scanning an up-to-date file
                                mFilesToProcess.remove(file);
                            }
                        }
                    } catch (IOException ignored) {}

                    // don't need the cursor any more.
                    cursor.close();

                    // scan files
                    if (!mFilesToProcess.isEmpty()) {

                        final int n = mFilesToProcess.size();
                        final String[] paths = new String[n];
                        int i = 0;
                        for (File file : mFilesToProcess) {
                            paths[i++] = file.getPath();
                        }

                        mNumberOfScannedFiles = 0;
                        MediaScannerConnection.scanFile(
                                context.getApplicationContext(),
                                paths,
                                null,
                                new MediaScannerConnection.OnScanCompletedListener() {
                                    public void onScanCompleted(String path, Uri uri) {
                                        mNumberOfScannedFiles++;
                                        if (mNumberOfScannedFiles >= n) {
                                            if (callback != null) {
                                                MblUtils.executeOnMainThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        callback.onFinish(n);
                                                    }
                                                });
                                            }
                                        }
                                    }
                                });
                    } else {
                        if (callback != null) {
                            MblUtils.executeOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onFinish(0);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Can not scan media files", e);
                    if (callback != null) {
                        MblUtils.executeOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onFailure();
                            }
                        });
                    }
                }
            }
        });
    }
}