package com.vios.apex;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Live GPS Map View:
 * Real-time tile map using OpenStreetMap dark matter tiles centered on GPS position.
 * Caches downloaded tiles to disk for fast offline operation.
 */
public class LiveMapView extends View {

    static final String ESRI = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/";
    static final String[] URLS = {
            "https://tile.openstreetmap.de/{z}/{x}/{y}.png",
            "https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png",
            "https://tile-a.openstreetmap.fr/hot/{z}/{x}/{y}.png",
            "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
            ESRI + "World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}",
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    };
    static final String[] LABELS = {null, null, null, null, ESRI + "World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}", null};
    static final boolean[] DARKEN = {true, true, true, true, false, true};
    static final int[] MAXZ = {18, 18, 18, 17, 16, 18};

    static int provider = 0;
    static int okCount;

    private final float s;
    private final float radius;
    private final File dir;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(64);
    private final HashSet<String> pending = new HashSet<String>();
    private final HashSet<String> failed = new HashSet<String>();
    private final LinkedList<String> queue = new LinkedList<String>();
    private int workers;

    private double lat = 3.17, lon = 101.54; // Default to Shah Alam
    private float bearing = Float.NaN;
    private int zoom = 16;
    private boolean hasGps = false;

    private final Paint tile = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint grid = new Paint();
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path tri = new Path();

    public LiveMapView(Context c, float scale, float radiusPx) {
        super(c);
        s = scale;
        radius = radiusPx;
        dir = new File(c.getCacheDir(), "map_tiles");
        dir.mkdirs();

        grid.setColor(0xFF181210);
        grid.setStrokeWidth(Math.max(1, s));

        text.setColor(0x88A09088);
        text.setTextSize(10 * s);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    static Bitmap darken(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.ColorMatrix m = new android.graphics.ColorMatrix(new float[]{
                -1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0});
        m.postConcat(new android.graphics.ColorMatrix(new float[]{
                -0.574f, 1.43f, 0.144f, 0, 0,
                0.426f, 0.43f, 0.144f, 0, 0,
                0.426f, 1.43f, -0.856f, 0, 0,
                0, 0, 0, 1, 0}));
        float lift = 30, k = 0.82f;
        m.postConcat(new android.graphics.ColorMatrix(new float[]{
                k, 0, 0, 0, lift, 0, k, 0, 0, lift, 0, 0, k * 1.05f, 0, lift + 4, 0, 0, 0, 1, 0}));
        Paint pt = new Paint(Paint.FILTER_BITMAP_FLAG);
        pt.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
        new Canvas(out).drawBitmap(src, 0, 0, pt);
        src.recycle();
        return out;
    }

    public void setPosition(double la, double lo, float br, float speedKmh) {
        lat = la;
        lon = lo;
        hasGps = true;
        if (!Float.isNaN(br)) bearing = br;
        zoom = speedKmh > 70 ? 15 : speedKmh > 35 ? 16 : 17;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        clip.reset();
        clip.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        c.drawColor(0xFF080605);

        if (zoom > MAXZ[provider]) zoom = MAXZ[provider];
        int n = 1 << zoom;
        double fx = (lon + 180.0) / 360.0 * n;
        double latR = Math.toRadians(lat);
        double fy = (1.0 - Math.log(Math.tan(latR) + 1.0 / Math.cos(latR)) / Math.PI) / 2.0 * n;
        float ts = 256 * s;
        float cx = w / 2f, cy = h * 0.45f;
        int tx0 = (int) Math.floor(fx - cx / ts) - 1, tx1 = (int) Math.floor(fx + (w - cx) / ts) + 1;
        int ty0 = (int) Math.floor(fy - cy / ts) - 1, ty1 = (int) Math.floor(fy + (h - cy) / ts) + 1;

        Rect dst = new Rect();
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int ty = ty0; ty <= ty1; ty++) {
                if (ty < 0 || ty >= n) continue;
                int wx = ((tx % n) + n) % n;
                float left = cx + (float) ((tx - fx) * ts), top = cy + (float) ((ty - fy) * ts);
                dst.set(Math.round(left), Math.round(top), Math.round(left + ts) + 1, Math.round(top + ts) + 1);
                Bitmap b = get(zoom, wx, ty, 0);
                if (b != null) {
                    c.drawBitmap(b, null, dst, tile);
                    if (LABELS[provider] != null) {
                        Bitmap l = get(zoom, wx, ty, 1);
                        if (l != null) c.drawBitmap(l, null, dst, tile);
                    }
                } else {
                    c.drawLine(dst.left, dst.top, dst.right, dst.top, grid);
                    c.drawLine(dst.left, dst.top, dst.left, dst.bottom, grid);
                }
            }
        }

