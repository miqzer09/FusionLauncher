package com.vios.apex;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class RadioReceiver extends BroadcastReceiver {

    public interface Callback {
        void onRadioUpdate(float freq, boolean isPlaying);
        void onRdsUpdate(String rds);
    }

    public static volatile Callback callback;

    public static float normalize(float f) {
        if (f <= 0) return 0f;
        if (f >= 8700f && f <= 109000f) return f / 100f; // e.g. 10530 -> 105.3
        if (f >= 870f && f <= 1085f) return f / 10f;     // e.g. 1053 -> 105.3
        return f;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String act = intent.getAction();
        SharedPreferences prefs = context.getSharedPreferences("apex", Context.MODE_PRIVATE);

        // Check for any broadcast RDS extras
        String rds = intent.getStringExtra("rds");
        if (rds == null) rds = intent.getStringExtra("name");
        if (rds == null) rds = intent.getStringExtra("ps");
        if (rds == null) rds = intent.getStringExtra("rt");
        if (rds != null && !rds.trim().isEmpty() && callback != null) {
            callback.onRdsUpdate(rds.trim());
        }

        if ("com.txznet.launcher.radio.action.recv".equals(act)) {
            int action = intent.getIntExtra("action", -1);
            if (action == 1) {
                float freq = intent.getFloatExtra("frequency", 0f);
                if (freq <= 0) freq = intent.getIntExtra("frequency", 0);
                freq = normalize(freq);
                if (freq > 0) {
                    prefs.edit().putFloat("radio_freq", freq).putBoolean("radio_active", true).apply();
                    if (callback != null) callback.onRadioUpdate(freq, true);
                }
            } else if (action == 2) {
                float f = prefs.getFloat("radio_freq", 0f);
                prefs.edit().putBoolean("radio_active", true).apply();
                if (callback != null) callback.onRadioUpdate(f, true);
            } else if (action == 3) {
                float f = prefs.getFloat("radio_freq", 0f);
                prefs.edit().putBoolean("radio_active", false).apply();
                if (callback != null) callback.onRadioUpdate(f, false);
            }
        } else if ("android.intent.action.ACTION_RADIO_OFF".equals(act)) {
            float f = prefs.getFloat("radio_freq", 0f);
            prefs.edit().putBoolean("radio_active", false).apply();
            if (callback != null) callback.onRadioUpdate(f, false);
        }
    }
}
