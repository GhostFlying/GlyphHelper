package com.ghostflying.glyphhelper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class ScreenCapService extends Service {
    public static final int DEFAULT_ICON_TOP_OFFSET = 10;
    public static final int DEFAULT_ICON_RIGHT_OFFSET = 60;
    public static final int DEFAULT_SCREEN_SHOT_TOP_OFFSET = 10;
    public static final int DEFAULT_SCREEN_SHOT_RIGHT_OFFSET = 110;
    public static final int DEFAULT_SCREEN_SHOT_SIZE = 40;
    private static final int SCREEN_SHOT_VIBRATE_TIME_IN_MILLISECONDS = 300;

    private int screenShotTopOffset;
    private int screenShotRightOffset;

    private Queue<ImageView> existScreenShots;

    private Thread mWorkingThread;
    private Handler mWorkingHandler;
    private MediaProjection mMediaProjection;
    private int mWidth;
    private int mHeight;
    private int mDensity;
    private ImageReader mImageReader;
    private VirtualDisplay mVirtualDisplay;
    private WindowManager mWindowManager;
    private ImageView mButton;
    private WindowManager.LayoutParams mButtonParams;
    private DisplayMetrics mDisplayMetrics;
    private LinearLayout mScreenShotsContainer;
    private WindowManager.LayoutParams mScreenShotsContainerParams;
    private Vibrator mVibrator;

    public ScreenCapService() {
    }

    @Override
    public void onCreate(){
        mWindowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        mDisplayMetrics = getResources().getDisplayMetrics();
        screenShotRightOffset = dpToPx(DEFAULT_SCREEN_SHOT_RIGHT_OFFSET);
        screenShotTopOffset = dpToPx(DEFAULT_SCREEN_SHOT_TOP_OFFSET);
        existScreenShots = new ArrayBlockingQueue<>(5);
        mVibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        addButton();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ScreenCapBinder();
    }

    @Override
    public void onDestroy(){
        onTearDownMediaProjection();
    }

    private void addButton(){
        if (mButton == null){
            mButton = new ImageView(this);
            mButton.setImageResource(R.drawable.ic_launcher);
            mButton.setOnClickListener(mButtonClickListener);
            mButton.setOnLongClickListener(mButtonLongClickListener);
            if (mButtonParams == null)
                mButtonParams = getLayoutParams(dpToPx(DEFAULT_ICON_TOP_OFFSET), dpToPx(DEFAULT_ICON_RIGHT_OFFSET));
            mWindowManager.addView(mButton, mButtonParams);
        }
    }

    private void addScreenShots() {
        if (mScreenShotsContainer == null){
            mScreenShotsContainer = new LinearLayout(this);
            mScreenShotsContainer.setOrientation(LinearLayout.HORIZONTAL);
            if (mScreenShotsContainerParams == null)
                mScreenShotsContainerParams = getLayoutParams(screenShotTopOffset, screenShotRightOffset);
            mWindowManager.addView(mScreenShotsContainer, mScreenShotsContainerParams);
        }
    }

    private void showScreenShot(Bitmap screenShot){
        ImageView container;

        if (mScreenShotsContainer == null)
            addScreenShots();

        if (existScreenShots.size() == 5){
            container = existScreenShots.remove();
            mScreenShotsContainer.removeView(container);
        }
        else {
            container = new ImageView(getApplicationContext());
        }
        container.setImageBitmap(screenShot);
        existScreenShots.add(container);
        mScreenShotsContainer.addView(container);
    }

    private void removeScreenShots() {
        if (mScreenShotsContainer != null){
            existScreenShots.clear();
            mWindowManager.removeView(mScreenShotsContainer);
            mScreenShotsContainer = null;
        }
    }

    private View.OnClickListener mButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mVibrator.vibrate(SCREEN_SHOT_VIBRATE_TIME_IN_MILLISECONDS);
            startScreenCap();
        }
    };

    private View.OnLongClickListener mButtonLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (existScreenShots.size() > 0){
                removeScreenShots();
                return true;
            }
            return false;
        }
    };

    private void startScreenCap(){
        Log.d("debug", "startSc");
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "screen_cap",
                mWidth,
                mHeight,
                mDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(),
                null,
                mWorkingHandler
        );
    }

    private void stopScreenCap(){
        if (mVirtualDisplay != null){
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    private Bitmap cropAndResize(Bitmap origin){
        int width = origin.getWidth();
        int height = origin.getHeight();
        int startY = height - getResources().getDimensionPixelSize(R.dimen.screenshot_margin_bottom) - width;
        Bitmap crop = Bitmap.createBitmap(origin, 0, startY, width, width);
        Bitmap resize = Bitmap.createScaledBitmap(crop, dpToPx(DEFAULT_SCREEN_SHOT_SIZE), dpToPx(DEFAULT_SCREEN_SHOT_SIZE), false);
        origin.recycle();
        crop.recycle();
        return resize;
    }

    private int dpToPx(int dp){
        return (int)TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                mDisplayMetrics
        );
    }

    private WindowManager.LayoutParams getLayoutParams(int topOffset, int rightOffset) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = rightOffset;
        params.y = topOffset;
        return params;
    }

    private void onSetMediaProjection(MediaProjection mediaProjection, int density, int width, int height){
        mMediaProjection = mediaProjection;
        mWidth = width;
        mHeight = height;
        mDensity = density;
        mWorkingThread = new Thread(){
            @Override
            public void run(){
                Looper.prepare();
                mWorkingHandler = new Handler();
                Looper.loop();
            }
        };
        setupImageReader();
    }

    private void onTearDownMediaProjection(){
        mWorkingHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mMediaProjection != null) {
                    mMediaProjection.stop();
                }
            }
        });

        if (mWorkingThread != null){
            mWorkingThread.interrupt();
            mWorkingThread = null;
        }
    }

    private void setupImageReader(){
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 1);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mWorkingHandler);
    }

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image mImage = null;
            Bitmap mBitmap;

            try{
                Log.d("debug", "onImageAva");
                mImage = reader.acquireLatestImage();
                stopScreenCap();
                if (mImage != null){
                    Image.Plane[] planes = mImage.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mWidth;

                    // create bitmap
                    mBitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                    mBitmap.copyPixelsFromBuffer(buffer);

                    showScreenShot(cropAndResize(mBitmap));
                }
            }
            finally {
                if (mImage != null)
                    mImage.close();
            }
        }
    };

    public class ScreenCapBinder extends Binder{
        public void setMediaProjection(MediaProjection mediaProjection, int density, int width, int height){
            onSetMediaProjection(mediaProjection, density, width, height);
        }

        public void tearDownMediaProjection(){
            onTearDownMediaProjection();
        }
    }
}
