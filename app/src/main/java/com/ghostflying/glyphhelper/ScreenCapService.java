package com.ghostflying.glyphhelper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.IBinder;

import java.util.logging.Handler;

public class ScreenCapService extends Service {
    private Thread mWorkingThread;
    private Handler mWorkingHandler;
    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private int mWidth;
    private int mHeight;
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

    private void setMediaProjection(MediaProjection mediaProjection){
        mMediaProjection = mediaProjection;
    }

    public class ScreenCapBinder extends Binder{

    }
}
