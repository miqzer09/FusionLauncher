package com.vios.apex;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.lang.reflect.Method;

public class MainActivity extends Activity {

    // ---- palette (Ember & Black) ----
    static final int BG = 0xFF000000, CARD = 0xFF0D0A09, CARD_HI = 0xFF1A1412,
            ACC = 0xFFFF5500, ACC2 = 0xFFFF2A00, TXT = 0xFFFFF5EE, SUB = 0xFFA09088, WARN = 0xFFFF3333;

    // ---- tiles: title, preferred packages ----
    static final String[] TITLES = {"Spotify", "Radio", "Waze", "YouTube", "EQ", "Maps", "Chrome", "All Apps"};
    static final String[][] DEFAULTS = {
            {"com.spotify.music", "com.tw.music"},
            {"com.tw.radio"},
            {"com.waze", "com.tw.bt"},
            {"app.revanced.android.youtube", "com.google.android.youtube"},
            {"com.tw.eq", "com.tw.video"},
            {"com.google.android.apps.maps", "com.waze"},
            {"com.android.chrome"},
            {}
    };
    static final int ALL_APPS_SLOT = 7;

    SharedPreferences prefs;
    final Handler ui = new Handler(Looper.getMainLooper());
    PackageManager pm;

    TextView clockTv, ampmTv, dateTv, netTv, speedTv, unitTv, tempTv, condTv, carTv, plateTv, wxStatsTv, wxLocTv;
    SpeedometerGaugeView speedGauge;
    TextView tripDistTv, tripTimeTv, avgSpeedTv, maxSpeedTv, tripStatusTv;
    float tripDistanceKm = 0f;
    long tripMovingSec = 0;
    float tripMaxKmh = 0f;
    Location lastLoc = null;
    float lastAltitude = 0f;
    TextView titleTv, artistTv;
    ImageView artIv;
    ControlView playBtn;
    LiveMapView mapView;
    AnimatedWeatherCard weatherCard;
    WeatherIconView weatherIcon;
    final View[] tiles = new View[8];

