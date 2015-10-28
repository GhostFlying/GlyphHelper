package com.ghostflying.glyphhelper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class ScreenCapService extends Service {
    private Thread mWorkingThread;
    private Handler mWorkingHandler;
    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private int mWidth;
    private int mHeight;
    private int mDensity;
    private ImageReader mImageReader;

    public ScreenCapService() {
    }

    @Override
    public void onCreate(){
        mProjectionManager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ScreenCapBinder();
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