        drawMarker(c, cx, cy);
        String attr = "© OpenStreetMap contributors";
        c.drawText(attr, w - text.measureText(attr) - 12 * s, 16 * s, text);

        c.restore();
    }

    private void drawMarker(Canvas c, float x, float y) {
        halo.setColor(0x40FF5500);
        c.drawCircle(x, y, 22 * s, halo);
        c.save();
        c.rotate(Float.isNaN(bearing) ? 0 : bearing, x, y);
        tri.reset();
        tri.moveTo(x, y - 14 * s);
        tri.lineTo(x + 10 * s, y + 11 * s);
        tri.lineTo(x, y + 5 * s);
        tri.lineTo(x - 10 * s, y + 11 * s);
        tri.close();

        arrow.setStyle(Paint.Style.FILL);
        arrow.setColor(0xFFFF5500); // Ember theme marker
        c.drawPath(tri, arrow);

        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeWidth(2 * s);
        arrow.setColor(0xFFFFFFFF);
        c.drawPath(tri, arrow);
        c.restore();
    }

    private Bitmap get(int z, int x, int y, int layer) {
        String k = provider + "_" + layer + "_" + z + "_" + x + "_" + y;
        Bitmap b = mem.get(k);
        if (b != null) return b;
        synchronized (queue) {
            if (!pending.contains(k) && !failed.contains(k)) {
                pending.add(k);
                queue.addFirst(k);
                if (workers < 2) { workers++; new Thread(worker).start(); }
            }
        }
        return null;
    }

    private final Runnable worker = new Runnable() {
        public void run() {
            while (true) {
                String k;
                synchronized (queue) {
                    if (queue.isEmpty()) { workers--; return; }
                    k = queue.removeFirst();
                }
                Bitmap b = null;
                try {
                    File f = new File(dir, k + ".png");
                    String[] p = k.split("_");
                    int pv = Integer.parseInt(p[0]), layer = Integer.parseInt(p[1]);
                    if (f.exists()) b = BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (b == null) {
                        int z = Integer.parseInt(p[2]), x = Integer.parseInt(p[3]), y = Integer.parseInt(p[4]);
                        String tpl = layer == 1 ? LABELS[pv] : URLS[pv];
                        String url = tpl.replace("{z}", String.valueOf(z)).replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
                        byte[] data = download(url, 8000);
                        okCount++;
                        b = BitmapFactory.decodeByteArray(data, 0, data.length);
                        if (b != null) {
                            FileOutputStream os = new FileOutputStream(f);
                            os.write(data);
                            os.close();
                        }
                    }
                    if (b != null && DARKEN[pv] && layer == 0) b = darken(b);
                } catch (Throwable ignored) { }

                final String fk = k;
                final Bitmap fb = b;
                ui.post(new Runnable() {
                    public void run() {
                        synchronized (queue) { pending.remove(fk); }
                        if (fb != null) { mem.put(fk, fb); invalidate(); }
                        else retryLater(fk);
                    }
                });
            }
        }
    };

    private static byte[] download(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", "FusionDeck/1.0 (Android Car Launcher)");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    private void retryLater(final String k) {
        synchronized (queue) { failed.add(k); }
        ui.postDelayed(new Runnable() {
            public void run() { synchronized (queue) { failed.remove(k); } invalidate(); }
        }, 15000);
    }
}