    // media
    MediaSessionManager msm;
    MediaController controller;
    final MediaController.Callback mcb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { refreshMedia(); }
        @Override public void onMetadataChanged(MediaMetadata m) { refreshMedia(); }
        @Override public void onSessionDestroyed() { controller = null; refreshMedia(); }
    };
    final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override public void onActiveSessionsChanged(List<MediaController> l) { pickController(l); }
            };

    // radio sync (com.tw.radio via txznet broadcast protocol + TWUtil hardware control)
    boolean isRadioActive = false;
    boolean isRadioPlaying = false;
    float radioFrequency = 0f;

    private Object twUtilInstance;
    private Method twWriteMethod;
    private boolean twUtilInited = false;

    String rdsText = null;

    private final Handler twHandler = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            if (msg == null) return;
            if (msg.what == 1029) { // 0x405: Live RDS Text from hardware FM tuner
                if (msg.obj != null) {
                    String s = String.valueOf(msg.obj).trim();
                    if (!s.isEmpty()) {
                        rdsText = s;
                        refreshMedia();
                    }
                }
            } else if (msg.what == 1026) { // 0x402: Hardware frequency report
                int rawFreq = msg.arg1;
                if (rawFreq > 0) {
                    float f = normalizeFrequency((float) rawFreq);
                    if (f > 0 && Math.abs(f - radioFrequency) > 0.05f) {
                        radioFrequency = f;
                        rdsText = null;
                        refreshMedia();
                    }
                }
            }
        }
    };

    private synchronized void initTwUtil() {
        if (twUtilInited) return;
        twUtilInited = true;
        try {
            Class<?> clazz = Class.forName("android.tw.john.TWUtil");
            twUtilInstance = clazz.getConstructor().newInstance();
            short[] subs = new short[]{ 0x010a, 0x0201, 0x0301, 0x0401, 0x0402, 0x0404, 0x0405, 0x0406, (short) 0x9e00 };
            Method openMethod = clazz.getMethod("open", short[].class);
            openMethod.invoke(twUtilInstance, (Object) subs);
            twWriteMethod = clazz.getMethod("write", int.class, int.class, int.class);
            Method startMethod = clazz.getMethod("start");
            startMethod.invoke(twUtilInstance);

            // Register handler for hardware RDS decoding & tuner events
            Method addHandlerMethod = clazz.getMethod("addHandler", String.class, Handler.class);
            addHandlerMethod.invoke(twUtilInstance, "ApexRadio", twHandler);
        } catch (Throwable ignored) { }
    }

    private void sendTwKey(int cmd, int sub, int val) {
        initTwUtil();
        if (twUtilInstance != null && twWriteMethod != null) {
            try {
                twWriteMethod.invoke(twUtilInstance, cmd, sub, val);
            } catch (Throwable ignored) { }
        }
    }

    final BroadcastReceiver radioReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String act = intent.getAction();
            if ("com.txznet.launcher.radio.action.recv".equals(act)) {
                int action = intent.getIntExtra("action", -1);
                if (action == 1) {
                    float freq = intent.getFloatExtra("frequency", 0f);
                    if (freq <= 0) freq = intent.getIntExtra("frequency", 0);
                    freq = normalizeFrequency(freq);
                    if (freq > 0) {
                        radioFrequency = freq;
                        isRadioActive = true;
                        isRadioPlaying = true;
                        prefs.edit().putFloat("radio_freq", freq).putBoolean("radio_active", true).apply();
                        refreshMedia();
                    }
                } else if (action == 2) {
                    isRadioActive = true;
                    isRadioPlaying = true;
                    refreshMedia();
                } else if (action == 3) {
                    isRadioPlaying = false;
                    refreshMedia();
                }
            } else if ("android.intent.action.ACTION_RADIO_OFF".equals(act)) {
                isRadioActive = false;
                isRadioPlaying = false;
                refreshMedia();
            } else if ("android.intent.action.RADIO_STATE_CHANGED".equals(act)) {
                queryRadio();
            }
        }
    };

    void queryRadio() {
        try {
            Intent q = new Intent("com.txznet.launcher.radio.action.send");
            q.putExtra("action", 10);
            sendBroadcast(q);
        } catch (Exception ignored) { }
    }

    static float normalizeFrequency(float f) {
        if (f <= 0) return 0f;
        if (f >= 8700f && f <= 109000f) {
            return f / 100f; // e.g. 10530 -> 105.3
        }
        if (f >= 870f && f <= 1085f) {
            return f / 10f; // e.g. 1053 -> 105.3
        }
        return f;
    }

    static String formatFrequency(float f) {
        if (f <= 0) return "";
        float norm = normalizeFrequency(f);
        if (norm >= 87.0f && norm <= 108.5f) {
            return String.format(Locale.US, "%.1f MHz", norm);
        }
        if (f >= 520 && f <= 1700) {
            return String.format(Locale.US, "%.0f kHz", f);
        }
        return String.format(Locale.US, "%.1f MHz", norm);
    }

    static String getKnownStation(float freq) {
        float f = normalizeFrequency(freq);
        int key = Math.round(f * 10f);
        switch (key) {
            case 877: return "Kool 101";
            case 881: return "8FM";
            case 889: return "GoXuan";
            case 899: return "BFM 89.9";
            case 911: return "Radio Klasik";
            case 915: return "IKIM FM";
            case 923: return "Minnal FM";
            case 929: return "Hitz FM";
            case 936: return "UFM";
            case 939: return "Bernama Radio";
            case 945: return "Mix FM";
            case 958: return "Fly FM";
            case 963: return "Minnal FM";
            case 972: return "KL FM";
            case 976: return "Hot FM";
            case 983: return "Radio Klasik";
            case 988: return "988 FM";
            case 1001: return "TraXX FM";
            case 1009: return "Selangor FM";
            case 1018: return "MY FM";
            case 1025: return "Asyik FM";
            case 1033: return "Era FM";
            case 1049: return "Melody FM";
            case 1053: return "Suria FM";
            case 1057: return "Lite FM";
            case 1067: return "Radio Klasik";
            case 1075: return "Pahang FM";
            default: return null;
        }
    }

    LocationManager lm;
    final LocationListener locListener = new LocationListener() {
        @Override public void onLocationChanged(Location l) { onLoc(l); }
        @Override public void onStatusChanged(String p, int s, Bundle b) { }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
    };
    float lastKmh = -1;
    boolean gps = false;

    Dialog drawer;

    // ------------------------------------------------------------------ lifecycle
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        prefs = getSharedPreferences("apex", MODE_PRIVATE);
        radioFrequency = prefs.getFloat("radio_freq", 105.3f);
        isRadioActive = prefs.getBoolean("radio_active", true);
        isRadioPlaying = isRadioActive;
        RadioReceiver.callback = new RadioReceiver.Callback() {
            @Override public void onRadioUpdate(final float freq, final boolean isPlaying) {
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (freq > 0 && Math.abs(freq - radioFrequency) > 0.05f) {
                            radioFrequency = freq;
                            rdsText = null;
                        }
                        isRadioActive = true;
                        isRadioPlaying = isPlaying;
                        refreshMedia();
                    }
                });
            }
            @Override public void onRdsUpdate(final String rds) {
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (rds != null && !rds.trim().isEmpty()) {
                            rdsText = rds.trim();
                            refreshMedia();
                        }
                    }
                });
            }
        };
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildUi());
        hideSystemUi();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        tickClock();
        prefs.edit().remove("w_time").apply();
        fetchWeather();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        radioFrequency = prefs.getFloat("radio_freq", radioFrequency > 0 ? radioFrequency : 105.3f);
        isRadioActive = prefs.getBoolean("radio_active", isRadioActive);
        RadioReceiver.callback = new RadioReceiver.Callback() {
            @Override public void onRadioUpdate(final float freq, final boolean isPlaying) {
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (freq > 0 && Math.abs(freq - radioFrequency) > 0.05f) {
                            radioFrequency = freq;
                            rdsText = null;
                        }
                        isRadioActive = true;
                        isRadioPlaying = isPlaying;
                        refreshMedia();
                    }
                });
            }
            @Override public void onRdsUpdate(final String rds) {
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (rds != null && !rds.trim().isEmpty()) {
                            rdsText = rds.trim();
                            refreshMedia();
                        }
                    }
                });
            }
        };
        registerReceiver(tickRx, new IntentFilter(Intent.ACTION_TIME_TICK));
        IntentFilter rf = new IntentFilter();
        rf.addAction("com.txznet.launcher.radio.action.recv");
        rf.addAction("android.intent.action.ACTION_RADIO_OFF");
        rf.addAction("android.intent.action.RADIO_STATE_CHANGED");
        registerReceiver(radioReceiver, rf);
        queryRadio();
        startLocation();
        initMedia();
        refreshTiles();
        tickClock();
        if (weatherCard != null) weatherCard.startAnimation();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (twUtilInstance != null) {
            try {
                Method closeMethod = twUtilInstance.getClass().getMethod("close");
                closeMethod.invoke(twUtilInstance);
            } catch (Exception ignored) {}
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (weatherCard != null) weatherCard.stopAnimation();
        try { unregisterReceiver(tickRx); } catch (Exception ignored) { }
        try { unregisterReceiver(radioReceiver); } catch (Exception ignored) { }
        try { if (lm != null) lm.removeUpdates(locListener); } catch (Exception ignored) { }
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sessionsListener); } catch (Exception ignored) { }
        if (controller != null) { try { controller.unregisterCallback(mcb); } catch (Exception ignored) { } }
    }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) { startLocation(); }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (drawer != null && drawer.isShowing()) drawer.dismiss();
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) hideSystemUi(); }

    @Override public void onBackPressed() { /* launcher: stay home */ }

    void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    final BroadcastReceiver tickRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { tickClock(); }
    };

    // ------------------------------------------------------------------ UI
    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    static GradientDrawable rr(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    StateListDrawable press(int normal, int pressed, float r) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable p = rr(pressed, r);
        p.setStroke(dp(2), ACC);
        s.addState(new int[]{android.R.attr.state_pressed}, p);
        s.addState(new int[]{}, rr(normal, r));
        return s;
    }

    TextView tv(String t, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setSingleLine(true);
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(8), dp(14), dp(12));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // ---- top bar ----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(top, new LinearLayout.LayoutParams(-1, dp(58)));

        clockTv = tv("--:--", 38, TXT, true);
        top.addView(clockTv);
        ampmTv = tv("", 15, ACC, true);
        ampmTv.setPadding(dp(6), dp(12), dp(14), 0);
        top.addView(ampmTv);
        dateTv = tv("", 16, SUB, false);
        top.addView(dateTv);

        View.OnClickListener openTime = new View.OnClickListener() {
            @Override public void onClick(View v) {
                launchIntent(new Intent(Settings.ACTION_DATE_SETTINGS));
            }
        };
        clockTv.setOnClickListener(openTime);
        ampmTv.setOnClickListener(openTime);
        dateTv.setOnClickListener(openTime);

        View sp = new View(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));

        final View cleanBtn = new CleanIconView(this);
        cleanBtn.setContentDescription("Clean Up Memory");
        cleanBtn.setBackground(press(CARD, CARD_HI, dp(22)));
        cleanBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cleanUpMemory(cleanBtn); }
        });
        cleanBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { openTaskManager(); return true; }
        });
        top.addView(cleanBtn, mp(dp(44), dp(44), 0, 0, 0, dp(10), 0));

        TextView gear = tv("⚙", 22, TXT, true);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Settings");
        gear.setBackground(press(CARD, CARD_HI, dp(22)));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launchIntent(new Intent(Settings.ACTION_SETTINGS)); }
        });
        top.addView(gear, new LinearLayout.LayoutParams(dp(44), dp(44)));

        // ---- content ----
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        body.addView(leftCol, new LinearLayout.LayoutParams(0, -1, 29f));

        // Animated Weather Card placed ON TOP of vehicle tab (height = 1 unit)
        weatherCard = new AnimatedWeatherCard(this, getResources().getDisplayMetrics().density, dp(22));
        weatherCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().remove("w_time").apply();
                fetchWeather();
                toast("Refreshing live weather…");
            }
        });
        leftCol.addView(weatherCard, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Vehicle Tab placed below the weather card (height = 3 units)
        leftCol.addView(buildVehicleCard(), mp(-1, 0, 3f, 0, dp(10), 0, 0));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        body.addView(mid, mp(0, -1, 41f, dp(10), 0, 0, 0));
        mid.addView(buildNavCard(), new LinearLayout.LayoutParams(-1, 0, 3f));
        mid.addView(buildMediaCard(), mp(-1, 0, 1f, 0, dp(10), 0, 0));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        body.addView(right, mp(0, -1, 30f, dp(10), 0, 0, 0));
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            right.addView(row, mp(-1, 0, 1f, 0, r == 0 ? 0 : dp(10), 0, 0));
            for (int c = 0; c < 2; c++) {
                int slot = r * 2 + c;
                View t = buildTile(slot);
                tiles[slot] = t;
                row.addView(t, mp(0, -1, 1f, c == 0 ? 0 : dp(10), 0, 0, 0));
            }
        }
        return root;
    }

    View buildNavCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(rr(CARD, dp(22)));
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(22)); }
        });

        mapView = new LiveMapView(this, getResources().getDisplayMetrics().density, dp(22));
        double initLat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(3.17)));
        double initLon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(101.54)));
        mapView.setPosition(initLat, initLon, 0, 0);
        mapView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (installed("com.google.android.apps.maps")) {
                    launchPackage("com.google.android.apps.maps");
                } else if (installed("com.waze")) {
                    launchPackage("com.waze");
                } else {
                    launchIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:3.17,101.54")));
                }
            }
        });
        card.addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        TextView lab = tv("LIVE MAP", 11, ACC, true);
        lab.setPadding(dp(12), dp(6), dp(12), dp(6));
        lab.setBackground(rr(0xD00D0A09, dp(14)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        lp.setMargins(dp(12), dp(12), 0, 0);
        card.addView(lab, lp);
        return card;
    }

    LinearLayout.LayoutParams mp(int w, int h, float wt, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, wt);
        p.setMargins(l, t, r, b);
        return p;
    }

    View buildVehicleCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        card.setBackground(rr(CARD, dp(22)));

        // 1. Top Header: Car model, plate, ambient temperature
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));

        carTv = tv(prefs.getString("car", "Toyota Vios NCP150"), 13, TXT, true);
        carTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(carTv, new LinearLayout.LayoutParams(0, -2, 1f));

        plateTv = tv(prefs.getString("plate", "JQE9125"), 11, Color.BLACK, true);
        plateTv.setPadding(dp(8), dp(2), dp(8), dp(2));
        plateTv.setBackground(rr(ACC, dp(6)));
        top.addView(plateTv);

        View.OnClickListener editClick = new View.OnClickListener() {
            @Override public void onClick(View v) { editVehicle(); }
        };
        View.OnLongClickListener editLong = new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { editVehicle(); return true; }
        };
        carTv.setOnClickListener(editClick);
        carTv.setOnLongClickListener(editLong);
        plateTv.setOnClickListener(editClick);
        plateTv.setOnLongClickListener(editLong);

        // 2. Speedometer Arc Gauge (Option A)
        speedGauge = new SpeedometerGaugeView(this);
        speedGauge.setClickable(true);
        speedGauge.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putBoolean("mph", !prefs.getBoolean("mph", false)).apply();
                showSpeed();
                updateTripStats();
            }
        });
        speedGauge.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                launchIntent(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                return true;
            }
        });
        card.addView(speedGauge, mp(-1, 0, 1.25f, 0, dp(4), 0, dp(6)));

        // 3. Live Trip Computer & Telemetry Dock (Option B)
        card.addView(buildTripDock(), new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    View buildTripDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setBackground(rr(CARD_HI, dp(14)));
        dock.setPadding(dp(10), dp(6), dp(10), dp(6));
        dock.setClickable(true);
        dock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tripDistanceKm = 0f;
                tripMovingSec = 0;
                tripMaxKmh = 0f;
                updateTripStats();
                toast("Trip stats reset to zero");
            }
        });

        // Row 1: TRIP DISTANCE and DRIVE TIME
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        dock.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout colDist = buildStatCol("TRIP", tripDistTv = tv("0.0 km", 13, TXT, true));
        row1.addView(colDist, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout colTime = buildStatCol("DRIVE TIME", tripTimeTv = tv("0m", 13, TXT, true));
        row1.addView(colTime, new LinearLayout.LayoutParams(0, -2, 1f));

        // Row 2: AVG SPEED and TOP SPEED
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        dock.addView(row2, mp(-1, -2, 0, 0, dp(4), 0, 0));

        LinearLayout colAvg = buildStatCol("AVG SPD", avgSpeedTv = tv("0 km/h", 12, ACC, true));
        row2.addView(colAvg, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout colMax = buildStatCol("TOP SPD", maxSpeedTv = tv("0 km/h", 12, ACC, true));
        row2.addView(colMax, new LinearLayout.LayoutParams(0, -2, 1f));

        // Row 3: Status footer
        tripStatusTv = tv("● GPS STANDBY", 9, SUB, false);
        tripStatusTv.setGravity(Gravity.CENTER);
        dock.addView(tripStatusTv, mp(-1, -2, 0, 0, dp(4), 0, 0));

        updateTripStats();
        return dock;
    }

    LinearLayout buildStatCol(String label, TextView valTv) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        TextView lab = tv(label, 8, SUB, true);
        lab.setGravity(Gravity.CENTER);
        col.addView(lab);
        valTv.setGravity(Gravity.CENTER);
        col.addView(valTv);
        return col;
    }

    void updateTripStats() {
        if (tripDistTv == null) return;
        boolean mph = prefs.getBoolean("mph", false);
        float dist = mph ? tripDistanceKm * 0.621371f : tripDistanceKm;
        String distUnit = mph ? " mi" : " km";
        tripDistTv.setText(String.format(java.util.Locale.US, "%.1f%s", dist, distUnit));

        long mins = tripMovingSec / 60;
        long hrs = mins / 60;
        if (hrs > 0) {
            tripTimeTv.setText(String.format(java.util.Locale.US, "%dh %02dm", hrs, mins % 60));
        } else {
            tripTimeTv.setText(String.format(java.util.Locale.US, "%dm", mins));
        }

        float avgKmh = (tripMovingSec > 5) ? (tripDistanceKm / (tripMovingSec / 3600f)) : 0f;
        float avgVal = mph ? avgKmh * 0.621371f : avgKmh;
        float maxVal = mph ? tripMaxKmh * 0.621371f : tripMaxKmh;
        String spdUnit = mph ? " mph" : " km/h";

        avgSpeedTv.setText(String.format(java.util.Locale.US, "%.0f%s", avgVal, spdUnit));
        maxSpeedTv.setText(String.format(java.util.Locale.US, "%.0f%s", maxVal, spdUnit));

        String altStr = (lastAltitude > 0) ? (" • ALT " + Math.round(lastAltitude) + "m") : "";
        tripStatusTv.setText(gps ? ("🟢 GPS 3D" + altStr + " • TAP TO RESET") : "● GPS STANDBY");
    }

    View buildMediaCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(16), dp(10));
        card.setBackground(rr(CARD, dp(22)));

        artIv = new ImageView(this);
        artIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artIv.setBackground(rr(CARD_HI, dp(14)));
        artIv.setClipToOutline(true);
        artIv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14)); }
        });
        artIv.setImageBitmap(BrandIconView.getIconBitmap(this, BrandIconView.TYPE_SPOTIFY, dp(70)));
        card.addView(artIv, new LinearLayout.LayoutParams(dp(70), dp(70)));

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(r, mp(0, -2, 1f, dp(12), 0, dp(8), 0));

        TextView lab = tv("NOW PLAYING", 10, ACC, true);
        r.addView(lab);
        titleTv = tv("Nothing playing", 15, TXT, true);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        titleTv.setMarqueeRepeatLimit(-1);
        titleTv.setSelected(true);
        r.addView(titleTv, mp(-1, -2, 0, 0, dp(3), 0, 0));

        artistTv = tv("Start music, radio or Bluetooth audio", 12, SUB, false);
        artistTv.setSingleLine(true);
        artistTv.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        artistTv.setMarqueeRepeatLimit(-1);
        artistTv.setSelected(true);
        r.addView(artistTv, mp(-1, -2, 0, 0, dp(2), 0, 0));

        View.OnClickListener openMedia = new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean sessionPlaying = controller != null && controller.getPlaybackState() != null
                        && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
                boolean hasMusicSession = controller != null && controller.getMetadata() != null
                        && controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE) != null;
                if (!sessionPlaying && !hasMusicSession && isRadioActive) {
                    launchPackage("com.tw.radio");
                } else if (controller != null) {
                    launchPackage(controller.getPackageName());
                } else if (isRadioActive) {
                    launchPackage("com.tw.radio");
                } else {
                    launchPackage("com.spotify.music");
                }
            }
        };
        artIv.setOnClickListener(openMedia);
        r.setOnClickListener(openMedia);

        LinearLayout ctl = new LinearLayout(this);
        ctl.setOrientation(LinearLayout.HORIZONTAL);
        ctl.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(ctl, new LinearLayout.LayoutParams(-2, -2));

        ControlView prev = new ControlView(this, ControlView.PREV, false);
        playBtn = new ControlView(this, ControlView.PLAY, true);
        ControlView next = new ControlView(this, ControlView.NEXT, false);
        ctl.addView(prev, new LinearLayout.LayoutParams(dp(40), dp(40)));
        ctl.addView(playBtn, mp(dp(48), dp(48), 0, dp(6), 0, dp(6), 0));
        ctl.addView(next, new LinearLayout.LayoutParams(dp(40), dp(40)));
        prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { transport(KeyEvent.KEYCODE_MEDIA_PREVIOUS); } });
        playBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { transport(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); } });
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { transport(KeyEvent.KEYCODE_MEDIA_NEXT); } });
        return card;
    }

    View buildTile(final int slot) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(press(CARD, CARD_HI, dp(22)));
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSlot(slot); }
        });
        if (slot != ALL_APPS_SLOT) {
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { pickAppFor(slot); return true; }
            });
        }
        return t;
    }

    int getBrandTypeForPackage(String pkg) {
        if (pkg == null) return -1;
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.contains("spotify") || p.contains("tw.music") || p.equals("com.android.music")) return BrandIconView.TYPE_SPOTIFY;
        if (p.contains("radio") || p.contains("tw.radio") || p.contains("txznet.launcher.radio")) return BrandIconView.TYPE_RADIO;
        if (p.contains("waze")) return BrandIconView.TYPE_WAZE;
        if (p.contains("youtube") || p.contains("revanced")) return BrandIconView.TYPE_YOUTUBE;
        if (p.contains("eq") || p.contains("equalizer")) return BrandIconView.TYPE_EQ;
        if (p.contains("maps") || p.contains("google.android.apps.maps")) return BrandIconView.TYPE_MAPS;
        if (p.contains("chrome") || p.contains("browser")) return BrandIconView.TYPE_CHROME;
        if (p.contains("bt") || p.contains("bluetooth") || p.contains("phone")) return BrandIconView.TYPE_BT;
        if (p.contains("video")) return BrandIconView.TYPE_VIDEO;
        if (p.contains("settings") || p.contains("carchoose")) return BrandIconView.TYPE_SETTINGS;
        if (p.contains("file") || p.contains("explore")) return BrandIconView.TYPE_FILES;
        return -1;
    }

    int getBrandTypeForSlot(int slot, String pkg) {
        if (slot == ALL_APPS_SLOT) return BrandIconView.TYPE_ALL_APPS;
        if (pkg != null) {
            int b = getBrandTypeForPackage(pkg);
            if (b >= 0) return b;
            String saved = prefs.getString("slot_" + slot, null);
            if (saved != null && !saved.equals(pkg)) return -1;
            for (String def : DEFAULTS[slot]) {
                if (def.equals(pkg)) return slot;
            }
            return -1;
        }
        return (slot >= 0 && slot < 7) ? slot : -1;
    }

    void refreshTiles() {
        for (int slot = 0; slot < tiles.length; slot++) {
            LinearLayout t = (LinearLayout) tiles[slot];
            t.removeAllViews();
            View icon;
            String label = TITLES[slot];
            String pkg = slotPackage(slot);
            int brandType = getBrandTypeForSlot(slot, pkg);

            if (brandType >= 0) {
                icon = new BrandIconView(this, brandType);
                if (pkg != null) {
                    try {
                        String appLabel = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
                        if ("app.revanced.android.youtube".equals(pkg) || appLabel.toLowerCase().contains("revanced")) {
                            label = "YouTube";
                        } else if (!appLabel.isEmpty()) {
                            label = appLabel;
                        }
                    } catch (Exception ignored) {}
                }
            } else {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (pkg != null) {
                    try {
                        iv.setImageDrawable(pm.getApplicationIcon(pkg));
                        label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
                    } catch (Exception e) { pkg = null; }
                }
                if (pkg == null) {
                    iv.setImageDrawable(new ColorDrawable(0));
                    label = TITLES[slot] + " (hold to set)";
                }
                icon = iv;
            }
            t.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView lb = tv(label, 15, TXT, true);
            lb.setGravity(Gravity.CENTER);
            lb.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.addView(lb, mp(-2, -2, 0, dp(6), dp(8), dp(6), 0));
        }
    }

    // ------------------------------------------------------------------ tile logic
    boolean installed(String pkg) { return pm.getLaunchIntentForPackage(pkg) != null; }

    String slotPackage(int slot) {
        String saved = prefs.getString("slot_" + slot, null);
        if (saved != null && installed(saved)) return saved;
        for (String p : DEFAULTS[slot]) if (installed(p)) return p;
        return null;
    }

    void openSlot(int slot) {
        if (slot == ALL_APPS_SLOT) { showDrawer(null, -1); return; }
        String pkg = slotPackage(slot);
        if (pkg == null) { pickAppFor(slot); return; }
        launchPackage(pkg);
    }

    void pickAppFor(final int slot) { showDrawer("Choose app for " + TITLES[slot], slot); }

    void launchPackage(String pkg) {
        Intent i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) { toast("App not installed"); return; }
        launchIntent(i);
    }

    void launchIntent(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) { toast("Cannot open"); }
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ------------------------------------------------------------------ app drawer
    static class AppItem { String label, pkg; Drawable icon; }

    List<AppItem> loadApps() {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
        List<AppItem> out = new ArrayList<AppItem>();
        List<String> seen = new ArrayList<String>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            AppItem a = new AppItem();
            a.pkg = pkg;
            String lbl = String.valueOf(ri.loadLabel(pm));
            if ("app.revanced.android.youtube".equals(pkg) || lbl.toLowerCase().contains("revanced")) {
                lbl = "YouTube";
            }
            a.label = lbl;
            int brand = getBrandTypeForPackage(pkg);
            if (brand >= 0) {
                Bitmap bmp = BrandIconView.getIconBitmap(this, brand, dp(52));
                a.icon = (bmp != null) ? new BitmapDrawable(getResources(), bmp) : ri.loadIcon(pm);
            } else {
                a.icon = ri.loadIcon(pm);
            }
            out.add(a);
        }
        Collections.sort(out, new Comparator<AppItem>() {
            @Override public int compare(AppItem x, AppItem y) { return x.label.compareToIgnoreCase(y.label); }
        });
        return out;
    }

    void showDrawer(String title, final int pickSlot) {
        final List<AppItem> all = loadApps();
        final List<AppItem> shown = new ArrayList<AppItem>(all);
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        drawer = d;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(10), dp(18), dp(10));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));
        TextView ttl = tv(title == null ? "All Apps" : title, 22, TXT, true);
        bar.addView(ttl, mp(0, -2, 1f, dp(6), 0, 0, 0));
        TextView close = tv("✕ CLOSE", 13, TXT, true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(18), 0, dp(18), 0);
        close.setBackground(press(CARD, CARD_HI, dp(22)));
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        bar.addView(close, new LinearLayout.LayoutParams(-2, dp(44)));

        GridView grid = new GridView(this);
        grid.setNumColumns(6);
        grid.setVerticalSpacing(dp(8));
        grid.setHorizontalSpacing(dp(8));
        grid.setSelector(new ColorDrawable(0));
        root.addView(grid, mp(-1, 0, 1f, 0, dp(8), 0, 0));

        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) {
                LinearLayout c = (LinearLayout) cv;
                if (c == null) {
                    c = new LinearLayout(MainActivity.this);
                    c.setOrientation(LinearLayout.VERTICAL);
                    c.setGravity(Gravity.CENTER);
                    c.setPadding(dp(4), dp(8), dp(4), dp(8));
                    c.setBackground(press(0x00000000, CARD_HI, dp(16)));
                    ImageView iv = new ImageView(MainActivity.this);
                    c.addView(iv, new LinearLayout.LayoutParams(dp(52), dp(52)));
                    TextView t = tv("", 13, TXT, false);
                    t.setGravity(Gravity.CENTER);
                    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    c.addView(t, mp(-1, -2, 0, 0, dp(6), 0, 0));
                    c.setLayoutParams(new AbsListViewLP(dp(110)));
                }
                AppItem a = shown.get(i);
                ((ImageView) c.getChildAt(0)).setImageDrawable(a.icon);
                ((TextView) c.getChildAt(1)).setText(a.label);
                return c;
            }
        };
        grid.setAdapter(ad);
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int i, long id) {
                AppItem a = shown.get(i);
                d.dismiss();
                if (pickSlot >= 0) {
                    prefs.edit().putString("slot_" + pickSlot, a.pkg).apply();
                    refreshTiles();
                } else launchPackage(a.pkg);
            }
        });
        grid.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int i, long id) {
                if (pickSlot >= 0) return false;
                AppItem a = shown.get(i);
                showAppOptionsDialog(a, d);
                return true;
            }
        });
        d.setContentView(root);
        d.show();
    }

    void showAppOptionsDialog(final AppItem a, final Dialog drawerDialog) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(0xAA000000));
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            d.getWindow().setGravity(Gravity.CENTER);
        }

        LinearLayout root = new LinearLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rr(CARD, dp(18)));
        card.setPadding(dp(18), dp(14), dp(18), dp(16));
        card.setClickable(true);

        // Header: App icon (36x36dp) + App Label (14sp) + Package (10sp) + Close button (✕)
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(a.icon);
        head.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout txtCol = new LinearLayout(this);
        txtCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = tv(a.label, 14, TXT, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView sub = tv(a.pkg, 10, SUB, false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        txtCol.addView(title);
        txtCol.addView(sub, mp(0, -2, 0, 0, dp(1), 0, 0));
        head.addView(txtCol, mp(0, -2, 1f, dp(10), 0, dp(8), 0));

        TextView closeX = tv("✕", 13, SUB, true);
        closeX.setGravity(Gravity.CENTER);
        closeX.setPadding(dp(8), dp(4), dp(8), dp(4));
        closeX.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        head.addView(closeX);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        View sep = new View(this);
        sep.setBackgroundColor(0x20FFFFFF);
        card.addView(sep, mp(-1, dp(1), 0, 0, dp(10), 0, dp(10)));

        // Option 1: App Info (height 36dp)
        LinearLayout btnInfo = new LinearLayout(this);
        btnInfo.setOrientation(LinearLayout.HORIZONTAL);
        btnInfo.setGravity(Gravity.CENTER_VERTICAL);
        btnInfo.setBackground(press(CARD_HI, 0xFF2A221E, dp(10)));
        btnInfo.setPadding(dp(12), 0, dp(12), 0);
        btnInfo.setClickable(true);
        TextView iconInfo = tv("ℹ️", 14, TXT, false);
        TextView lblInfo = tv("  App Info", 13, TXT, true);
        btnInfo.addView(iconInfo);
        btnInfo.addView(lblInfo);
        btnInfo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                if (drawerDialog != null) drawerDialog.dismiss();
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.fromParts("package", a.pkg, null));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) { toast("Cannot open App Info"); }
            }
        });
        card.addView(btnInfo, new LinearLayout.LayoutParams(-1, dp(36)));

        // Option 2: Uninstall App (height 36dp)
        LinearLayout btnUninstall = new LinearLayout(this);
        btnUninstall.setOrientation(LinearLayout.HORIZONTAL);
        btnUninstall.setGravity(Gravity.CENTER_VERTICAL);
        btnUninstall.setBackground(press(CARD_HI, 0xFF2A221E, dp(10)));
        btnUninstall.setPadding(dp(12), 0, dp(12), 0);
        btnUninstall.setClickable(true);
        TextView iconUn = tv("🗑️", 14, WARN, false);
        TextView lblUn = tv("  Uninstall App", 13, WARN, true);
        btnUninstall.addView(iconUn);
        btnUninstall.addView(lblUn);
        btnUninstall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                boolean isSystem = false;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(a.pkg, 0);
                    isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                } catch (Exception ignored) {}

                if (isSystem) {
                    toast(a.label + " is a system app and cannot be uninstalled");
                    return;
                }
                if (drawerDialog != null) drawerDialog.dismiss();
                try {
                    Intent i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                    i.setData(Uri.parse("package:" + a.pkg));
                    i.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    try {
                        Intent i2 = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg));
                        i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i2);
                    } catch (Exception e2) { toast("Cannot uninstall app"); }
                }
            }
        });
        card.addView(btnUninstall, mp(-1, dp(36), 0, 0, dp(8), 0, 0));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dp(260), -2);
        cardLp.gravity = Gravity.CENTER;
        root.addView(card, cardLp);

        d.setContentView(root);
        d.show();
    }

    static class AbsListViewLP extends android.widget.AbsListView.LayoutParams {
        AbsListViewLP(int h) { super(-1, h); }
    }

    void cleanUpMemory(final View v) {
        if (v != null) {
            v.animate().rotationBy(360f).setDuration(500).start();
        }

        new Thread(new Runnable() {
            @Override public void run() {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo miBefore = new ActivityManager.MemoryInfo();
                if (am != null) am.getMemoryInfo(miBefore);
                long before = miBefore.availMem;

                // 1. Terminate background processes of non-system apps
                try {
                    List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                    for (ApplicationInfo app : apps) {
                        if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !app.packageName.equals(getPackageName())) {
                            if (am != null) am.killBackgroundProcesses(app.packageName);
                        }
                    }
                } catch (Exception ignored) {}

                // 2. Drop caches via root if available
                try {
                    Runtime.getRuntime().exec(new String[]{"su", "-c", "sync; echo 3 > /proc/sys/vm/drop_caches"}).waitFor();
                } catch (Exception ignored) {}

                // 3. Garbage collection
                System.gc();

                ActivityManager.MemoryInfo miAfter = new ActivityManager.MemoryInfo();
                if (am != null) am.getMemoryInfo(miAfter);
                long after = miAfter.availMem;
                final long freedMb = Math.max(0, (after - before) / (1024 * 1024));
                final long freeTotalMb = after / (1024 * 1024);

                ui.post(new Runnable() {
                    @Override public void run() {
                        if (freedMb > 5) {
                            toast("⚡ Freed " + freedMb + " MB RAM • " + freeTotalMb + " MB Free");
                        } else {
                            toast("⚡ System Optimized • " + freeTotalMb + " MB Free");
                        }
                    }
                });
            }
        }).start();
    }

    void openTaskManager() {
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "187"});
            return;
        } catch (Exception ignored) {}
        try {
            launchIntent(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        } catch (Exception ignored) {}
    }

    void editVehicle() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(10), dp(20), 0);
        final EditText car = new EditText(this);
        car.setText(prefs.getString("car", "Toyota Vios NCP150"));
        car.setHint("Vehicle name");
        final EditText plate = new EditText(this);
        plate.setText(prefs.getString("plate", "JQE9125"));
        plate.setHint("Plate / tag");
        l.addView(car);
        l.addView(plate);
        new AlertDialog.Builder(this).setTitle("Vehicle").setView(l)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        prefs.edit().putString("car", car.getText().toString().trim())
                                .putString("plate", plate.getText().toString().trim()).apply();
                        carTv.setText(prefs.getString("car", ""));
                        String p = prefs.getString("plate", "");
                        plateTv.setText(p);
                        plateTv.setVisibility(p.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ clock / network
    void tickClock() {
        Date n = new Date();
        clockTv.setText(new SimpleDateFormat("h:mm", Locale.ENGLISH).format(n));
        ampmTv.setText(new SimpleDateFormat("a", Locale.ENGLISH).format(n).toUpperCase(Locale.ENGLISH));
        dateTv.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(n));
        boolean on = false;
        try {
            NetworkInfo ni = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            on = ni != null && ni.isConnected();
        } catch (Exception ignored) { }
        // Network status text intentionally hidden; tap blank area opens Wi-Fi settings.
    }

    // ------------------------------------------------------------------ weather (Open-Meteo)
    void fetchWeather() {
        long last = prefs.getLong("w_time", 0);
        if (System.currentTimeMillis() - last < 30 * 60 * 1000L && prefs.contains("w_temp")) {
            showWeather(prefs.getInt("w_temp", 0), prefs.getInt("w_code", 0),
                    prefs.getInt("w_high", 30), prefs.getInt("w_low", 24),
                    prefs.getInt("w_hum", 80), prefs.getInt("w_feels", 28),
                    prefs.getInt("w_wind", 8), prefs.getInt("w_day", 1) != 0, null);
            return;
        }
        final double lat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(3.17)));
        final double lon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(101.54)));
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection c = null;
                try {
                    URL u = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto");
                    c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String ln;
                    while ((ln = r.readLine()) != null) sb.append(ln);
                    JSONObject rootObj = new JSONObject(sb.toString());
                    JSONObject cur = rootObj.getJSONObject("current");
                    final int t = (int) Math.round(cur.getDouble("temperature_2m"));
                    final int code = cur.getInt("weather_code");
                    final int hum = cur.optInt("relative_humidity_2m", 80);
                    final int feels = (int) Math.round(cur.optDouble("apparent_temperature", t));
                    final int wind = (int) Math.round(cur.optDouble("wind_speed_10m", 0));
                    final int day = cur.optInt("is_day", 1);

                    JSONObject daily = rootObj.optJSONObject("daily");
                    int hVal = t + 4, lVal = t - 3;
                    final AnimatedWeatherCard.DayForecast[] fList = new AnimatedWeatherCard.DayForecast[4];
                    if (daily != null) {
                        org.json.JSONArray times = daily.optJSONArray("time");
                        org.json.JSONArray codes = daily.optJSONArray("weather_code");
                        org.json.JSONArray maxs = daily.optJSONArray("temperature_2m_max");
                        org.json.JSONArray mins = daily.optJSONArray("temperature_2m_min");
                        if (maxs != null && maxs.length() > 0) hVal = (int) Math.round(maxs.optDouble(0, t + 4));
                        if (mins != null && mins.length() > 0) lVal = (int) Math.round(mins.optDouble(0, t - 3));

                        SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        SimpleDateFormat outFmt = new SimpleDateFormat("EEE", Locale.US);

                        for (int i = 0; i < 4; i++) {
                            AnimatedWeatherCard.DayForecast df = new AnimatedWeatherCard.DayForecast();
                            if (times != null && i < times.length()) {
                                try {
                                    Date dt = inFmt.parse(times.getString(i));
                                    df.dayName = outFmt.format(dt).toUpperCase(Locale.US);
                                } catch (Exception ignored) {
                                    df.dayName = "D" + (i + 1);
                                }
                            }
                            if (codes != null && i < codes.length()) df.code = codes.optInt(i, 0);
                            if (mins != null && i < mins.length()) df.minTemp = (int) Math.round(mins.optDouble(i, 22));
                            if (maxs != null && i < maxs.length()) df.maxTemp = (int) Math.round(maxs.optDouble(i, 30));
                            fList[i] = df;
                        }
                    }
                    final int high = hVal;
                    final int low = lVal;

                    prefs.edit().putInt("w_temp", t).putInt("w_code", code)
                            .putInt("w_high", high).putInt("w_low", low)
                            .putInt("w_hum", hum).putInt("w_feels", feels)
                            .putInt("w_wind", wind).putInt("w_day", day)
                            .putLong("w_time", System.currentTimeMillis()).apply();
                    ui.post(new Runnable() {
                        @Override public void run() {
                            showWeather(t, code, high, low, hum, feels, wind, day != 0, fList);
                        }
                    });
                } catch (Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (prefs.contains("w_temp")) {
                                showWeather(prefs.getInt("w_temp", 0), prefs.getInt("w_code", 0),
                                        prefs.getInt("w_high", 30), prefs.getInt("w_low", 24),
                                        prefs.getInt("w_hum", 80), prefs.getInt("w_feels", 28),
                                        prefs.getInt("w_wind", 8), prefs.getInt("w_day", 1) != 0, null);
                            }
                        }
                    });
                } finally { if (c != null) c.disconnect(); }
            }
        }).start();
        // refresh again in 30 minutes while the launcher is alive
        ui.postDelayed(new Runnable() { @Override public void run() { prefs.edit().remove("w_time").apply(); fetchWeather(); } }, 30 * 60 * 1000L);
    }

    void showWeather(int t, int code, int high, int low, int hum, int feels, int wind, boolean isDay, AnimatedWeatherCard.DayForecast[] forecasts) {
        String s;
        if (code == 0) s = isDay ? "Clear sky" : "Clear night";
        else if (code <= 3) s = "Mostly cloudy";
        else if (code == 45 || code == 48) s = "Fog";
        else if (code <= 57) s = "Drizzle";
        else if (code <= 67) s = "Rain";
        else if (code <= 77) s = "Snow";
        else if (code <= 82) s = "Rain showers";
        else if (code >= 95) s = "Thunderstorm";
        else s = "Cloudy";

        if (weatherCard != null) {
            weatherCard.updateWeather(t, code, high, low, s, forecasts, isDay);
        }
    }

    // ------------------------------------------------------------------ GPS speed
    void startLocation() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) { showSpeed(); return; }
            lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            try {
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locListener);
                }
            } catch (Exception ignored) { }
            try {
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, locListener);
                }
            } catch (Exception ignored) { }

            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (l != null) {
                saveLoc(l);
                if (mapView != null) mapView.setPosition(l.getLatitude(), l.getLongitude(), l.getBearing(), 0);
            }
        } catch (Exception ignored) { }
        showSpeed();
    }

    void saveLoc(Location l) {
        prefs.edit().putLong("lat", Double.doubleToLongBits(l.getLatitude()))
                .putLong("lon", Double.doubleToLongBits(l.getLongitude())).apply();
    }

    void onLoc(Location l) {
        gps = true;
        lastKmh = l.hasSpeed() ? l.getSpeed() * 3.6f : 0;
        if (!prefs.contains("lat_saved")) { saveLoc(l); prefs.edit().putBoolean("lat_saved", true).apply(); }
        if (mapView != null) {
            mapView.setPosition(l.getLatitude(), l.getLongitude(), l.getBearing(), lastKmh);
        }

        // Trip distance accumulation
        if (lastLoc != null && l.hasSpeed() && l.getSpeed() > 0.5f) {
            float dist = lastLoc.distanceTo(l);
            if (dist > 0 && dist < 500) {
                tripDistanceKm += (dist / 1000f);
            }
        }
        lastLoc = l;

        // Drive time
        if (lastKmh > 1.0f) {
            tripMovingSec++;
        }
        if (lastKmh > tripMaxKmh) tripMaxKmh = lastKmh;
        if (l.hasAltitude()) lastAltitude = (float) l.getAltitude();

        if (speedGauge != null) {
            if (l.hasBearing()) speedGauge.setBearing(l.getBearing());
            if (l.hasAltitude()) speedGauge.setAltitude((float) l.getAltitude());
        }

        updateTripStats();
        showSpeed();
    }

    void showSpeed() {
        boolean mph = prefs.getBoolean("mph", false);
        if (unitTv != null) unitTv.setText(mph ? "mph" : "km/h");
        if (speedGauge != null) {
            speedGauge.setSpeed(gps ? lastKmh : 0, mph, gps);
        }
        if (!gps || lastKmh < 0) {
            if (speedTv != null) speedTv.setText("--");
            return;
        }
        float v = mph ? lastKmh * 0.621371f : lastKmh;
        if (speedTv != null) speedTv.setText(String.valueOf(Math.round(v < 2 ? 0 : v)));
    }

    // ------------------------------------------------------------------ media
    void initMedia() {
        try {
            msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(this, MediaListener.class);
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn);
            pickController(msm.getActiveSessions(cn));
        } catch (SecurityException e) {
            artistTv.setText("Enable notification access for Apex to show track info");
            controller = null;
        } catch (Exception ignored) { }
    }

    void pickController(List<MediaController> l) {
        if (controller != null) { try { controller.unregisterCallback(mcb); } catch (Exception ignored) { } }
        controller = null;
        if (l != null && !l.isEmpty()) {
            MediaController best = l.get(0);
            for (MediaController c : l) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
            }
            controller = best;
            controller.registerCallback(mcb);
        }
        refreshMedia();
    }

    void refreshMedia() {
        boolean sessionPlaying = controller != null && controller.getPlaybackState() != null
                && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;

        boolean hasMusicSession = controller != null && controller.getMetadata() != null
                && controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE) != null;

        // If radio is actively playing and no music session is playing, show radio
        // OR if no music track exists and radio frequency is tuned, show radio
        if ((!sessionPlaying && isRadioPlaying && isRadioActive && radioFrequency > 0)
                || (!hasMusicSession && !sessionPlaying && isRadioActive && radioFrequency > 0)) {
            String freqStr = formatFrequency(radioFrequency);
            String station = getKnownStation(radioFrequency);
            if (station != null) {
                titleTv.setText(station);
                if (rdsText != null && !rdsText.isEmpty()) {
                    artistTv.setText(freqStr + " • RDS: " + rdsText);
                } else {
                    artistTv.setText(freqStr + (isRadioPlaying ? " • Live" : " • Paused"));
                }
            } else {
                titleTv.setText("FM " + freqStr);
                if (rdsText != null && !rdsText.isEmpty()) {
                    artistTv.setText("RDS: " + rdsText);
                } else {
                    artistTv.setText(isRadioPlaying ? "Radio Tuner • Live" : "Radio Tuner • Paused");
                }
            }

            artIv.setImageBitmap(BrandIconView.getIconBitmap(this, BrandIconView.TYPE_RADIO, dp(70)));
            playBtn.setPlaying(isRadioPlaying);
            return;
        }

        if (controller == null) {
            if (isRadioActive && radioFrequency > 0) {
                String freqStr = formatFrequency(radioFrequency);
                String station = getKnownStation(radioFrequency);
                if (station != null) {
                    titleTv.setText(station);
                    if (rdsText != null && !rdsText.isEmpty()) {
                        artistTv.setText(freqStr + " • RDS: " + rdsText);
                    } else {
                        artistTv.setText(freqStr + " • Live");
                    }
                } else {
                    titleTv.setText("FM " + freqStr);
                    if (rdsText != null && !rdsText.isEmpty()) {
                        artistTv.setText("RDS: " + rdsText);
                    } else {
                        artistTv.setText("Radio Tuner • Live");
                    }
                }
                artIv.setImageBitmap(BrandIconView.getIconBitmap(this, BrandIconView.TYPE_RADIO, dp(70)));
                playBtn.setPlaying(isRadioPlaying);
                return;
            }
            titleTv.setText("Nothing playing");
            artistTv.setText("Start music, radio or Bluetooth audio");
            artIv.setImageBitmap(BrandIconView.getIconBitmap(this, BrandIconView.TYPE_SPOTIFY, dp(70)));
            playBtn.setPlaying(false);
            return;
        }

        MediaMetadata m = controller.getMetadata();
        String t = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String a = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null && m != null) {
            a = m.getString(MediaMetadata.METADATA_KEY_ALBUM);
        }

        titleTv.setText(t == null ? "Unknown track" : t);
        artistTv.setText(a == null ? String.valueOf(controller.getPackageName()) : a);

        // Load album art with async URI fetcher and fallback
        AlbumArtLoader.loadArt(this, m, controller.getPackageName(), artIv, ui);

        PlaybackState s = controller.getPlaybackState();
        playBtn.setPlaying(s != null && s.getState() == PlaybackState.STATE_PLAYING);
    }

    void transport(int key) {
        boolean sessionPlaying = controller != null && controller.getPlaybackState() != null
                && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
        boolean hasMusicSession = controller != null && controller.getMetadata() != null
                && controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE) != null;

        if ((!sessionPlaying && !hasMusicSession && isRadioActive)
                || (!sessionPlaying && isRadioPlaying && isRadioActive)) {
            if (key == KeyEvent.KEYCODE_MEDIA_NEXT) {
                // 1. Hardware seek next station via Topway MCU
                sendTwKey(1025, 1, 1);
                // 2. Broadcast seek next to com.tw.radio (Action 7 = RadioActivity.W())
                Intent i = new Intent("com.txznet.launcher.radio.action.send");
                i.putExtra("action", 7);
                sendBroadcast(i);
                // 3. Fallback step frequency up
                float cur = radioFrequency > 0 ? radioFrequency : 105.3f;
                float nextFreq = Math.round((cur + 0.1f) * 10f) / 10f;
                if (nextFreq > 108.0f) nextFreq = 87.5f;
                radioFrequency = nextFreq;
                rdsText = null;
                refreshMedia();
                ui.postDelayed(new Runnable() {
                    @Override public void run() { queryRadio(); }
                }, 400);
                return;
            }
            if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                // 1. Hardware seek prev station via Topway MCU
                sendTwKey(1025, 1, 0);
                // 2. Step frequency down via Action 8
                float cur = radioFrequency > 0 ? radioFrequency : 105.3f;
                float prevFreq = Math.round((cur - 0.1f) * 10f) / 10f;
                if (prevFreq < 87.5f) prevFreq = 108.0f;
                Intent i = new Intent("com.txznet.launcher.radio.action.send");
                i.putExtra("action", 8);
                i.putExtra("frequency", prevFreq);
                sendBroadcast(i);
                radioFrequency = prevFreq;
                rdsText = null;
                refreshMedia();
                ui.postDelayed(new Runnable() {
                    @Override public void run() { queryRadio(); }
                }, 400);
                return;
            }
            // Play / pause toggle
            isRadioPlaying = !isRadioPlaying;
            playBtn.setPlaying(isRadioPlaying);
            if (isRadioPlaying) {
                sendTwKey(1025, 5, 2);
                launchPackage("com.tw.radio");
            } else {
                sendTwKey(1025, 5, 1);
                Intent i = new Intent("com.txznet.launcher.radio.action.send");
                i.putExtra("action", 5);
                sendBroadcast(i);
            }
            refreshMedia();
            return;
        }

        if (controller != null) {
            MediaController.TransportControls tc = controller.getTransportControls();
            if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { tc.skipToPrevious(); return; }
            if (key == KeyEvent.KEYCODE_MEDIA_NEXT) { tc.skipToNext(); return; }
            PlaybackState s = controller.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) tc.pause(); else tc.play();
            return;
        }
        dispatchMediaKey(key);
    }

    void dispatchMediaKey(int key) {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
            }
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ custom drawn views
    static class ControlView extends View {
        static final int PREV = 0, PLAY = 1, NEXT = 2;
        final int type;
        final boolean primary;
        boolean playing;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        ControlView(Context c, int type, boolean primary) {
            super(c);
            this.type = type;
            this.primary = primary;
            setClickable(true);
        }

        void setPlaying(boolean b) { playing = b; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, s = Math.min(w, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(isPressed() ? ACC2 : (primary ? ACC : CARD_HI));
            c.drawCircle(cx, cy, s / 2, p);
            p.setColor(primary ? Color.BLACK : TXT);
            float u = s * 0.18f;
            path.reset();
            if (type == PLAY) {
                if (playing) {
                    c.drawRect(new RectF(cx - u * 0.95f, cy - u, cx - u * 0.25f, cy + u), p);
                    c.drawRect(new RectF(cx + u * 0.25f, cy - u, cx + u * 0.95f, cy + u), p);
                } else {
                    path.moveTo(cx - u * 0.7f, cy - u * 1.1f);
                    path.lineTo(cx + u * 1.1f, cy);
                    path.lineTo(cx - u * 0.7f, cy + u * 1.1f);
                    path.close();
                    c.drawPath(path, p);
                }
            } else {
                float d = type == PREV ? -1 : 1;
                for (int i = 0; i < 2; i++) {
                    float o = (i == 0 ? -0.45f : 0.45f) * u * d * -1;
                    path.reset();
                    path.moveTo(cx + o - d * u * 0.5f, cy - u * 0.9f);
                    path.lineTo(cx + o + d * u * 0.5f, cy);
                    path.lineTo(cx + o - d * u * 0.5f, cy + u * 0.9f);
                    path.close();
                    c.drawPath(path, p);
                }
            }
        }
    }

    static class CleanIconView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path broomPath = new Path();
        float lastW = -1, lastH = -1;

        CleanIconView(Context c) { super(c); }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w == lastW && h == lastH) return;
            lastW = w; lastH = h;
            broomPath.reset();
            // Flared broom bristles
            broomPath.moveTo(w * 0.44f, h * 0.44f);
            broomPath.lineTo(w * 0.56f, h * 0.52f);
            broomPath.lineTo(w * 0.44f, h * 0.76f);
            broomPath.lineTo(w * 0.24f, h * 0.70f);
            broomPath.close();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            p.reset();
            p.setAntiAlias(true);

            // 1. Broom Handle (45 degrees, from top-right down to center)
            p.setColor(0xFFFFF5EE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(w * 0.08f);
            c.drawLine(w * 0.74f, h * 0.24f, w * 0.48f, h * 0.48f, p);

            // 2. Ferrule / Collar
            p.setColor(0xFFFF5500);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(w * 0.48f, h * 0.48f, w * 0.09f, p);

            // 3. Flared sweep bristles in fiery Ember
            p.setColor(0xFFFF5500);
            c.drawPath(broomPath, p);

            // Bristle slit detail lines
            p.setColor(0xFF140E0C);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w * 0.03f);
            c.drawLine(w * 0.42f, h * 0.53f, w * 0.30f, h * 0.72f, p);
            c.drawLine(w * 0.49f, h * 0.56f, w * 0.38f, h * 0.74f, p);

            // 4. Sparkle star (4-point diamond star at top-left cx=0.28, cy=0.28)
            p.setColor(0xFFFFD740);
            p.setStrokeWidth(w * 0.04f);
            c.drawLine(w * 0.20f, h * 0.28f, w * 0.36f, h * 0.28f, p);
            c.drawLine(w * 0.28f, h * 0.20f, w * 0.28f, h * 0.36f, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(w * 0.28f, h * 0.28f, w * 0.035f, p);
        }
    }

    static class GridGlyph extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        GridGlyph(Context c) { super(c); p.setColor(ACC); }
        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()), g = s / 5f, r = g * 0.34f;
            float ox = (getWidth() - s) / 2f, oy = (getHeight() - s) / 2f;
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++)
                    c.drawCircle(ox + g * (j + 1.1f) + g * 0.2f * j, oy + g * (i + 1.1f) + g * 0.2f * i, r, p);
        }
    }

    static class WeatherIconView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int code = 0;
        boolean isDay = true;

        WeatherIconView(Context c) { super(c); }

        void update(int code, boolean isDay) {
            this.code = code;
            this.isDay = isDay;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, r = Math.min(w, h) * 0.35f;
            p.setStyle(Paint.Style.FILL);

            if (code == 0) { // Clear sky
                if (isDay) {
                    p.setColor(ACC);
                    c.drawCircle(cx, cy, r * 0.65f, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(w * 0.07f);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    for (int i = 0; i < 8; i++) {
                        float a = (float) (i * Math.PI / 4.0);
                        float x1 = cx + (float) Math.cos(a) * r * 0.9f;
                        float y1 = cy + (float) Math.sin(a) * r * 0.9f;
                        float x2 = cx + (float) Math.cos(a) * r * 1.3f;
                        float y2 = cy + (float) Math.sin(a) * r * 1.3f;
                        c.drawLine(x1, y1, x2, y2, p);
                    }
                } else {
                    p.setColor(0xFFFF8844);
                    c.drawCircle(cx, cy, r * 0.75f, p);
                    p.setColor(CARD_HI);
                    c.drawCircle(cx + r * 0.45f, cy - r * 0.2f, r * 0.65f, p);
                }
            } else if (code <= 3) { // Partly cloudy
                p.setColor(ACC);
                c.drawCircle(cx + r * 0.35f, cy - r * 0.25f, r * 0.5f, p);
                drawCloud(c, cx - r * 0.15f, cy + r * 0.2f, r * 0.8f, 0xFF8A94A8);
            } else if (code >= 95) { // Thunderstorm
                drawCloud(c, cx, cy - r * 0.2f, r * 0.85f, 0xFF4A5568);
                p.setColor(ACC);
                p.setStyle(Paint.Style.FILL);
                Path bolt = new Path();
                bolt.moveTo(cx - r * 0.1f, cy);
                bolt.lineTo(cx + r * 0.3f, cy);
                bolt.lineTo(cx, cy + r * 0.6f);
                bolt.lineTo(cx + r * 0.15f, cy + r * 0.6f);
                bolt.lineTo(cx - r * 0.2f, cy + r * 1.2f);
                bolt.lineTo(cx - r * 0.05f, cy + r * 0.75f);
                bolt.lineTo(cx - r * 0.25f, cy + r * 0.75f);
                bolt.close();
                c.drawPath(bolt, p);
            } else { // Rain / Drizzle / Showers / Fog
                drawCloud(c, cx, cy - r * 0.25f, r * 0.85f, 0xFF6B7280);
                p.setColor(ACC);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(w * 0.06f);
                p.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(cx - r * 0.35f, cy + r * 0.55f, cx - r * 0.5f, cy + r * 0.95f, p);
                c.drawLine(cx, cy + r * 0.6f, cx - r * 0.15f, cy + r * 1.0f, p);
                c.drawLine(cx + r * 0.35f, cy + r * 0.55f, cx + r * 0.2f, cy + r * 0.95f, p);
            }
        }

        private void drawCloud(Canvas c, float x, float y, float cr, int color) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawCircle(x, y, cr * 0.48f, p);
            c.drawCircle(x - cr * 0.42f, y + cr * 0.15f, cr * 0.35f, p);
            c.drawCircle(x + cr * 0.42f, y + cr * 0.15f, cr * 0.35f, p);
            c.drawRect(x - cr * 0.42f, y + cr * 0.12f, x + cr * 0.42f, y + cr * 0.5f, p);
        }
    }
}
