package com.vios.apex;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlbumArtLoader {
    private static final String TAG = "ApexArtLoader";

    // In-memory LRU cache of decoded album arts
    private static final Map<String, Bitmap> CACHE = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > 25;
        }
    };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static volatile String activeLoadingUri = null;

    /**
     * Resolves album art from MediaMetadata.
     * Checks direct bitmaps first. If null, loads from HTTP/Content URI asynchronously.
     * Sets app icon as fallback if nothing is found.
     */
    public static void loadArt(final Context context,
                               final MediaMetadata metadata,
                               final String packageName,
                               final ImageView imageView,
                               final Handler uiHandler) {

        if (imageView == null) return;

        // 1. Check direct bitmaps in MediaMetadata
        Bitmap directBmp = null;
        if (metadata != null) {
            try { directBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART); } catch (Exception ignored) {}
            if (directBmp == null) {
                try { directBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART); } catch (Exception ignored) {}
            }
            if (directBmp == null) {
                try { directBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON); } catch (Exception ignored) {}
            }
            if (directBmp == null) {
                MediaDescription desc = metadata.getDescription();
                if (desc != null) {
                    try { directBmp = desc.getIconBitmap(); } catch (Exception ignored) {}
                }
            }
        }

        if (directBmp != null && !directBmp.isRecycled()) {
            activeLoadingUri = null;
            imageView.setImageBitmap(directBmp);
            return;
        }

        // 2. Extract URI string from metadata
        String uriStr = null;
        if (metadata != null) {
            try { uriStr = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI); } catch (Exception ignored) {}
            if (uriStr == null) {
                try { uriStr = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI); } catch (Exception ignored) {}
            }
            if (uriStr == null) {
                try { uriStr = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI); } catch (Exception ignored) {}
            }
            if (uriStr == null) {
                MediaDescription desc = metadata.getDescription();
                if (desc != null && desc.getIconUri() != null) {
                    uriStr = desc.getIconUri().toString();
                }
            }
        }

        // 3. If no URI available, show app icon fallback
        if (uriStr == null || uriStr.trim().isEmpty()) {
            activeLoadingUri = null;
            setAppIconFallback(context, packageName, imageView);
            return;
        }

        final String finalUri = uriStr.trim();

        // 4. Check memory cache
        synchronized (CACHE) {
            Bitmap cached = CACHE.get(finalUri);
            if (cached != null && !cached.isRecycled()) {
                activeLoadingUri = null;
                imageView.setImageBitmap(cached);
                return;
            }
        }

        // 5. Show app icon while loading
        setAppIconFallback(context, packageName, imageView);
        activeLoadingUri = finalUri;

        // 6. Asynchronous fetch
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                Bitmap downloaded = null;
                InputStream is = null;
                try {
                    if (finalUri.startsWith("http://") || finalUri.startsWith("https://")) {
                        HttpURLConnection conn = (HttpURLConnection) new URL(finalUri).openConnection();
                        conn.setConnectTimeout(6000);
                        conn.setReadTimeout(6000);
                        conn.setDoInput(true);
                        conn.setInstanceFollowRedirects(true);
                        conn.connect();
                        int code = conn.getResponseCode();
                        if (code == 200) {
                            is = conn.getInputStream();
                            downloaded = BitmapFactory.decodeStream(is);
                        }
                    } else {
                        Uri contentUri = Uri.parse(finalUri);
                        is = context.getContentResolver().openInputStream(contentUri);
                        downloaded = BitmapFactory.decodeStream(is);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to load album art from " + finalUri + ": " + t.getMessage());
                } finally {
                    if (is != null) {
                        try { is.close(); } catch (Exception ignored) {}
                    }
                }

                final Bitmap resultBmp = downloaded;
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (finalUri.equals(activeLoadingUri)) {
                            if (resultBmp != null) {
                                synchronized (CACHE) {
                                    CACHE.put(finalUri, resultBmp);
                                }
                                imageView.setImageBitmap(resultBmp);
                            } else {
                                setAppIconFallback(context, packageName, imageView);
                            }
                        }
                    }
                });
            }
        });
    }

    private static void setAppIconFallback(Context context, String packageName, ImageView target) {
        int sizePx = target.getWidth() > 0 ? target.getWidth() : (int) (70 * context.getResources().getDisplayMetrics().density);
        if (packageName != null && !packageName.isEmpty()) {
            String p = packageName.toLowerCase(java.util.Locale.ROOT);
            if (p.contains("spotify") || p.contains("tw.music")) {
                target.setImageBitmap(BrandIconView.getIconBitmap(context, BrandIconView.TYPE_SPOTIFY, sizePx));
                return;
            }
            if (p.contains("radio")) {
                target.setImageBitmap(BrandIconView.getIconBitmap(context, BrandIconView.TYPE_RADIO, sizePx));
                return;
            }
            if (p.contains("youtube")) {
                target.setImageBitmap(BrandIconView.getIconBitmap(context, BrandIconView.TYPE_YOUTUBE, sizePx));
                return;
            }
            try {
                target.setImageDrawable(context.getPackageManager().getApplicationIcon(packageName));
                return;
            } catch (Exception ignored) {}
        }
        target.setImageBitmap(BrandIconView.getIconBitmap(context, BrandIconView.TYPE_SPOTIFY, sizePx));
    }
}
