package com.ghostflying.glyphhelper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;

import java.nio.ByteBuffer;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {
    private static final int MEDIA_PROJECTION_REQUEST_CODE = 100;

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private int mWidth;
    private int mHeight;
    private ImageReader mImageReader;

    @Bind(R.id.fab) FloatingActionButton fab;
    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.screen_preview) ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startProjection();
            }
        });

        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    private void startProjection(){
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), MEDIA_PROJECTION_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE){
            if (resultCode != RESULT_OK){
                Snackbar.make(fab, R.string.projection_permission_failed, Snackbar.LENGTH_LONG).show();
            }

            mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
            Intent serviceIntent = new Intent(this, ScreenCapService.class);
            startService(serviceIntent);
            bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);


            //mMediaProjection.createVirtualDisplay("screen_cap", mWidth, mHeight, density, flags, mImageReader.getSurface(), null, null);
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ScreenCapService.ScreenCapBinder mBinder = (ScreenCapService.ScreenCapBinder)service;
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            mBinder.setMediaProjection(mMediaProjection, metrics.densityDpi, 720, 1280);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image mImage = null;
            Bitmap mBitmap;

            try{
                mImage = reader.acquireLatestImage();
                if (mImage != null){
                    Image.Plane[] planes = mImage.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mWidth;

                    // create bitmap
                    mBitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                    mBitmap.copyPixelsFromBuffer(buffer);

                    mImageView.setImageBitmap(mBitmap);
                }
            }
            finally {
                if (mImage != null)
                    mImage.close();
            }
        }
    };
}
