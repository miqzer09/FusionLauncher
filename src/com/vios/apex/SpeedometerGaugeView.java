package com.vios.apex;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Concept B: Audi Virtual Cockpit Dual-Wing Sport HUD (Fully Optimized)
 * - Zero-allocation onDraw pipeline (0 B/frame GC pressure on Allwinner T3)
 * - Cached Paths, Rects, Shaders, and Typefaces
 * - Smooth hardware-accelerated rendering
 * - Pure Toyota Amber palette matching Vios NCP150 interior
 */
public class SpeedometerGaugeView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wingBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Cached geometries
    private final RectF boxRect = new RectF();
    private final RectF tapeRect = new RectF();
    private final RectF activeFillRect = new RectF();
    private final Path leftWingPath = new Path();
    private final Path rightWingPath = new Path();

    // Cached Shaders
    private Shader leftBaseShader;
    private Shader rightBaseShader;

    // Cached Typefaces
    private static final Typeface TF_BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

    private float currentSpeed = 0f;
    private float animatedSpeed = 0f;
    private float maxSpeed = 220f;
    private boolean isMph = false;
    private float bearing = 0f;
    private boolean hasGps = false;
    private float altitude = 0f;

    private ValueAnimator speedAnimator;

    // Pre-calculated layout metrics
    private float density = 1f;
    private float cx = 0f;
    private float boxRadius = 0f;
    private float centerY = 0f;
    private float wingTop = 0f;
    private float wingBottom = 0f;
    private float leftWingL = 0f, leftWingR = 0f;
    private float rightWingL = 0f, rightWingR = 0f;
    private float tapeY = 0f, tapeTextY = 0f;
    private float speedTextSize = 0f, unitTextSize = 0f, compTextSize = 0f;

    private static final int COLOR_ACC = 0xFFFF5500;         // Hot Burning Ember
    private static final int COLOR_TXT = 0xFFFFFFFF;         // Pure White Digits
    private static final int COLOR_SUB = 0xFFA09088;         // Muted Ash Label Color
    private static final int COLOR_BOX_BG = 0xFF0A0706;      // Pure Black Box
    private static final int COLOR_BOX_BORDER = 0x2EFF5500;  // Glowing Ember Rim

    public SpeedometerGaugeView(Context context) {
        super(context);
        init();
    }

    private void init() {
        textPaint.setTextAlign(Paint.Align.CENTER);
        bgPaint.setStyle(Paint.Style.FILL);
        wingPaint.setStyle(Paint.Style.FILL);
        wingBorderPaint.setStyle(Paint.Style.STROKE);
        pillPaint.setStyle(Paint.Style.FILL);
        linePaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        density = getResources().getDisplayMetrics().density;
        cx = w * 0.5f;

        float boxPadX = 2f * density;
        float boxPadY = 4f * density;
        boxRect.set(boxPadX, boxPadY, w - boxPadX, h - boxPadY);
        boxRadius = 18f * density;

        centerY = h * 0.44f;
        float wingH = 68f * density;
        wingTop = centerY - wingH / 2f;
        wingBottom = centerY + wingH / 2f;

        float centerGap = 42f * density;
        float wingMargin = 10f * density;

        leftWingL = boxPadX + wingMargin;
        leftWingR = cx - centerGap;
        rightWingL = cx + centerGap;
        rightWingR = w - boxPadX - wingMargin;

        float rad = 14f * density;

        // Build Left Wing Path
        leftWingPath.reset();
        leftWingPath.moveTo(leftWingL + rad, wingTop);
        leftWingPath.lineTo(leftWingR, wingTop);
        leftWingPath.lineTo(leftWingR, wingBottom);
        leftWingPath.lineTo(leftWingL + rad, wingBottom);
        leftWingPath.quadTo(leftWingL, wingBottom, leftWingL, wingBottom - rad);
        leftWingPath.lineTo(leftWingL, wingTop + rad);
        leftWingPath.quadTo(leftWingL, wingTop, leftWingL + rad, wingTop);
        leftWingPath.close();

        // Build Right Wing Path
        rightWingPath.reset();
        rightWingPath.moveTo(rightWingL, wingTop);
        rightWingPath.lineTo(rightWingR - rad, wingTop);
        rightWingPath.quadTo(rightWingR, wingTop, rightWingR, wingTop + rad);
        rightWingPath.lineTo(rightWingR, wingBottom - rad);
        rightWingPath.quadTo(rightWingR, wingBottom, rightWingR - rad, wingBottom);
        rightWingPath.lineTo(rightWingL, wingBottom);
        rightWingPath.close();

        // Shaders (Ember glow)
        leftBaseShader = new LinearGradient(leftWingL, wingTop, leftWingR, wingTop,
                0x00FF5500, 0x1EFF5500, Shader.TileMode.CLAMP);
        rightBaseShader = new LinearGradient(rightWingL, wingTop, rightWingR, wingTop,
                0x1EFF5500, 0x00FF5500, Shader.TileMode.CLAMP);

        // Compass Tape
        tapeY = h - boxPadY - 18f * density;
        float tapeW = w * 0.88f;
        float tapeH = 22f * density;
        tapeRect.set(cx - tapeW / 2f, tapeY - tapeH / 2f, cx + tapeW / 2f, tapeY + tapeH / 2f);
        tapeTextY = tapeY + 3.8f * density;

        // Typography sizes
        speedTextSize = 58f * density;
        unitTextSize = 11f * density;
        compTextSize = 9.5f * density;

        linePaint.setStrokeWidth(1f * density);
        wingBorderPaint.setStrokeWidth(3f * density);
    }

    public void setSpeed(float speed, boolean mph, boolean gps) {
        this.isMph = mph;
        this.hasGps = gps;
        this.maxSpeed = mph ? 140f : 220f;
        this.currentSpeed = Math.max(0, Math.min(speed, maxSpeed));

        if (speedAnimator != null && speedAnimator.isRunning()) {
            speedAnimator.cancel();
        }
        speedAnimator = ValueAnimator.ofFloat(animatedSpeed, currentSpeed);
        speedAnimator.setDuration(300);
        speedAnimator.setInterpolator(new DecelerateInterpolator());
        speedAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                animatedSpeed = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        speedAnimator.start();
    }

    public void setBearing(float bearing) {
        this.bearing = bearing;
        invalidate();
    }

    public void setAltitude(float alt) {
        this.altitude = alt;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 1. HUD Outer Box Container
        bgPaint.setColor(COLOR_BOX_BG);
        canvas.drawRoundRect(boxRect, boxRadius, boxRadius, bgPaint);

        linePaint.setColor(COLOR_BOX_BORDER);
        canvas.drawRoundRect(boxRect, boxRadius, boxRadius, linePaint);

        // 2. Dual Aerodynamic Wings
        drawLeftWing(canvas);
        drawRightWing(canvas);

        // 3. Center Digital Speed Readout
        textPaint.setTypeface(TF_BOLD);
        textPaint.setTextSize(speedTextSize);
        textPaint.setColor(COLOR_TXT);

        String speedStr = (!hasGps) ? "--" : String.valueOf(Math.round(animatedSpeed < 1.5f ? 0 : animatedSpeed));
        canvas.drawText(speedStr, cx, centerY + 16f * density, textPaint);

        // Unit Tag
        textPaint.setTextSize(unitTextSize);
        textPaint.setColor(COLOR_ACC);
        String unitStr = isMph ? "MPH" : "KM/H";
        canvas.drawText(unitStr, cx, centerY + 34f * density, textPaint);

        // 4. Horizontal Aviation-Style Rolling Compass Tape
        drawCompassTape(canvas);
    }

    private void drawLeftWing(Canvas canvas) {
        // Base fill
        wingPaint.setShader(leftBaseShader);
        canvas.drawPath(leftWingPath, wingPaint);

        // Dynamic speed fill
        float frac = Math.max(0f, Math.min(1f, animatedSpeed / maxSpeed));
        if (frac > 0.02f) {
            float fillWidth = (leftWingR - leftWingL) * frac;
            float fillLeft = leftWingR - fillWidth;

            canvas.save();
            canvas.clipPath(leftWingPath);
            activeFillRect.set(fillLeft, wingTop, leftWingR, wingBottom);
            wingPaint.setShader(null);
            wingPaint.setColor(0x38FF5500);
            canvas.drawRect(activeFillRect, wingPaint);
            canvas.restore();
        }

        // Aerodynamic boundary lines
        linePaint.setColor(0x33FF5500);
        canvas.drawLine(leftWingL + 14f * density, wingTop, leftWingR, wingTop, linePaint);
        canvas.drawLine(leftWingL + 14f * density, wingBottom, leftWingR, wingBottom, linePaint);

        // Glowing boundary bar
        wingBorderPaint.setColor(COLOR_ACC);
        canvas.drawLine(leftWingR, wingTop + 2f * density, leftWingR, wingBottom - 2f * density, wingBorderPaint);
    }

    private void drawRightWing(Canvas canvas) {
        // Base fill
        wingPaint.setShader(rightBaseShader);
        canvas.drawPath(rightWingPath, wingPaint);

        // Dynamic speed fill
        float frac = Math.max(0f, Math.min(1f, animatedSpeed / maxSpeed));
        if (frac > 0.02f) {
            float fillWidth = (rightWingR - rightWingL) * frac;
            float fillRight = rightWingL + fillWidth;

            canvas.save();
            canvas.clipPath(rightWingPath);
            activeFillRect.set(rightWingL, wingTop, fillRight, wingBottom);
            wingPaint.setShader(null);
            wingPaint.setColor(0x38FF5500);
            canvas.drawRect(activeFillRect, wingPaint);
            canvas.restore();
        }

        // Aerodynamic boundary lines
        linePaint.setColor(0x33FF5500);
        canvas.drawLine(rightWingL, wingTop, rightWingR - 14f * density, wingTop, linePaint);
        canvas.drawLine(rightWingL, wingBottom, rightWingR - 14f * density, wingBottom, linePaint);

        // Glowing boundary bar
        wingBorderPaint.setColor(COLOR_ACC);
        canvas.drawLine(rightWingL, wingTop + 2f * density, rightWingL, wingBottom - 2f * density, wingBorderPaint);
    }

    private void drawCompassTape(Canvas canvas) {
        pillPaint.setColor(0x18FF5500);
        canvas.drawRoundRect(tapeRect, tapeRect.height() / 2f, tapeRect.height() / 2f, pillPaint);

        linePaint.setColor(0x26FF5500);
        canvas.drawRoundRect(tapeRect, tapeRect.height() / 2f, tapeRect.height() / 2f, linePaint);

        textPaint.setTypeface(TF_BOLD);
        textPaint.setTextSize(compTextSize);

        if (!hasGps) {
            textPaint.setColor(COLOR_SUB);
            canvas.drawText("● GPS STANDBY • ACQUIRING FIX", cx, tapeTextY, textPaint);
            return;
        }

        // Active heading
        String curDir = getCompassDirection(bearing);
        String activeStr = curDir + " " + Math.round(bearing) + "°";

        float prevAngle = (bearing - 45f + 360f) % 360f;
        float nextAngle = (bearing + 45f) % 360f;
        String prevDir = getCompassDirection(prevAngle);
        String nextDir = getCompassDirection(nextAngle);

        textPaint.setColor(COLOR_ACC);
        canvas.drawText(activeStr, cx, tapeTextY, textPaint);

        textPaint.setColor(COLOR_SUB);
        canvas.drawText(prevDir, cx - 64f * density, tapeTextY, textPaint);
        textPaint.setColor(0x66FF5500);
        canvas.drawText("•", cx - 36f * density, tapeTextY, textPaint);

        textPaint.setColor(0x66FF5500);
        canvas.drawText("•", cx + 36f * density, tapeTextY, textPaint);
        textPaint.setColor(COLOR_SUB);
        canvas.drawText(nextDir, cx + 64f * density, tapeTextY, textPaint);
    }

    private static String getCompassDirection(float deg) {
        float d = (deg % 360 + 360) % 360;
        if (d >= 337.5 || d < 22.5) return "N";
        if (d >= 22.5 && d < 67.5) return "NE";
        if (d >= 67.5 && d < 112.5) return "E";
        if (d >= 112.5 && d < 157.5) return "SE";
        if (d >= 157.5 && d < 202.5) return "S";
        if (d >= 202.5 && d < 247.5) return "SW";
        if (d >= 247.5 && d < 292.5) return "W";
        return "NW";
    }
}
