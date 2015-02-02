package com.ghostflying.glyphhelper;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class MonitorService extends AccessibilityService {
    public static final String SETTINGS_NAME = "settings";
    public static final String SETTING_ICON_TOP_OFFSET_NAME = "iconTopOffset";
    public static final String SETTING_ICON_RIGHT_OFFSET_NAME = "iconRightOffset";
    public static final String SETTING_SCREEN_SHOT_TOP_OFFSET_NAME = "screenShotTopOffset";
    public static final String SETTING_SCREEN_SHOT_RIGHT_OFFSET_NAME = "screenShotRightOffset";
    public static final String SETTING_SCREEN_SHOT_SIZE_NAME = "screenShotSize";
    public static final String SETTING_SCREEN_SHOT_INTERVAL = "screenShotInterval";
    public static final String INGRESS_PACKAGE_NAME = "com.nianticproject.ingress";

    public static final int DEFAULT_ICON_TOP_OFFSET = 10;
    public static final int DEFAULT_ICON_RIGHT_OFFSET = 60;
    public static final int DEFAULT_SCREEN_SHOT_TOP_OFFSET = 10;
    public static final int DEFAULT_SCREEN_SHOT_RIGHT_OFFSET = 110;
    public static final int DEFAULT_SCREEN_SHOT_SIZE = 50;
    public static final int DEFAULT_SCREEN_SHOT_INTERVAL = 1200;

    private static final String SETTING_IS_SHOW = "isShow";
    private static final String SETTING_AUTO_SCREEN_SHOT = "autoScreenShot";
    private static final boolean DEFAULT_IS_SHOW = true;
    private static final boolean DEFAULT_AUTO_SCREEN_SHOT = false;

    private static final int GLYPH_TIMEOUT_IN_MILLISECONDS = 30000;
    private static final int SCREEN_SHOT_VIBRATE_TIME_IN_MILLISECONDS = 300;
    private static final String SYSTEM_UI_PACKAGE_NAME = "com.android.systemui";
    private static final String ACTION_CLOSE = "hideHelper";
    private static final String ACTION_OPEN = "showHelper";
    private static final String ACTION_TOGGLE_AUTO = "toggleAutoScreenShot";

    private static MonitorService instance;

    private WindowManager mWindowManager;
    private Vibrator mVibrator;
    private NotificationManager mNotificationManager;
    private ImageView mButton;
    private LinearLayout mScreenShotsContainer;
    private WindowManager.LayoutParams mScreenShotsContainerParams;
    private WindowManager.LayoutParams mButtonParams;
    private Queue<ImageView> existScreenShots;
    private SharedPreferences mSettings;
    private BroadcastReceiver mActionReceiver;
    private String lastPkg;
    private boolean mIsShow;
    private boolean mAutoScreenShot;
    private int iconTopOffset;
    private int iconRightOffset;
    private int screenShotTopOffset;
    private int screenShotRightOffset;
    private int screenShotSize;
    private int captureCount;
    private int screenShotInterval;
    private DisplayMetrics mDisplayMetrics;
    private Queue<AsyncTask> mRunningTasks;
    private final Object mLock = new Object();

    public MonitorService() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            String pkgName = event.getPackageName().toString();
            if (pkgName.equals(BuildConfig.APPLICATION_ID)){
                addButton();
                addScreenShots();
            }
            else if (pkgName.equals(INGRESS_PACKAGE_NAME)
                    || (pkgName.equals(SYSTEM_UI_PACKAGE_NAME) && lastPkg.equals(INGRESS_PACKAGE_NAME))){
                if (isShow()){
                    addButton();
                    addScreenShots();
                }
                else {
                    removeButton();
                    removeScreenShots();
                }
                showNotification();
            }
            else{
                if (mButton != null){
                    removeButton();
                    removeScreenShots();
                }
                hideNotification();
            }
            lastPkg = pkgName;
        }
    }

    private boolean isShow(){
        return mIsShow;
    }

    private boolean isAutoScreenShot(){
        return mAutoScreenShot;
    }

    private void showNotification(){
        NotificationCompat.Builder mBuild = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentText(getString(R.string.click_to_setting))
                .setAutoCancel(true);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        Intent settingIntent = new Intent(this, MainActivity.class);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(settingIntent);
        PendingIntent settingPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuild.setContentIntent(settingPendingIntent);
        // action
        if (mActionReceiver == null) {
            mActionReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(ACTION_CLOSE)) {
                        hideHelper();
                    }
                    else if (intent.getAction().equals(ACTION_OPEN)) {
                        showHelper();
                    }
                    else if(intent.getAction().equals(ACTION_TOGGLE_AUTO)){
                        mAutoScreenShot = !mAutoScreenShot;
                    }
                    showNotification();
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_CLOSE);
            filter.addAction(ACTION_OPEN);
            filter.addAction(ACTION_TOGGLE_AUTO);
            registerReceiver(mActionReceiver, filter);
        }
        // set by setting
        if (isShow()) {
            mBuild.addAction(
                    R.drawable.ic_clear,
                    getString(R.string.hide_helper),
                    PendingIntent.getBroadcast(this, 1, new Intent(ACTION_CLOSE), 0)
            );
            mBuild.setContentTitle(getString(R.string.notification_title_open));
        } else {
            mBuild.addAction(
                    R.drawable.ic_check,
                    getString(R.string.show_helper),
                    PendingIntent.getBroadcast(this, 1, new Intent(ACTION_OPEN), 0)
            );
            mBuild.setContentTitle(getString(R.string.notification_title_close));
        }
        if (isAutoScreenShot()){
            mBuild.addAction(
                    R.drawable.ic_alarm_off,
                    getString(R.string.disable_auto_screenshot),
                    PendingIntent.getBroadcast(this, 2, new Intent(ACTION_TOGGLE_AUTO), 0)
            );
        }
        else {
            mBuild.addAction(
                    R.drawable.ic_alarm_on,
                    getString(R.string.enable_auto_screenshot),
                    PendingIntent.getBroadcast(this, 2 ,new Intent(ACTION_TOGGLE_AUTO), 0)
            );
        }
        mNotificationManager.notify(0, mBuild.build());
    }

    private void hideHelper(){
        mIsShow = false;
    }

    private void showHelper(){
        mIsShow = true;
    }

    private void hideNotification(){
        mNotificationManager.cancel(0);
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onCreate(){
        super.onCreate();

        existScreenShots = new ArrayBlockingQueue<>(5);
        mRunningTasks = new ArrayDeque<>();
        mWindowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        mVibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        mSettings = getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE);
        mDisplayMetrics = getResources().getDisplayMetrics();

        mIsShow = mSettings.getBoolean(SETTING_IS_SHOW, DEFAULT_IS_SHOW);
        mAutoScreenShot = mSettings.getBoolean(SETTING_AUTO_SCREEN_SHOT, DEFAULT_AUTO_SCREEN_SHOT);
        loadSettings();
        instance = this;
    }

    private void loadSettings() {
        iconTopOffset = dpToPx(mSettings.getInt(SETTING_ICON_TOP_OFFSET_NAME, DEFAULT_ICON_TOP_OFFSET));
        iconRightOffset = dpToPx(mSettings.getInt(SETTING_ICON_RIGHT_OFFSET_NAME, DEFAULT_ICON_RIGHT_OFFSET));
        screenShotTopOffset = dpToPx( mSettings.getInt(SETTING_SCREEN_SHOT_TOP_OFFSET_NAME, DEFAULT_SCREEN_SHOT_TOP_OFFSET));
        screenShotRightOffset = dpToPx(mSettings.getInt(SETTING_SCREEN_SHOT_RIGHT_OFFSET_NAME, DEFAULT_SCREEN_SHOT_RIGHT_OFFSET));
        screenShotSize = dpToPx(mSettings.getInt(SETTING_SCREEN_SHOT_SIZE_NAME, DEFAULT_SCREEN_SHOT_SIZE));
        screenShotInterval = mSettings.getInt(SETTING_SCREEN_SHOT_INTERVAL, DEFAULT_SCREEN_SHOT_INTERVAL);
    }

    private int dpToPx(int dp){
        return (int)TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                mDisplayMetrics
        );
    }

    public void updateIconSetting(int topOffset, int rightOffset){
        iconTopOffset = dpToPx(topOffset);
        iconRightOffset = dpToPx(rightOffset);
        if (mButton != null){
            mButtonParams.x = iconRightOffset;
            mButtonParams.y = iconTopOffset;
            mWindowManager.updateViewLayout(mButton, mButtonParams);
        }
    }
    public void updateScreenShotsSetting(int topOffset, int rightOffset, int size, int interval){
        screenShotTopOffset = dpToPx(topOffset);
        screenShotRightOffset = dpToPx(rightOffset);
        screenShotSize = dpToPx(size);
        screenShotInterval = interval;
        if (mScreenShotsContainer != null){
            mScreenShotsContainerParams.x = screenShotRightOffset;
            mScreenShotsContainerParams.y = screenShotTopOffset;
            mWindowManager.updateViewLayout(mScreenShotsContainer, mScreenShotsContainerParams);
        }
    }

    public static MonitorService getSharedInstance() {
        return instance;
    }

    private Runnable timeOutRunnable = new Runnable() {
        @Override
        public void run() {
            if (existScreenShots != null)
                removeScreenShots();
        }
    };

    private Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            new CaptureTask().execute();
            captureCount--;
            if (mButton != null){
                if (captureCount > 0)
                    mButton.postDelayed(captureRunnable, screenShotInterval);
                else
                    mButton.setEnabled(true);
            }
        }
    };

    private void removeScreenShots() {
        if (mScreenShotsContainer != null){
            existScreenShots.clear();
            mWindowManager.removeView(mScreenShotsContainer);
            mScreenShotsContainer = null;
            captureCount = 0;
        }
    }

    private void removeButton(){
        if (mButton != null){
            mWindowManager.removeView(mButton);
            mButton = null;
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

    private void addButton(){
        if (mButton == null){
            mButton = new ImageView(this);
            mButton.setImageResource(R.drawable.ic_launcher);
            mButton.setOnClickListener(mButtonClickListener);
            mButton.setOnLongClickListener(mButtonLongClickListener);
            if (mButtonParams == null)
                mButtonParams = getLayoutParams(iconTopOffset, iconRightOffset);
            mWindowManager.addView(mButton, mButtonParams);
        }
    }

    private View.OnClickListener mButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mVibrator.vibrate(SCREEN_SHOT_VIBRATE_TIME_IN_MILLISECONDS);
            if (isAutoScreenShot()){
                captureCount = 5;
                v.setEnabled(false);
                captureRunnable.run();
            }
            else {
                new CaptureTask().execute();
            }
        }
    };

    private View.OnLongClickListener mButtonLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (!existScreenShots.isEmpty()) {
                removeScreenShots();
                v.removeCallbacks(timeOutRunnable);
                return true;
            }
            return false;
        }
    };

    private WindowManager.LayoutParams getLayoutParams(int topOffset, int rightOffset) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = rightOffset;
        params.y = topOffset;
        return params;
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mSettings.edit()
                .putBoolean(SETTING_IS_SHOW, mIsShow)
                .putBoolean(SETTING_AUTO_SCREEN_SHOT, mAutoScreenShot)
                .apply();
        removeButton();
        removeScreenShots();
        if (mActionReceiver != null)
            unregisterReceiver(mActionReceiver);
        existScreenShots = null;
        instance = null;
    }

    private Bitmap captureScreenShots(){
        try{
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            BufferedInputStream result = new BufferedInputStream(p.getInputStream());
            os.writeBytes("/system/bin/screencap -p\n");
            Bitmap screenShot = BitmapFactory.decodeStream(result);
            os.writeBytes("exit\n");
            return screenShot;
        }
        catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }

    private Bitmap cropAndResize(Bitmap origin){
        int width = origin.getWidth();
        int height = origin.getHeight();
        int startY = height - getResources().getDimensionPixelSize(R.dimen.screenshot_margin_bottom) - width;
        Bitmap crop = Bitmap.createBitmap(origin, 0, startY, width, width);
        Bitmap resize = Bitmap.createScaledBitmap(crop, screenShotSize, screenShotSize, false);
        origin.recycle();
        crop.recycle();
        return resize;
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

    public class CaptureTask extends AsyncTask<Void, Void, Bitmap>{

        @Override
        public void onPreExecute(){
            synchronized (mLock){
                mRunningTasks.add(this);
            }
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap screenShot = captureScreenShots();
            Bitmap result = cropAndResize(screenShot);
            boolean isFirst;
            synchronized (mLock){
                isFirst = mRunningTasks.peek().equals(this);
            }
            while (!isFirst){
                try {
                    Thread.sleep(200);
                }
                catch (Exception e){
                    e.printStackTrace();
                }
                synchronized (mLock){
                    isFirst = mRunningTasks.peek().equals(this);
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (mButton != null){
                showScreenShot(result);
                mButton.removeCallbacks(timeOutRunnable);
                mButton.postDelayed(timeOutRunnable, GLYPH_TIMEOUT_IN_MILLISECONDS);
            }
            synchronized (mLock){
                mRunningTasks.remove();
            }
        }
    }
}
