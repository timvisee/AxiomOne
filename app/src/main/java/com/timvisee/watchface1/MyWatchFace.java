/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.timvisee.watchface1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        boolean mAmbient;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Set the time zone
                setTimeZoneById(intent.getStringExtra("time-zone"));

                // Update the time
                updateTime();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * The text painter for the hour digits.
         */
        Paint mTextPaintHour;

        /**
         * The text painter for the faded hour digit.
         */
        Paint mTextPaintHourGhost;

        /**
         * The text painter for the minute digits.
         */
        Paint mTextPaintMinute;

        /**
         * The second glance painter.
         */
        Paint mGleamPaint;

        /**
         * The main small tick painter.
         */
        Paint mTickSmallPaint;

        /**
         * The faded small tick painter.
         */
        Paint mTickSmallFadedPaint;

        /**
         * The main large tick painter.
         */
        Paint mTickLargePaint;

        /**
         * The faded large tick painter.
         */
        Paint mTickLargeFadedPaint;

        /**
         * The calendar instance that is used as time for the watch face.
         */
        Calendar calendar;

        /**
         * Resources instance.
         */
        Resources resources;

        /**
         * The background color.
         */
        int backgroundColor;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        float hourDigitHeight;
        float hourDigitWidth;
        float hourDigitSpacing;
        float minuteDigitHeight;
        Path secondGleamPath;
        float gleamWidth;
        float gleamLength;
        float tickLengthSmall;
        float tickLengthLarge;

        Bitmap clockBitmap;
        Date clockBitmapLastUpdate;

        Bitmap ticksBitmap;
        Date ticksBitmapLastUpdate;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            // Set the resources instance
            resources = MyWatchFace.this.getResources();

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            // Get the background color
            backgroundColor = resources.getColor(R.color.background);

            // Create a typeface with the main font, set the font afterwards
            // TODO: Make a constant of this font name?
            Typeface font = Typeface.createFromAsset(getAssets(), "fonts/BebasNeue Bold.otf");
            mTextPaint = new Paint();
            mTextPaint.setTypeface(font);
            mTextPaint.setColor(resources.getColor(R.color.digital_text));
            mTextPaint.setAntiAlias(true);

            // Create the hour and minute text painters
            // TODO: Set the color and alpha of the font painters!
            mTextPaintHour = new Paint(mTextPaint);
            mTextPaintHour.setColor(resources.getColor(R.color.digit_hour_color));
            mTextPaintHour.setTextAlign(Paint.Align.RIGHT);
            mTextPaintHourGhost = new Paint(mTextPaintHour);
            mTextPaintHourGhost.setAlpha(255 / 10);
            mTextPaintMinute = new Paint(mTextPaint);
            mTextPaintMinute.setColor(resources.getColor(R.color.digit_minute_color));
            mTextPaintMinute.setTextAlign(Paint.Align.LEFT);

            // Create the second gleam painter
            mGleamPaint = new Paint();
            mGleamPaint.setColor(Color.WHITE);
            // TODO: Set the proper alpha here, use a resource constant!
            mGleamPaint.setAlpha(255 / 5);
            mGleamPaint.setStyle(Paint.Style.FILL);
            mGleamPaint.setAntiAlias(true);

            // Create the tick painters
            mTickLargePaint = new Paint();
            //noinspection deprecation
            mTickLargePaint.setColor(resources.getColor(R.color.tick_color_large));
            mTickLargePaint.setAlpha(resources.getInteger(R.integer.tick_alpha_large));
            mTickLargePaint.setAntiAlias(true);
            mTickLargePaint.setStrokeWidth(resources.getDimension(R.dimen.tick_width_large));
            mTickLargeFadedPaint = new Paint(mTickLargePaint);
            mTickLargeFadedPaint.setAlpha(resources.getInteger(R.integer.tick_alpha_faded_large));
            mTickSmallPaint = new Paint(mTickLargePaint);
            //noinspection deprecation
            mTickSmallPaint.setColor(resources.getColor(R.color.tick_color_small));
            mTickSmallPaint.setAlpha(resources.getInteger(R.integer.tick_alpha_small));
            mTickSmallPaint.setAntiAlias(true);
            mTickSmallPaint.setStrokeWidth(resources.getDimension(R.dimen.tick_width_small));
            mTickSmallFadedPaint = new Paint(mTickSmallPaint);
            mTickSmallFadedPaint.setAlpha(resources.getInteger(R.integer.tick_alpha_faded_small));

            // Set the calendar instance
            calendar = new GregorianCalendar(TimeZone.getDefault());

            // Initialize the second glance path
            secondGleamPath = new Path();

            // Determine the gleam width and height
            gleamWidth = (float) (1.0f / 60.0f * Math.PI * 2.0f);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                setTimeZone(null);

                // Update the time
                updateTime();
            } else {
                unregisterReceiver();
            }

            // Invalidate the face for smooth animations if it's visible and not in ambient mode
            if(isVisible() && !isInAmbientMode())
                invalidate();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);

            // Determine the size of the hour and minute digits
            float hourTextSize = resources.getDimension(isRound
                    ? R.dimen.hour_text_size_round : R.dimen.hour_text_size);
            float minuteTextSize = resources.getDimension(isRound
                    ? R.dimen.minute_text_size_round : R.dimen.minute_text_size);

            // Set the font size of the hour and minute digits painter
            mTextPaintHour.setTextSize(hourTextSize);
            mTextPaintHourGhost.setTextSize(hourTextSize);
            mTextPaintMinute.setTextSize(minuteTextSize);

            // Determine the width and height of the hour digits
            Rect hourDigitBounds = new Rect();
            Rect hourDigitBoundsDouble = new Rect();
            mTextPaintHour.getTextBounds("0", 0, 1, hourDigitBounds);
            mTextPaintHour.getTextBounds("00", 0, 2, hourDigitBoundsDouble);
            hourDigitHeight = hourDigitBounds.height();
            hourDigitWidth = hourDigitBounds.width();
            hourDigitSpacing = hourDigitBoundsDouble.width() - hourDigitWidth * 2;

            // Determine the height of the minute digits
            Rect minuteDigitBounds = new Rect();
            mTextPaintMinute.getTextBounds("0", 0, 1, minuteDigitBounds);
            minuteDigitHeight = minuteDigitBounds.height();

            // Determine the second gleam length
            gleamLength = resources.getDimension(R.dimen.second_gleam_length);

            // Determine the length of the ticks
            tickLengthSmall = resources.getDimension(R.dimen.tick_length_small);
            tickLengthLarge = resources.getDimension(R.dimen.tick_length_large);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);

                    // Set the low-bit modes for the digit painters
                    mTextPaintHour.setAntiAlias(!inAmbientMode);
                    mTextPaintHourGhost.setAntiAlias(!inAmbientMode);
                    mTextPaintMinute.setAntiAlias(!inAmbientMode);
                    mGleamPaint.setAntiAlias(!inAmbientMode);
                    mTickLargePaint.setAntiAlias(!inAmbientMode);
                    mTickLargeFadedPaint.setAntiAlias(!inAmbientMode);
                    mTickSmallPaint.setAntiAlias(!inAmbientMode);
                    mTickSmallFadedPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Invalidate the face for smooth animations if it's visible and not in ambient mode
            if(isVisible() && !isInAmbientMode())
                invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawColor(!isInAmbientMode() ? backgroundColor : Color.BLACK);

            // Update the time
            updateTime();

            // Get the offset for the hour and minute digits
            int digitsX = (bounds.width() / 2) + (int) resources.getDimension(R.dimen.digit_x_offset);
            int hourDigitsY = (int) ((bounds.height() / 2) - ((mTextPaintHour.descent() + mTextPaintHour.ascent()) / 2));
            int minuteDigitsY = (int) (hourDigitsY - hourDigitHeight + minuteDigitHeight);

            float radius = bounds.width() / 2.0f;
            float secondPrecise = calendar.get(Calendar.SECOND) + (float) (calendar.get(Calendar.MILLISECOND) % 1000) / 1000.0f;

            // Create the clock bitmap if it hasn't been initialized yet
            if(clockBitmap == null)
                clockBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);

            // Create the ticks bitmap if it hasn't been initialized yet
            if(ticksBitmap == null)
                ticksBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);

            // Draw the clock bitmap if it isn't up-to-date
            // TODO: Also force-update this when the screen goes to ambient mode!
            if(clockBitmapLastUpdate == null
                    || clockBitmapLastUpdate.getMinutes() != calendar.get(Calendar.MINUTE)
                    || clockBitmapLastUpdate.getHours() != calendar.get(Calendar.HOUR_OF_DAY)) {
                // Update the last bitmap update time
                clockBitmapLastUpdate = calendar.getTime();

                // Show a debug message
                System.out.println("Redrawing clock bitmap");

                // Create a new canvas to draw in
                Canvas clockCanvas = new Canvas(clockBitmap);

                // Clear the current bitmap (transparent)
                clockCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                // Draw the hour digits and draw a ghost digit if it's only one digit
                clockCanvas.drawText(String.valueOf(calendar.get(Calendar.HOUR_OF_DAY)), digitsX, hourDigitsY, mTextPaintHour);
                if(calendar.get(Calendar.HOUR_OF_DAY) < 10)
                    clockCanvas.drawText("0", digitsX - hourDigitWidth - hourDigitSpacing, hourDigitsY, mTextPaintHourGhost);

                // Draw the minute digits
                clockCanvas.drawText(String.format("%02d", calendar.get(Calendar.MINUTE)), digitsX, minuteDigitsY, mTextPaintMinute);
            }

            // Draw the clock bitmap if it isn't up-to-date
            if(isVisible() && !isInAmbientMode() && (ticksBitmapLastUpdate == null || ticksBitmapLastUpdate.getSeconds() != calendar.get(Calendar.SECOND))) {
                // Update the last bitmap update time
                ticksBitmapLastUpdate = calendar.getTime();

                // Show a debug message
                System.out.println("Redrawing ticks bitmap");

                // Create a new canvas to draw in
                Canvas ticksCanvas = new Canvas(ticksBitmap);

                // Clear the current bitmap (transparent)
                ticksCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                // Draw all ticks
                for(int i = 0; i < 60; i++) {
                    // Determine whether to draw a large or small tick
                    boolean largeTick = i % 5 == 0;

                    // Calculate the angle and the length of the current tick
                    float angle = (float) ((i - 15.0f) / 60.0f * Math.PI * 2.0f);
                    float tickLength = largeTick ? tickLengthLarge : tickLengthSmall;

                    // Select the correct tick paint
                    Paint tickPaint = largeTick ? mTickLargeFadedPaint : mTickSmallFadedPaint;
                    if((int) (secondPrecise + 0.5f) % 60 == i)
                        tickPaint = largeTick ? mTickLargePaint : mTickSmallPaint;

                    // Calculate the coordinates of the tick to draw
                    float[][] pointsTick = {
                            getCircleCoords(radius - tickLength, angle, radius, radius),
                            getCircleCoords(radius, angle, radius, radius),
                    };

                    // Draw the tick
                    ticksCanvas.drawLine(
                            pointsTick[0][0], pointsTick[0][1],
                            pointsTick[1][0], pointsTick[1][1],
                            tickPaint
                    );
                }
            }

            // Draw the second gleam
            if(isVisible() && !isInAmbientMode()) {
                // Calculate some variables for the second gleam
                float radiusInside = radius - gleamLength;
                float radiusOutside = radius + 2.0f;
                float secondAngle = (float) ((secondPrecise - 15.0f) / 60.0f * Math.PI * 2.0f);

                // Create the path of the second gleam
                float[][] points = {
                        getCircleCoords(radiusInside, secondAngle - gleamWidth, radius, radius),
                        getCircleCoords(radiusOutside, secondAngle - gleamWidth, radius, radius),
                        getCircleCoords(radiusOutside, secondAngle, radius, radius),
                        getCircleCoords(radiusInside, secondAngle, radius, radius),
                };

                // Reset the current path
                secondGleamPath.reset();

                // Draw the second glance path
                secondGleamPath.moveTo(points[0][0], points[0][1]);
                secondGleamPath.lineTo(points[1][0], points[1][1]);
                secondGleamPath.lineTo(points[2][0], points[2][1]);
                secondGleamPath.lineTo(points[3][0], points[3][1]);
                secondGleamPath.close();

                // Draw the second gleam
                canvas.drawPath(secondGleamPath, mGleamPaint);
            }

            // Draw and render the clock bitmap
            canvas.drawBitmap(clockBitmap, 0, 0, null);

            // Draw and render the clock bitmap
            if(isVisible() && !isInAmbientMode())
                canvas.drawBitmap(ticksBitmap, 0, 0, null);

            // Invalidate the face for smooth animations if it's visible and not in ambient mode
            if(isVisible() && !isInAmbientMode())
                invalidate();
        }

        /**
         * Calculate the coordinates in a circle for the given radius and angle.
         *
         * @param radius The radius.
         * @param angle The angle.
         * @param offsetX The X offset.
         * @param offsetY The Y offset.
         *
         * @return The coordinates.
         */
        public float[] getCircleCoords(float radius, float angle, float offsetX, float offsetY) {
            return new float[] {
                    (float) (radius * Math.cos(angle)) + offsetX,
                    (float) (radius * Math.sin(angle)) + offsetY
            };
        }

        /**
         * Update the current time stored in the calendar instance.
         * This method must be called to properly update the millis field in the calendar.
         */
        public void updateTime() {
            calendar.setTimeInMillis(System.currentTimeMillis());
        }

        /**
         * Set the timezone of the calendar instance.
         *
         * @param timeZone The time zone, or null to use the default time zone.
         */
        public void setTimeZone(TimeZone timeZone) {
            // Set the default timezone
            if(timeZone == null)
                calendar.setTimeZone(TimeZone.getDefault());

            else
                // Set the given timezone
                calendar.setTimeZone(timeZone);
        }

        /**
         * Set the timezone of the calendar instance by it's ID.
         *
         * @param timeZoneId The time zone ID, or null to use the default time zone.
         */
        public void setTimeZoneById(String timeZoneId) {
            // If the timezone ID is null, set it to the default
            if(timeZoneId == null) {
                setTimeZone(null);
                return;
            }

            // FIXME: Determine the time zone, and set it afterwards!
        }
    }
}
