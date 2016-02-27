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
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

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

//        @Override
//        public void handleMessage(Message msg) {
//            MyWatchFace.Engine engine = mWeakReference.get();
//            if (engine != null) {
//                switch (msg.what) {
//                    case MSG_UPDATE_TIME:
//                        engine.handleUpdateTimeMessage();
//                        break;
//                }
//            }
//        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * The text painter for the hour digits.
         */
        Paint mTextPaintHour;

        /**
         * The text painter for the minute digits.
         */
        Paint mTextPaintMinute;

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
            // TODO: Set the color of both digit painters!
            mTextPaintHour = new Paint(mTextPaint);
            mTextPaintHour.setTextAlign(Paint.Align.RIGHT);
            mTextPaintMinute = new Paint(mTextPaint);
            mTextPaintMinute.setTextAlign(Paint.Align.LEFT);

            mTime = new Time();
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
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
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

            // Set the font size of the hour and minute digits painter
            mTextPaintHour.setTextSize(
                    resources.getDimension(isRound
                            ? R.dimen.hour_text_size_round : R.dimen.hour_text_size)
            );
            mTextPaintMinute.setTextSize(
                    resources.getDimension(isRound
                            ? R.dimen.minute_text_size_round : R.dimen.minute_text_size)
            );
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
                    mTextPaintMinute.setAntiAlias(!inAmbientMode);
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
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Set the time
            // TODO: Deprecate this for the calendar!
            mTime.setToNow();

            // Create a calendar instance
            // TODO: Create instance on create, update it once each loop!
            Calendar time = Calendar.getInstance();

            // Get the resources instance
            Resources resources = MyWatchFace.this.getResources();

            // Get the offset for the hour and minute digits
            float digitOffsetX = resources.getDimension(R.dimen.digit_x_offset);

            int digitsX = (canvas.getWidth() / 2) + (int) digitOffsetX;
            int hourDigitsY = (int) ((canvas.getHeight() / 2) - ((mTextPaintHour.descent() + mTextPaintHour.ascent()) / 2));

            // Determine the height of the hour digits
            Rect hourDigitBounds = new Rect();
            mTextPaintHour.getTextBounds("0", 0, 1, hourDigitBounds);
            float hourDigitHeight = hourDigitBounds.height();
            float hourDigitWidth = hourDigitBounds.width();

            // Determine the height of the minute digits
            Rect minuteDigitBounds = new Rect();
            mTextPaintMinute.getTextBounds("0", 0, 1, minuteDigitBounds);
            float minuteDigitHeight = minuteDigitBounds.height();

            int minuteDigitsY = (int) (hourDigitsY - hourDigitHeight + minuteDigitHeight);

            // Draw the second gleam
            if(!isInAmbientMode()) {
                // Create the second gleam painter
                Paint pPaint = new Paint();
                pPaint.setColor(Color.WHITE);
                // TODO: Set the proper alpha here, use a resource constant!
                pPaint.setAlpha(255 / 2);
                pPaint.setStyle(Paint.Style.FILL);
                pPaint.setAntiAlias(true);

                // Calculate some variables for the second gleam
                float centerX = canvas.getWidth() / 2.0f;
                float centerY = canvas.getHeight() / 2.0f;
                float radius = canvas.getWidth() / 2.0f;
                float radiusShort = radius - 35.0f;
                float radiusLong = radius + 5.0f;
                float secondVal = mTime.second + (float) (time.get(Calendar.MILLISECOND) % 1000) / 1000.0f;
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
                canvas.drawPath(p, pPaint);
            }

            // Draw the hour
            canvas.drawText(String.format("%02d", mTime.hour), digitsX, hourDigitsY, mTextPaintHour);

            // Draw the minute
            canvas.drawText(String.format("%02d", mTime.minute), digitsX, minuteDigitsY, mTextPaintMinute);

            // Invalidate the face for smooth animations if it's visible and not in ambient mode
            if(isVisible() && !isInAmbientMode())
                invalidate();
        }
    }
}
