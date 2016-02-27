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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
        Paint mTextPaintHourFaded;

        /**
         * The text painter for the minute digits.
         */
        Paint mTextPaintMinute;

        /**
         * The second glance painter.
         */
        Paint mGlancePaint;

        Calendar calendar;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

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
            mTextPaintHour.setTextAlign(Paint.Align.RIGHT);
            mTextPaintHourFaded = new Paint(mTextPaintHour);
            mTextPaintHourFaded.setAlpha(255 / 10);
            mTextPaintMinute = new Paint(mTextPaint);
            mTextPaintMinute.setTextAlign(Paint.Align.LEFT);

            // Create the second glance painter
            mGlancePaint = new Paint();
            mGlancePaint.setColor(Color.WHITE);
            // TODO: Set the proper alpha here, use a resource constant!
            mGlancePaint.setAlpha(255 / 5);
            mGlancePaint.setStyle(Paint.Style.FILL);
            mGlancePaint.setAntiAlias(true);

            // Set the calendar instance
            calendar = new GregorianCalendar(TimeZone.getDefault());
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
            mTextPaintHourFaded.setTextSize(hourTextSize);
            mTextPaintMinute.setTextSize(minuteTextSize);
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
                    mTextPaintHourFaded.setAntiAlias(!inAmbientMode);
                    mTextPaintMinute.setAntiAlias(!inAmbientMode);
                    mGlancePaint.setAntiAlias(!inAmbientMode);
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
            if(isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Update the time
            updateTime();

            // Get the resources instance
            Resources resources = MyWatchFace.this.getResources();

            // Get the offset for the hour and minute digits
            float digitOffsetX = resources.getDimension(R.dimen.digit_x_offset);

            int digitsX = (canvas.getWidth() / 2) + (int) digitOffsetX;
            int hourDigitsY = (int) ((canvas.getHeight() / 2) - ((mTextPaintHour.descent() + mTextPaintHour.ascent()) / 2));

            // Determine the height of the hour digits
            Rect hourDigitBounds = new Rect();
            Rect hourDigitBoundsDouble = new Rect();
            mTextPaintHour.getTextBounds("0", 0, 1, hourDigitBounds);
            mTextPaintHour.getTextBounds("00", 0, 2, hourDigitBoundsDouble);
            float hourDigitHeight = hourDigitBounds.height();
            float hourDigitWidth = hourDigitBounds.width();
            float hourDigitSpacing = hourDigitBoundsDouble.width() - hourDigitWidth * 2;

            // Determine the height of the minute digits
            Rect minuteDigitBounds = new Rect();
            mTextPaintMinute.getTextBounds("0", 0, 1, minuteDigitBounds);
            float minuteDigitHeight = minuteDigitBounds.height();

            int minuteDigitsY = (int) (hourDigitsY - hourDigitHeight + minuteDigitHeight);

            // Draw the second gleam
            if(!isInAmbientMode()) {
                // Calculate some variables for the second gleam
                float centerX = canvas.getWidth() / 2.0f;
                float centerY = canvas.getHeight() / 2.0f;
                float radius = canvas.getWidth() / 2.0f;
                float radiusShort = radius - 35.0f;
                float radiusLong = radius + 5.0f;
                float secondVal = calendar.get(Calendar.SECOND) + (float) (calendar.get(Calendar.MILLISECOND) % 1000) / 1000.0f;
                float angle = (float) ((secondVal + 15.0f) / 60.0f * Math.PI * 2.0f);
                float halfWidth = (float) (1.0f / 60.0f * Math.PI);

                // Create the path of the second gleam
                Path p = new Path();
                p.reset();
                p.moveTo((float) (centerX + radiusShort * Math.cos(angle - halfWidth)), (float) (centerY + radiusShort * Math.sin(angle - halfWidth)));
                p.lineTo((float) (centerX + radiusLong * Math.cos(angle - halfWidth)), (float) (centerY + radiusLong * Math.sin(angle - halfWidth)));
                p.lineTo((float) (centerX + radiusLong * Math.cos(angle + halfWidth)), (float) (centerY + radiusLong * Math.sin(angle + halfWidth)));
                p.lineTo((float) (centerX + radiusShort * Math.cos(angle + halfWidth)), (float) (centerY + radiusShort * Math.sin(angle + halfWidth)));
                p.close();

                // Draw the second gleam
                canvas.drawPath(p, mGlancePaint);
            }

            // Draw the hour digits
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            canvas.drawText(String.valueOf(hour), digitsX, hourDigitsY, mTextPaintHour);
            if(hour < 10)
                canvas.drawText("0", digitsX - hourDigitWidth - hourDigitSpacing, hourDigitsY, mTextPaintHourFaded);

            // Draw the minute
            canvas.drawText(String.format("%02d", calendar.get(Calendar.MINUTE)), digitsX, minuteDigitsY, mTextPaintMinute);

            // Invalidate the face for smooth animations if it's visible and not in ambient mode
            if(isVisible() && !isInAmbientMode())
                invalidate();
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
