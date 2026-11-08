package com.vios.apex;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * High-performance, zero-allocation vector icon renderer for the 8 launcher tiles.
 * Renders brand-authentic colors and iconography (Spotify, Radio, Waze, YouTube,
 * EQ, Google Maps, Chrome, and All Apps) in a unified, modern automotive squircle format.
 */
public class BrandIconView extends View {
    public static final int TYPE_SPOTIFY = 0;
    public static final int TYPE_RADIO = 1;
    public static final int TYPE_WAZE = 2;
    public static final int TYPE_YOUTUBE = 3;
    public static final int TYPE_EQ = 4;
    public static final int TYPE_MAPS = 5;
    public static final int TYPE_CHROME = 6;
    public static final int TYPE_ALL_APPS = 7;

    private final int type;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rfBg = new RectF();
    private final RectF rfTemp = new RectF();

    // Cached geometries
    private float lastW = -1, lastH = -1;
    private Shader bgShader;
    private Shader barShader1, barShader2, barShader3;

    // Spotify waves
    private final Path wave1 = new Path();
    private final Path wave2 = new Path();
    private final Path wave3 = new Path();

    // YouTube triangle
    private final Path playTri = new Path();

    // Waze car
    private final Path wazeBody = new Path();

    // Maps pin
    private final Path mapLeft = new Path();
    private final Path mapRight = new Path();
    private final Path mapTip = new Path();

    // Chrome segments
    private final Path chromeRed = new Path();
    private final Path chromeYellow = new Path();
    private final Path chromeGreen = new Path();

    // 9 colors for All Apps 3x3 matrix
    private static final int[] APP_COLORS = {
        0xFFFF5252, 0xFF448AFF, 0xFF69F0AE,
        0xFFFFD740, 0xFFE040FB, 0xFFFF6E40,
        0xFF18FFFF, 0xFFB388FF, 0xFFFFFFFF
    };

