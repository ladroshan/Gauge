package de.nitri.gauge;

/**
 * Created by helfrich on 07/01/2017.
 */

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Html;
import android.text.Spanned;
import android.util.AttributeSet;
import android.view.View;

public class Gauge extends View {

    private Paint needlePaint;
    private Path needlePath;
    private Paint needleScrewPaint;

    private float canvasCenterX;
    private float canvasCenterY;
    private float canvasWidth;
    private float canvasHeight;
    private float needleTailLength;
    private float needleWidth;
    private float needleLength;
    private RectF rimRect;
    private Paint rimPaint;
    private Paint rimCirclePaint;
    private RectF faceRect;
    private Paint facePaint;
    private Paint rimShadowPaint;
    private Paint scalePaint;
    private RectF scaleRect;

    private int totalNicks = 120; // on a full circle
    private float degreesPerNick = totalNicks / 360;
    private float valuePerNick = 10;
    private float minValue = 0;
    private float maxValue = 1000;
    private boolean intScale = true;

    private float requestedLabelTextSize = 0;

    private float initialValue = 0;
    private float value = 0;
    private float needleValue = 0;

    private float needleStep;

    private float centerValue;
    private float labelRadius;

    private int majorNickInterval = 10;

    private int deltaTimeInterval = 5;
    private float needleStepFactor = 3f;

    private static final String TAG = Gauge.class.getSimpleName();
    private Paint labelPaint;
    private long lastMoveTime;
    private boolean needleShadow = true;
    private int faceColor;
    private int scaleColor;
    private int needleColor;
    private Paint textPaint;

    private float requestedTextSize = 0;
    private String upperText = "";
    private String lowerText = "";

    public Gauge(Context context) {
        super(context);
        init();
    }

