package com.ghostflying.glyphhelper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.Display;
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

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int density = metrics.densityDpi;
            int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            mWidth = size.x;
            mHeight = size.y;

            mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 1);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);


            mMediaProjection.createVirtualDisplay("screen_cap", mWidth, mHeight, density, flags, mImageReader.getSurface(), null, null);
        }
    }

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