    public BrandIconView(Context context, int type) {
        super(context);
        this.type = type;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        if (w == lastW && h == lastH) return;
        lastW = w;
        lastH = h;

        float s = Math.min(w, h);
        float ox = (w - s) / 2f;
        float oy = (h - s) / 2f;

        rfBg.set(ox + s * 0.04f, oy + s * 0.04f, ox + s * 0.96f, oy + s * 0.96f);

        switch (type) {
            case TYPE_SPOTIFY:
                bgShader = new LinearGradient(ox, oy, ox, oy + s,
                        0xFF25E768, 0xFF149742, Shader.TileMode.CLAMP);
                wave1.reset();
                wave1.moveTo(ox + s * 0.25f, oy + s * 0.41f);
                wave1.cubicTo(ox + s * 0.42f, oy + s * 0.34f, ox + s * 0.60f, oy + s * 0.36f, ox + s * 0.75f, oy + s * 0.44f);

                wave2.reset();
                wave2.moveTo(ox + s * 0.28f, oy + s * 0.53f);
                wave2.cubicTo(ox + s * 0.43f, oy + s * 0.47f, ox + s * 0.58f, oy + s * 0.49f, ox + s * 0.71f, oy + s * 0.56f);

                wave3.reset();
                wave3.moveTo(ox + s * 0.31f, oy + s * 0.65f);
                wave3.cubicTo(ox + s * 0.43f, oy + s * 0.60f, ox + s * 0.55f, oy + s * 0.62f, ox + s * 0.67f, oy + s * 0.67f);
                break;

            case TYPE_RADIO:
                bgShader = new LinearGradient(ox, oy, ox + s, oy + s,
                        0xFFFF6A00, 0xFFEE0979, Shader.TileMode.CLAMP);
                break;

            case TYPE_WAZE:
                bgShader = new LinearGradient(ox, oy, ox + s, oy + s,
                        0xFF00D5F5, 0xFF00A3FF, Shader.TileMode.CLAMP);
                wazeBody.reset();
                rfTemp.set(ox + s * 0.22f, oy + s * 0.24f, ox + s * 0.78f, oy + s * 0.62f);
                wazeBody.addRoundRect(rfTemp, s * 0.18f, s * 0.18f, Path.Direction.CW);
                break;

            case TYPE_YOUTUBE:
                bgShader = new LinearGradient(ox, oy, ox, oy + s,
                        0xFFFF2020, 0xFFE60000, Shader.TileMode.CLAMP);
                playTri.reset();
                playTri.moveTo(ox + s * 0.40f, oy + s * 0.33f);
                playTri.lineTo(ox + s * 0.68f, oy + s * 0.50f);
                playTri.lineTo(ox + s * 0.40f, oy + s * 0.67f);
                playTri.close();
                break;

            case TYPE_EQ:
                barShader1 = new LinearGradient(ox, oy + s * 0.78f, ox, oy + s * 0.48f,
                        0xFF00E676, 0xFF76FF03, Shader.TileMode.CLAMP);
                barShader2 = new LinearGradient(ox, oy + s * 0.78f, ox, oy + s * 0.34f,
                        new int[]{0xFF00E676, 0xFFFFEA00, 0xFFFF9100}, null, Shader.TileMode.CLAMP);
                barShader3 = new LinearGradient(ox, oy + s * 0.78f, ox, oy + s * 0.22f,
                        new int[]{0xFF00E676, 0xFFFFEA00, 0xFFFF1744}, null, Shader.TileMode.CLAMP);
                break;

            case TYPE_MAPS:
                float cx = ox + s * 0.50f;
                float cy = oy + s * 0.38f;
                float hr = s * 0.18f;
                float tipX = cx;
                float tipY = oy + s * 0.75f;

                mapLeft.reset();
                mapLeft.moveTo(cx - hr, cy);
                mapLeft.cubicTo(cx - hr, cy + hr * 0.8f, cx - s * 0.08f, tipY - s * 0.08f, tipX, tipY);
                mapLeft.lineTo(cx, cy);
                mapLeft.close();

                mapRight.reset();
                mapRight.moveTo(cx + hr, cy);
                mapRight.cubicTo(cx + hr, cy + hr * 0.8f, cx + s * 0.08f, tipY - s * 0.08f, tipX, tipY);
                mapRight.lineTo(cx, cy);
                mapRight.close();

                mapTip.reset();
                mapTip.moveTo(tipX - s * 0.07f, tipY - s * 0.08f);
                mapTip.lineTo(tipX + s * 0.07f, tipY - s * 0.08f);
                mapTip.lineTo(tipX, tipY);
                mapTip.close();
                break;

            case TYPE_CHROME:
                float ccx = ox + s * 0.50f;
                float ccy = oy + s * 0.50f;
                float cr = s * 0.33f;
                rfTemp.set(ccx - cr, ccy - cr, ccx + cr, ccy + cr);

                chromeRed.reset();
                chromeRed.arcTo(rfTemp, 210, 120, true);
                chromeRed.lineTo(ccx, ccy);
                chromeRed.close();

                chromeYellow.reset();
                chromeYellow.arcTo(rfTemp, 330, 120, true);
                chromeYellow.lineTo(ccx, ccy);
                chromeYellow.close();

                chromeGreen.reset();
                chromeGreen.arcTo(rfTemp, 90, 120, true);
                chromeGreen.lineTo(ccx, ccy);
                chromeGreen.close();
                break;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float s = Math.min(w, h);
        float ox = (w - s) / 2f;
        float oy = (h - s) / 2f;
        float squircleR = s * 0.24f;

        paint.reset();
        paint.setAntiAlias(true);

        switch (type) {
            case TYPE_SPOTIFY:
                // Background Gradient
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(bgShader);
                canvas.drawRoundRect(rfBg, squircleR, squircleR, paint);
                paint.setShader(null);

                // Acoustic soundwaves in dark charcoal #121212
                paint.setColor(0xFF121212);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);

                paint.setStrokeWidth(s * 0.088f);
                canvas.drawPath(wave1, paint);

                paint.setStrokeWidth(s * 0.076f);
                canvas.drawPath(wave2, paint);

                paint.setStrokeWidth(s * 0.064f);
                canvas.drawPath(wave3, paint);
                break;

            case TYPE_RADIO:
                // Vibrant Orange/Pink gradient
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(bgShader);
                canvas.drawRoundRect(rfBg, squircleR, squircleR, paint);
                paint.setShader(null);

                // Radio chassis plate
                rfTemp.set(ox + s * 0.14f, oy + s * 0.22f, ox + s * 0.86f, oy + s * 0.80f);
                paint.setColor(0xFF1E1F24);
                canvas.drawRoundRect(rfTemp, s * 0.10f, s * 0.10f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0x40FFFFFF);
                paint.setStrokeWidth(s * 0.015f);
                canvas.drawRoundRect(rfTemp, s * 0.10f, s * 0.10f, paint);

                // Antenna
                paint.setColor(0xFFFFFFFF);
                paint.setStrokeWidth(s * 0.035f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(ox + s * 0.24f, oy + s * 0.22f, ox + s * 0.38f, oy + s * 0.11f, paint);

                // LCD frequency display
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF0B0C0E);
                rfTemp.set(ox + s * 0.20f, oy + s * 0.28f, ox + s * 0.56f, oy + s * 0.46f);
                canvas.drawRoundRect(rfTemp, s * 0.04f, s * 0.04f, paint);

                paint.setColor(0xFF00F0FF);
                paint.setTextSize(s * 0.11f);
                paint.setTypeface(Typeface.MONOSPACE);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("104.5", ox + s * 0.38f, oy + s * 0.40f, paint);

                // Tuner dial knob
                paint.setColor(0xFFE0E2EC);
                canvas.drawCircle(ox + s * 0.32f, oy + s * 0.62f, s * 0.085f, paint);
                paint.setColor(0xFF9AA0B0);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.015f);
                canvas.drawCircle(ox + s * 0.32f, oy + s * 0.62f, s * 0.085f, paint);
                paint.setColor(0xFFFF2A00);
                paint.setStrokeWidth(s * 0.025f);
                canvas.drawLine(ox + s * 0.32f, oy + s * 0.62f, ox + s * 0.32f, oy + s * 0.55f, paint);

                // Speaker grille
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF0C0D10);
                canvas.drawCircle(ox + s * 0.68f, oy + s * 0.52f, s * 0.14f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xFF3A3B44);
                paint.setStrokeWidth(s * 0.02f);
                canvas.drawCircle(ox + s * 0.68f, oy + s * 0.52f, s * 0.14f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF1F2026);
                canvas.drawCircle(ox + s * 0.68f, oy + s * 0.52f, s * 0.09f, paint);
                paint.setColor(0xFFFF6A00);
                canvas.drawCircle(ox + s * 0.68f, oy + s * 0.52f, s * 0.04f, paint);
                break;