    public Gauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        applyAttrs(context, attrs);
        init();
    }

    public Gauge(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyAttrs(context, attrs);
        init();
    }

    private void applyAttrs(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Gauge, 0, 0);
        totalNicks = a.getInt(R.styleable.Gauge_totalNicks, totalNicks);
        degreesPerNick = 360.0f / totalNicks;
        valuePerNick = a.getFloat(R.styleable.Gauge_valuePerNick, valuePerNick);
        majorNickInterval = a.getInt(R.styleable.Gauge_majorNickInterval, 10);
        minValue = a.getFloat(R.styleable.Gauge_minValue, minValue);
        maxValue = a.getFloat(R.styleable.Gauge_maxValue, maxValue);
        intScale = a.getBoolean(R.styleable.Gauge_intScale, intScale);
        initialValue = a.getFloat(R.styleable.Gauge_initialValue, initialValue);
        requestedLabelTextSize = a.getFloat(R.styleable.Gauge_labelTextSize, requestedLabelTextSize);
        faceColor = a.getColor(R.styleable.Gauge_faceColor, Color.argb(0xff, 0xff, 0xff, 0xff));
        scaleColor = a.getColor(R.styleable.Gauge_scaleColor, 0x9f004d0f);
        needleColor = a.getColor(R.styleable.Gauge_needleColor, Color.RED);
        needleShadow = a.getBoolean(R.styleable.Gauge_needleShadow, needleShadow);
        requestedTextSize = a.getFloat(R.styleable.Gauge_textSize, requestedTextSize);
        upperText = a.getString(R.styleable.Gauge_upperText) == null ? upperText : fromHtml(a.getString(R.styleable.Gauge_upperText)).toString();
        lowerText = a.getString(R.styleable.Gauge_lowerText) == null ? lowerText : fromHtml(a.getString(R.styleable.Gauge_lowerText)).toString();
        a.recycle();
    }

    private void init() {

        setSaveEnabled(true);

        needleStep = needleStepFactor * valuePerDegree();

        centerValue = (minValue + maxValue) / 2;

        // Rim and shadow are based on the Vintage Thermometer:
        // http://mindtherobot.com/blog/272/android-custom-ui-making-a-vintage-thermometer/

        rimPaint = new Paint();
        rimPaint.setFlags(Paint.ANTI_ALIAS_FLAG);

        rimCirclePaint = new Paint();
        rimCirclePaint.setAntiAlias(true);
        rimCirclePaint.setStyle(Paint.Style.STROKE);
        rimCirclePaint.setColor(Color.argb(0x4f, 0x33, 0x36, 0x33));
        rimCirclePaint.setStrokeWidth(0.005f);

        facePaint = new Paint();
        facePaint.setAntiAlias(true);
        facePaint.setStyle(Paint.Style.FILL);
        facePaint.setColor(faceColor);

        rimShadowPaint = new Paint();
        rimShadowPaint.setStyle(Paint.Style.FILL);

        scalePaint = new Paint();
        scalePaint.setStyle(Paint.Style.STROKE);

        scalePaint.setAntiAlias(true);
        scalePaint.setColor(scaleColor);

        labelPaint = new Paint();
        labelPaint.setColor(scaleColor);
        labelPaint.setTypeface(Typeface.SANS_SERIF);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        textPaint = new Paint();
        textPaint.setColor(scaleColor);
        textPaint.setTypeface(Typeface.SANS_SERIF);
        textPaint.setTextAlign(Paint.Align.CENTER);

        needlePaint = new Paint();
        needlePaint.setColor(needleColor);
        needlePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        needlePaint.setAntiAlias(true);

        needlePath = new Path();

        needleScrewPaint = new Paint();
        needleScrewPaint.setColor(Color.BLACK);
        needleScrewPaint.setAntiAlias(true);

        needleValue = value = initialValue;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // canvas.drawColor(Color.LTGRAY);

        drawRim(canvas);

        drawFace(canvas);

        drawScale(canvas);
        drawLabels(canvas);

        drawTexts(canvas);

        canvas.rotate(scaleToCanvasDegrees(valueToDegrees(needleValue)), canvasCenterX, canvasCenterY);

        canvas.drawPath(needlePath, needlePaint);

        canvas.drawCircle(canvasCenterX, canvasCenterY, canvasWidth / 61f, needleScrewPaint);

        invalidate();

        if (needsToMove()) {
            moveNeedle();
        }
    }

    private void moveNeedle() {
        long currentTime = System.currentTimeMillis();
        long deltaTime = currentTime - lastMoveTime;

        if (deltaTime >= deltaTimeInterval) {
            if (Math.abs(value - needleValue) <= needleStep) {
                needleValue = value;
            } else {
                if (value > needleValue) {
                    needleValue += 2 * valuePerDegree();
                } else {
                    needleValue -= 2 * valuePerDegree();
                }
            }
            lastMoveTime = System.currentTimeMillis();
        }

    }

    private void drawRim(Canvas canvas) {
        canvas.drawOval(rimRect, rimPaint);
        canvas.drawOval(rimRect, rimCirclePaint);
    }

    private void drawFace(Canvas canvas) {
        canvas.drawOval(faceRect, facePaint);
        canvas.drawOval(faceRect, rimCirclePaint);
        canvas.drawOval(faceRect, rimShadowPaint);
    }

    private void drawScale(Canvas canvas) {

        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        for (int i = 0; i < totalNicks; ++i) {
            float y1 = scaleRect.top;
            float y2 = y1 + (0.020f * canvasHeight);
            float y3 = y1 + (0.060f * canvasHeight);
            float y4 = y1 + (0.030f * canvasHeight);

            float value = nickToValue(i);

            if (value >= minValue && value <= maxValue) {
                canvas.drawLine(0.5f * canvasWidth, y1, 0.5f * canvasWidth, y2, scalePaint);

                if (i % majorNickInterval == 0) {
                    canvas.drawLine(0.5f * canvasWidth, y1, 0.5f * canvasWidth, y3, scalePaint);
                }

                if (i % (majorNickInterval / 2) == 0) {
                    canvas.drawLine(0.5f * canvasWidth, y1, 0.5f * canvasWidth, y4, scalePaint);
                }
            }

            canvas.rotate(degreesPerNick, 0.5f * canvasWidth, 0.5f * canvasHeight);
        }
        canvas.restore();
    }

    private void drawLabels(Canvas canvas) {
        for (int i = 0; i < totalNicks; i += majorNickInterval) {
            float value = nickToValue(i);
            if (value >= minValue && value <= maxValue) {
                float scaleAngle = i * degreesPerNick;
                float scaleAngleRads = (float) Math.toRadians(scaleAngle);
                //Log.d(TAG, "i = " + i + ", angle = " + scaleAngle + ", value = " + value);
                float deltaX = labelRadius * (float) Math.sin(scaleAngleRads);
                float deltaY = labelRadius * (float) Math.cos(scaleAngleRads);
                String valueLabel;
                if (intScale) {
                    valueLabel = String.valueOf((int) value);
                } else {
                    valueLabel = String.valueOf(value);
                }
                drawTextCentered(valueLabel, canvasCenterX + deltaX, canvasCenterY - deltaY, labelPaint, canvas);
            }
        }
    }

    private void drawTexts(Canvas canvas) {
        drawTextCentered(upperText, canvasCenterX, canvasCenterY - (canvasHeight / 6.5f), textPaint, canvas);
        drawTextCentered(lowerText, canvasCenterX, canvasCenterY + (canvasHeight / 6.5f), textPaint, canvas);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        canvasWidth = (float) w;
        canvasHeight = (float) h;
        canvasCenterX = w / 2f;
        canvasCenterY = h / 2f;
        needleTailLength = canvasWidth / 12f;
        needleWidth = canvasWidth / 98f;
        needleLength = (canvasWidth / 2f) * 0.8f;

        needlePaint.setStrokeWidth(canvasWidth / 197f);

        if (needleShadow)
            needlePaint.setShadowLayer(canvasWidth / 123f, canvasWidth / 10000f, canvasWidth / 10000f, Color.GRAY);

        setNeedle();

        rimRect = new RectF(canvasWidth * .05f, canvasHeight * .05f, canvasWidth * 0.95f, canvasHeight * 0.95f);
        rimPaint.setShader(new LinearGradient(canvasWidth * 0.40f, canvasHeight * 0.0f, canvasWidth * 0.60f, canvasHeight * 1.0f,
                Color.rgb(0xf0, 0xf5, 0xf0),
                Color.rgb(0x30, 0x31, 0x30),
                Shader.TileMode.CLAMP));

        float rimSize = 0.02f * canvasWidth;
        faceRect = new RectF();
        faceRect.set(rimRect.left + rimSize, rimRect.top + rimSize,
                rimRect.right - rimSize, rimRect.bottom - rimSize);

        rimShadowPaint.setShader(new RadialGradient(0.5f * canvasWidth, 0.5f * canvasHeight, faceRect.width() / 2.0f,
                new int[]{0x00000000, 0x00000500, 0x50000500},
                new float[]{0.96f, 0.96f, 0.99f},
                Shader.TileMode.MIRROR));

        scalePaint.setStrokeWidth(0.005f * canvasWidth);
        scalePaint.setTextSize(0.045f * canvasWidth);
        scalePaint.setTextScaleX(0.8f * canvasWidth);

        float scalePosition = 0.015f * canvasWidth;
        scaleRect = new RectF();
        scaleRect.set(faceRect.left + scalePosition, faceRect.top + scalePosition,
                faceRect.right - scalePosition, faceRect.bottom - scalePosition);

        labelRadius = (canvasCenterX - scaleRect.left) * 0.70f;

        if (requestedLabelTextSize > 0) {
            labelPaint.setTextSize(requestedLabelTextSize);
        } else {
            labelPaint.setTextSize(w / 16f);
        }

        if (requestedTextSize > 0) {
            textPaint.setTextSize(requestedTextSize);
        } else {
            textPaint.setTextSize(w / 14f);
        }

        // Log.d(TAG, "width = " + w);

        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void setNeedle() {
        needlePath.reset();
        needlePath.moveTo(canvasCenterX - needleTailLength, canvasCenterY);
        needlePath.lineTo(canvasCenterX, canvasCenterY - (needleWidth / 2));
        needlePath.lineTo(canvasCenterX + needleLength, canvasCenterY);
        needlePath.lineTo(canvasCenterX, canvasCenterY + (needleWidth / 2));
        needlePath.lineTo(canvasCenterX - needleTailLength, canvasCenterY);
        needlePath.addCircle(canvasCenterX, canvasCenterY, canvasWidth / 49f, Path.Direction.CW);
        needlePath.close();

        needleScrewPaint.setShader(new RadialGradient(canvasCenterX, canvasCenterY, needleWidth / 2,
                Color.DKGRAY, Color.BLACK, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int size;
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        int widthWithoutPadding = width - getPaddingLeft() - getPaddingRight();
        int heigthWithoutPadding = height - getPaddingTop() - getPaddingBottom();

        if (widthWithoutPadding > heigthWithoutPadding) {
            size = heigthWithoutPadding;
        } else {
            size = widthWithoutPadding;
        }

        setMeasuredDimension(size + getPaddingLeft() + getPaddingRight(), size + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("superState", super.onSaveInstanceState());
        bundle.putFloat("value", value);
        bundle.putFloat("needleValue", needleValue);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            value = bundle.getFloat("value");
            needleValue = bundle.getFloat("needleValue");
            super.onRestoreInstanceState(bundle.getParcelable("superState"));
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    private float nickToValue(int nick) {
        float rawValue = ((nick < totalNicks / 2) ? nick : (nick - totalNicks)) * valuePerNick;
        return rawValue + centerValue;
    }

    private float valueToDegrees(float value) {
        // these are scale degrees, 0 is on top
        return ((value - centerValue) / valuePerNick) * degreesPerNick;
    }

    private float valuePerDegree() {
        return valuePerNick / degreesPerNick;
    }

    private float scaleToCanvasDegrees(float degrees) {
        return degrees - 90;
    }

    public void setValue(float value) {
        needleValue = this.value = value;
    }

    public void moveToValue(float value) {
        this.value = value;
    }

    private boolean needsToMove() {
        return Math.abs(needleValue - value) > 0;
    }

    private void drawTextCentered(String text, float x, float y, Paint paint, Canvas canvas) {
        //float xPos = x - (paint.measureText(text)/2f);
        float yPos = (y - ((paint.descent() + paint.ascent()) / 2f));
        canvas.drawText(text, x, yPos, paint);
    }

    public void setUpperText(String text) {
        upperText = text;
        invalidate();
    }

    public void setLowerText(String text) {
        lowerText = text;
        invalidate();
    }

    public void setRequestedTextSize(float size) {
        requestedTextSize = size;
    }

    public void setDeltaTimeInterval(int interval) {
        deltaTimeInterval = interval;
    }

    public void setNeedleStepFactor(float factor) {
        needleStepFactor = factor;
    }


    @SuppressWarnings("deprecation")
    public static Spanned fromHtml(String html){
        Spanned result;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            result = Html.fromHtml(html,Html.FROM_HTML_MODE_LEGACY);
        } else {
            result = Html.fromHtml(html);
        }
        return result;
    }

}