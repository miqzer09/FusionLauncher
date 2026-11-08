package com.vios.apex;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Animated Weather Card matching user reference:
 * - Vibrant gradient sky background with rounded corners
 * - Big temperature readout (e.g. 25°)
 * - Condition description ("Mostly cloudy", "Drizzle")
 * - High/Low colored indicators: ▲ red, ▼ cyan
 * - Animated rotating sunbeams & glowing golden sun
 * - Animated drifting fluffy clouds & rain streaks
 * - 4-Day frosted forecast pill cards at bottom
 */
public class AnimatedWeatherCard extends FrameLayout {

    public static class DayForecast {
        public String dayName = "";
        public int code = 0;
        public int minTemp = 0;
        public int maxTemp = 0;
    }

    private final SkyCanvasView skyView;
    private final TextView tempTv;
    private final TextView condTv;
    private final TextView highLowTv;
    private final LinearLayout forecastRow;

    private final DayCardView[] dayCards = new DayCardView[4];

    public AnimatedWeatherCard(Context c, float density, float radiusPx) {
        super(c);
        setBackgroundColor(Color.TRANSPARENT);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radiusPx);
            }
        });

        // 1. Animated Sky Background (Canvas)
        skyView = new SkyCanvasView(c, density, radiusPx);
        addView(skyView, new LayoutParams(-1, -1));

        // 2. Foreground Content Overlay
        LinearLayout overlay = new LinearLayout(c);
        overlay.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(10 * density);
        int padTop = Math.round(6 * density);
        int padBot = Math.round(6 * density);
        overlay.setPadding(padH, padTop, padH, padBot);
        addView(overlay, new LayoutParams(-1, -1));

        // Top Row: Big Temp + Condition & High/Low
        LinearLayout topRow = new LinearLayout(c);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        overlay.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout textCol = new LinearLayout(c);
        textCol.setOrientation(LinearLayout.HORIZONTAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));

        tempTv = new TextView(c);
        tempTv.setText("--°");
        tempTv.setTextSize(28);
        tempTv.setTextColor(Color.WHITE);
        tempTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tempTv.setIncludeFontPadding(false);
        textCol.addView(tempTv);

        LinearLayout condCol = new LinearLayout(c);
        condCol.setOrientation(LinearLayout.VERTICAL);
        condCol.setPadding(Math.round(8 * density), 0, 0, 0);
        textCol.addView(condCol, new LinearLayout.LayoutParams(-2, -2));

        condTv = new TextView(c);
        condTv.setText("Loading…");
        condTv.setTextSize(11);
        condTv.setTextColor(0xFFFFFFFF);
        condTv.setTypeface(Typeface.DEFAULT_BOLD);
        condTv.setSingleLine(true);
        condCol.addView(condTv);

        highLowTv = new TextView(c);
        highLowTv.setText("▲ --°  ▼ --°");
        highLowTv.setTextSize(10);
        highLowTv.setTypeface(Typeface.DEFAULT_BOLD);
        condCol.addView(highLowTv);

        // Spacer between top info and forecast pills
        View spacer = new View(c);
        overlay.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Bottom 4-Day Forecast Row (frosted glass pills, compact for height 1)
        forecastRow = new LinearLayout(c);
        forecastRow.setOrientation(LinearLayout.HORIZONTAL);
        forecastRow.setGravity(Gravity.CENTER);
        overlay.addView(forecastRow, new LinearLayout.LayoutParams(-1, Math.round(48 * density)));

        for (int i = 0; i < 4; i++) {
            dayCards[i] = new DayCardView(c, density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = Math.round(4 * density);
            forecastRow.addView(dayCards[i], lp);
        }
    }

    public void updateWeather(int temp, int code, int high, int low, String cond, DayForecast[] forecasts, boolean isDay) {
        tempTv.setText(temp + "°");
        condTv.setText(cond);

        // Colored High / Low arrows: Red ⇧, Cyan ⇩ matching reference image
        String hlText = "⇧ " + high + "°   ⇩ " + low + "°";
        SpannableString ss = new SpannableString(hlText);
        int upIdx = hlText.indexOf("⇧");
        if (upIdx >= 0) ss.setSpan(new ForegroundColorSpan(0xFFFF4848), upIdx, upIdx + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int dnIdx = hlText.indexOf("⇩");
        if (dnIdx >= 0) ss.setSpan(new ForegroundColorSpan(0xFF40C4FF), dnIdx, dnIdx + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        highLowTv.setText(ss);

        skyView.setWeather(code, isDay);

        if (forecasts != null) {
            for (int i = 0; i < 4 && i < forecasts.length; i++) {
                if (forecasts[i] != null) {
                    dayCards[i].setData(forecasts[i]);
                }
            }
        }
    }

    public void startAnimation() {
        skyView.startAnim();
    }

    public void stopAnimation() {
        skyView.stopAnim();
    }

    // -------------------------------------------------------------------------
    // Canvas View with Realistic Sky, Animated Sunbeams, and Drifting Clouds
    // -------------------------------------------------------------------------
    static class SkyCanvasView extends View {
        private final float s;
        private final float r;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        private ValueAnimator animator;
        private float animTime = 0;
        private int code = 0;
        private boolean isDay = true;

        SkyCanvasView(Context c, float density, float radius) {
            super(c);
            s = density;
            r = radius;
            setLayerType(LAYER_TYPE_HARDWARE, null);
            startAnim();
        }

        void setWeather(int c, boolean day) {
            code = c;
            isDay = day;
            invalidate();
        }

        void startAnim() {
            if (animator != null && animator.isRunning()) return;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(4000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    animTime = (float) a.getCurrentPlayTime() / 1000f;
                    invalidate();
                }
            });
            animator.start();
        }

        void stopAnim() {
            if (animator != null) animator.cancel();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            // 1. Atmospheric Sky Gradient Background (Dark Twilight matching car theme)
            p.setStyle(Paint.Style.FILL);
            if (isDay) {
                // Daytime rich azure
                p.setShader(new LinearGradient(0, 0, w, h, 0xFF0D74EC, 0xFF063B80, Shader.TileMode.CLAMP));
            } else {
                // Night / Twilight deep atmospheric dark (matches Amber car theme)
                p.setShader(new LinearGradient(0, 0, w, h, 0xFF14243B, 0xFF0A1322, Shader.TileMode.CLAMP));
            }
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);

            // 2. Animated Celestial Body (Top-Right, optimized for 1-unit height)
            float sunX = w * 0.82f;
            float sunY = h * 0.28f;
            float sunR = Math.min(w, h) * 0.26f;

            if (isDay) {
                // Rotating radiant sunbeams
                float rayAngle = (animTime * 15f) % 360f;
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0x80FFAE00);
                p.setStrokeWidth(2f * s);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 14; i++) {
                    double a = Math.toRadians(rayAngle + i * (360f / 14f));
                    float r1 = sunR * 1.10f;
                    float r2 = sunR * (1.32f + 0.08f * (float) Math.sin(animTime * 3f + i));
                    c.drawLine(sunX + (float) Math.cos(a) * r1, sunY + (float) Math.sin(a) * r1,
                               sunX + (float) Math.cos(a) * r2, sunY + (float) Math.sin(a) * r2, p);
                }

                // Outer Glowing Halo
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x35FFAE00);
                c.drawCircle(sunX, sunY, sunR * 1.22f, p);

                // Golden 3D Spherical Sun Body
                p.setShader(new LinearGradient(sunX - sunR, sunY - sunR, sunX + sunR, sunY + sunR,
                        0xFFFFF176, 0xFFFF8F00, Shader.TileMode.CLAMP));
                c.drawCircle(sunX, sunY, sunR, p);
                p.setShader(null);
            } else {
                // Glowing Moon with Halo
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x30FFF59D);
                c.drawCircle(sunX, sunY, sunR * 1.15f, p);

                Path moon = new Path();
                moon.addCircle(sunX, sunY, sunR * 0.85f, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(sunX + sunR * 0.42f, sunY - sunR * 0.22f, sunR * 0.72f, Path.Direction.CW);
                moon.op(cut, Path.Op.DIFFERENCE);

                p.setColor(0xFFFFF176);
                c.drawPath(moon, p);
            }

            // 3. Drifting Fluffy Volumetric Clouds
            float cloudOffset = (float) Math.sin(animTime * 0.8f) * 5f * s;
            drawFluffyCloud(c, sunX - 8 * s + cloudOffset, sunY + sunR * 0.45f, sunR * 1.15f, 0xF0FFFFFF);
            drawFluffyCloud(c, sunX - 35 * s + cloudOffset * 0.7f, sunY + sunR * 0.75f, sunR * 0.85f, 0xAAFFFFFF);

            // 4. Raindrops Animation if rainy
            if (code >= 51 && code <= 82) {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xD0E0F7FF);
                p.setStrokeWidth(1.8f * s);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 7; i++) {
                    float rx = w * (0.18f + (i * 0.09f));
                    float ry = ((animTime * 120f + i * 35f) % (h * 0.5f)) + h * 0.35f;
                    c.drawLine(rx, ry, rx - 3 * s, ry + 8 * s, p);
                }
            }
        }

        private void drawFluffyCloud(Canvas c, float cx, float cy, float cr, int color) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawCircle(cx, cy, cr * 0.45f, p);
            c.drawCircle(cx - cr * 0.45f, cy + cr * 0.12f, cr * 0.36f, p);
            c.drawCircle(cx + cr * 0.45f, cy + cr * 0.12f, cr * 0.36f, p);
            c.drawCircle(cx + cr * 0.18f, cy - cr * 0.22f, cr * 0.42f, p);
            c.drawRect(cx - cr * 0.45f, cy + cr * 0.05f, cx + cr * 0.45f, cy + cr * 0.48f, p);
        }
    }

    // -------------------------------------------------------------------------
    // Frosted Pill Card for Daily Forecast
    // -------------------------------------------------------------------------
    static class DayCardView extends LinearLayout {
        private final TextView dayTv;
        private final MiniIconView iconView;
        private final TextView rangeTv;

        DayCardView(Context c, float density) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            int p = Math.round(2 * density);
            setPadding(p, Math.round(2 * density), p, Math.round(2 * density));

            // Frosted glass background
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(0x35000000); // semi-transparent dark frost
            gd.setCornerRadius(10 * density);
            gd.setStroke(Math.round(1 * density), 0x25FFFFFF);
            setBackground(gd);

            dayTv = new TextView(c);
            dayTv.setText("---");
            dayTv.setTextSize(9);
            dayTv.setTextColor(0xFFFFFFFF);
            dayTv.setTypeface(Typeface.DEFAULT_BOLD);
            dayTv.setGravity(Gravity.CENTER);
            dayTv.setIncludeFontPadding(false);
            addView(dayTv);

            iconView = new MiniIconView(c, density);
            int iconSize = Math.round(16 * density);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(iconSize, iconSize);
            ip.topMargin = Math.round(1 * density);
            ip.bottomMargin = Math.round(1 * density);
            addView(iconView, ip);

            rangeTv = new TextView(c);
            rangeTv.setText("--~--");
            rangeTv.setTextSize(8.5f);
            rangeTv.setTextColor(0xFFFFFFFF);
            rangeTv.setTypeface(Typeface.DEFAULT_BOLD);
            rangeTv.setGravity(Gravity.CENTER);
            rangeTv.setIncludeFontPadding(false);
            addView(rangeTv);
        }

        void setData(DayForecast d) {
            dayTv.setText(d.dayName);
            iconView.setCode(d.code);
            rangeTv.setText(d.minTemp + "°~" + d.maxTemp + "°");
        }
    }

    static class MiniIconView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int code = 0;

        MiniIconView(Context c, float density) {
            super(c);
        }

        void setCode(int c) {
            code = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, r = Math.min(w, h) * 0.42f;
            p.setStyle(Paint.Style.FILL);
            if (code == 0) { // Golden 3D Sun with radiant halo
                p.setColor(0x40FFB300);
                c.drawCircle(cx, cy, r * 1.05f, p);
                p.setColor(0xFFFFB300);
                c.drawCircle(cx, cy, r * 0.75f, p);
            } else if (code <= 3) { // Sun + Fluffy Cloud
                p.setColor(0xFFFFB300);
                c.drawCircle(cx + r * 0.35f, cy - r * 0.25f, r * 0.52f, p);
                p.setColor(0xF0FFFFFF);
                c.drawCircle(cx - r * 0.2f, cy + r * 0.18f, r * 0.55f, p);
                c.drawCircle(cx + r * 0.22f, cy + r * 0.22f, r * 0.44f, p);
                c.drawRect(cx - r * 0.2f, cy + r * 0.18f, cx + r * 0.22f, cy + r * 0.65f, p);
            } else { // Rain Cloud with Cyan Drops
                p.setColor(0xE6FFFFFF);
                c.drawCircle(cx, cy - r * 0.18f, r * 0.55f, p);
                c.drawCircle(cx - r * 0.32f, cy + r * 0.05f, r * 0.42f, p);
                c.drawCircle(cx + r * 0.32f, cy + r * 0.05f, r * 0.42f, p);
                c.drawRect(cx - r * 0.32f, cy + r * 0.05f, cx + r * 0.32f, cy + r * 0.45f, p);

                p.setColor(0xFF40C4FF);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f);
                p.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(cx - r * 0.25f, cy + r * 0.55f, cx - r * 0.35f, cy + r * 0.85f, p);
                c.drawLine(cx + r * 0.22f, cy + r * 0.55f, cx + r * 0.12f, cy + r * 0.85f, p);
            }
        }
    }
}