            case TYPE_WAZE:
                // Cyan gradient background
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(bgShader);
                canvas.drawRoundRect(rfBg, squircleR, squircleR, paint);
                paint.setShader(null);

                // Waze mascot body (white)
                paint.setColor(0xFFFFFFFF);
                canvas.drawPath(wazeBody, paint);

                // Wheels
                paint.setColor(0xFF24262E);
                canvas.drawCircle(ox + s * 0.36f, oy + s * 0.67f, s * 0.08f, paint);
                canvas.drawCircle(ox + s * 0.64f, oy + s * 0.67f, s * 0.08f, paint);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(ox + s * 0.36f, oy + s * 0.67f, s * 0.035f, paint);
                canvas.drawCircle(ox + s * 0.64f, oy + s * 0.67f, s * 0.035f, paint);

                // Eyes
                paint.setColor(0xFF24262E);
                canvas.drawCircle(ox + s * 0.42f, oy + s * 0.41f, s * 0.045f, paint);
                canvas.drawCircle(ox + s * 0.58f, oy + s * 0.41f, s * 0.045f, paint);

                // Smile
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(s * 0.028f);
                rfTemp.set(ox + s * 0.45f, oy + s * 0.46f, ox + s * 0.55f, oy + s * 0.54f);
                canvas.drawArc(rfTemp, 20, 140, false, paint);
                break;

            case TYPE_YOUTUBE:
                // Saturated YouTube Red gradient
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(bgShader);
                canvas.drawRoundRect(rfBg, squircleR, squircleR, paint);
                paint.setShader(null);

                // White play triangle
                paint.setColor(0xFFFFFFFF);
                canvas.drawPath(playTri, paint);
                break;

            case TYPE_EQ:
                // Dark brushed plate
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF16171D);
                canvas.drawRoundRect(rfBg, squircleR, squircleR, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xFF343744);
                paint.setStrokeWidth(s * 0.02f);
                canvas.drawRoundRect(rfBg, squircleR, squircleR, paint);

                // 5 vertical VU spectrum bars
                paint.setStyle(Paint.Style.FILL);
                float barW = s * 0.08f;
                float barR = s * 0.035f;

                // Bar 1
                paint.setShader(barShader1);
                rfTemp.set(ox + s * 0.20f, oy + s * 0.48f, ox + s * 0.20f + barW, oy + s * 0.78f);
                canvas.drawRoundRect(rfTemp, barR, barR, paint);

                // Bar 2
                paint.setShader(barShader2);
                rfTemp.set(ox + s * 0.33f, oy + s * 0.34f, ox + s * 0.33f + barW, oy + s * 0.78f);
                canvas.drawRoundRect(rfTemp, barR, barR, paint);

                // Bar 3 (Peak)
                paint.setShader(barShader3);
                rfTemp.set(ox + s * 0.46f, oy + s * 0.22f, ox + s * 0.46f + barW, oy + s * 0.78f);
                canvas.drawRoundRect(rfTemp, barR, barR, paint);

                // Bar 4
                paint.setShader(barShader2);
                rfTemp.set(ox + s * 0.59f, oy + s * 0.38f, ox + s * 0.59f + barW, oy + s * 0.78f);
                canvas.drawRoundRect(rfTemp, barR, barR, paint);

                // Bar 5
                paint.setShader(barShader1);
                rfTemp.set(ox + s * 0.72f, oy + s * 0.52f, ox + s * 0.72f + barW, oy + s * 0.78f);
                canvas.drawRoundRect(rfTemp, barR, barR, paint);
                paint.setShader(null);
                break;

            case TYPE_MAPS:
                // Clean white card background
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                canvas.drawRoundRect(rfBg, squircleR, squircleR, paint);

                // Red Bulb
                paint.setColor(0xFFEA4335);
                canvas.drawCircle(ox + s * 0.50f, oy + s * 0.38f, s * 0.18f, paint);

                // Left Blue wing
                paint.setColor(0xFF4285F4);
                canvas.drawPath(mapLeft, paint);

                // Right Green wing
                paint.setColor(0xFF34A853);
                canvas.drawPath(mapRight, paint);

                // Yellow tip
                paint.setColor(0xFFFBBC04);
                canvas.drawPath(mapTip, paint);

                // Aperture center white dot
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(ox + s * 0.50f, oy + s * 0.38f, s * 0.07f, paint);
                break;

            case TYPE_CHROME:
                // Clean white card background
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                canvas.drawRoundRect(rfBg, squircleR, squircleR, paint);

                float ccx = ox + s * 0.50f;
                float ccy = oy + s * 0.50f;

                // Red segment (top)
                paint.setColor(0xFFEA4335);
                canvas.drawPath(chromeRed, paint);

                // Yellow segment (right-bottom)
                paint.setColor(0xFFFBBC04);
                canvas.drawPath(chromeYellow, paint);

                // Green segment (left-bottom)
                paint.setColor(0xFF34A853);
                canvas.drawPath(chromeGreen, paint);

                // White ring
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(ccx, ccy, s * 0.16f, paint);

                // Blue core
                paint.setColor(0xFF1A73E8);
                canvas.drawCircle(ccx, ccy, s * 0.13f, paint);
                break;

            case TYPE_ALL_APPS:
                // Dark slate plate
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF1F212A);
                canvas.drawRoundRect(rfBg, squircleR, squircleR, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xFF383C4C);
                paint.setStrokeWidth(s * 0.02f);
                canvas.drawRoundRect(rfBg, squircleR, squircleR, paint);

                // 3x3 vibrant rounded app pebbles
                paint.setStyle(Paint.Style.FILL);
                float startX = ox + s * 0.22f;
                float startY = oy + s * 0.22f;
                float gap = s * 0.20f;
                float pebW = s * 0.16f;
                float pebR = s * 0.045f;

                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int idx = row * 3 + col;
                        paint.setColor(APP_COLORS[idx]);
                        rfTemp.set(startX + col * gap, startY + row * gap,
                                startX + col * gap + pebW, startY + row * gap + pebW);
                        canvas.drawRoundRect(rfTemp, pebR, pebR, paint);
                    }
                }
                break;
        }
    }
}
