package com.ghostflying.glyphhelper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.Optional;


public class MainActivity extends ActionBarActivity {
    @InjectView(R.id.toolbar) Toolbar mToolbar;
    @InjectView(R.id.service_status_view) View mServiceStatusView;
    @InjectView(R.id.service_status) TextView mServiceStatus;
    @InjectView(R.id.icon_top_offset) SeekBar mIconTopOffset;
    @InjectView(R.id.icon_top_offset_value) TextView mIconTopOffsetValue;
    @InjectView(R.id.icon_right_offset) SeekBar mIconRightOffset;
    @InjectView(R.id.icon_right_offset_value) TextView mIconRightOffsetValue;
    @InjectView(R.id.screenshot_top_offset) SeekBar mScreenShotTopOffset;
    @InjectView(R.id.screenshot_top_offset_value) TextView mScreenShotTopOffsetValue;
    @InjectView(R.id.screenshot_right_offset) SeekBar mScreenShotRightOffset;
    @InjectView(R.id.screenshot_right_offset_value) TextView mScreenShotRightOffsetValue;
    @InjectView(R.id.screenshot_size) SeekBar mScreenShotSize;
    @InjectView(R.id.screenshot_size_value) TextView mScreenShotSizeValue;
    @InjectView(R.id.screenshot_interval) SeekBar mScreenShotInterval;
    @InjectView(R.id.screenshot_interval_value) TextView mScreenShotIntervalValue;
    @InjectView(R.id.disable_overlay) View mDisableOverlay;
    @Optional @InjectView(R.id.show_icon) View mShowIconView;
    @Optional @InjectView(R.id.show_icon_toggle) CheckBox mShowIconToggle;
    @Optional @InjectView(R.id.auto_shot) View mAutoShotView;
    @Optional @InjectView(R.id.auto_shot_toggle) CheckBox mAutoShotToggle;

    private SharedPreferences mSetting;
    private MonitorService mService;
    private int iconTopOffset;
    private int iconRightOffset;
    private int screenShotTopOffset;
    private int screenShotRightOffset;
    private int screenShotSize;
    private int screenShotInterval;
    private boolean showIcon;
    private boolean autoShot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);
        setSupportActionBar(mToolbar);
        initViews();
    }

    private void initViews(){
        mSetting = getSharedPreferences(MonitorService.SETTINGS_NAME, MODE_PRIVATE);

        loadSettings();

        mIconTopOffset.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mIconRightOffset.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mScreenShotTopOffset.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mScreenShotRightOffset.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mScreenShotSize.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mScreenShotInterval.setOnSeekBarChangeListener(mSeekBarChangeListener);

        mServiceStatusView.setOnClickListener(mServiceStatusViewClickListener);
        mServiceStatusView.setOnLongClickListener(mServiceStatusViewLongClickListener);

        // compatibility with 4.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN){
            mShowIconView.setOnClickListener(mViewClickListener);
            mAutoShotView.setOnClickListener(mViewClickListener);
            mShowIconToggle.setOnCheckedChangeListener(mCheckBoxChangeListener);
            mAutoShotToggle.setOnCheckedChangeListener(mCheckBoxChangeListener);
        }
    }

    private void loadSettings() {
        iconTopOffset =
                mSetting.getInt(
                        MonitorService.SETTING_ICON_TOP_OFFSET_NAME,
                        MonitorService.DEFAULT_ICON_TOP_OFFSET
                );
        mIconTopOffsetValue.setText(Integer.toString(iconTopOffset));
        mIconTopOffset.setProgress(iconTopOffset);

        iconRightOffset =
                mSetting.getInt(
                        MonitorService.SETTING_ICON_RIGHT_OFFSET_NAME,
                        MonitorService.DEFAULT_ICON_RIGHT_OFFSET
                );
        mIconRightOffsetValue.setText(Integer.toString(iconRightOffset));
        mIconRightOffset.setProgress(iconRightOffset);

        screenShotTopOffset =
                mSetting.getInt(
                        MonitorService.SETTING_SCREEN_SHOT_TOP_OFFSET_NAME,
                        MonitorService.DEFAULT_SCREEN_SHOT_TOP_OFFSET
                );
        mScreenShotTopOffsetValue.setText(Integer.toString(screenShotTopOffset));
        mScreenShotTopOffset.setProgress(screenShotTopOffset);

        screenShotRightOffset =
                mSetting.getInt(
                        MonitorService.SETTING_SCREEN_SHOT_RIGHT_OFFSET_NAME,
                        MonitorService.DEFAULT_SCREEN_SHOT_RIGHT_OFFSET
                );
        mScreenShotRightOffsetValue.setText(Integer.toString(screenShotRightOffset));
        mScreenShotRightOffset.setProgress(screenShotRightOffset);

        screenShotSize =
                mSetting.getInt(
                        MonitorService.SETTING_SCREEN_SHOT_SIZE_NAME,
                        MonitorService.DEFAULT_SCREEN_SHOT_SIZE
                );
        mScreenShotSizeValue.setText(Integer.toString(screenShotSize));
        mScreenShotSize.setProgress(screenShotSize);

        screenShotInterval =
                mSetting.getInt(
                        MonitorService.SETTING_SCREEN_SHOT_INTERVAL,
                        MonitorService.DEFAULT_SCREEN_SHOT_INTERVAL
                );
        mScreenShotIntervalValue.setText(Integer.toString(screenShotInterval));
        mScreenShotInterval.setProgress(screenShotInterval);

        // compatibility with 4.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN){
            showIcon = mSetting.getBoolean(
                    MonitorService.SETTING_IS_SHOW,
                    MonitorService.DEFAULT_IS_SHOW
            );
            mShowIconToggle.setChecked(showIcon);

            autoShot = mSetting.getBoolean(
                    MonitorService.SETTING_AUTO_SCREEN_SHOT,
                    MonitorService.DEFAULT_AUTO_SCREEN_SHOT
            );
            mAutoShotToggle.setChecked(autoShot);
        }
    }

    @Override
    public void onResume(){
        super.onResume();
        checkServiceStatus();
    }

    @Override
    public void onPause(){
        super.onPause();
        mSetting.edit()
                .putInt(MonitorService.SETTING_ICON_TOP_OFFSET_NAME, iconTopOffset)
                .putInt(MonitorService.SETTING_ICON_RIGHT_OFFSET_NAME, iconRightOffset)
                .putInt(MonitorService.SETTING_SCREEN_SHOT_TOP_OFFSET_NAME, screenShotTopOffset)
                .putInt(MonitorService.SETTING_SCREEN_SHOT_RIGHT_OFFSET_NAME, screenShotRightOffset)
                .putInt(MonitorService.SETTING_SCREEN_SHOT_SIZE_NAME, screenShotSize)
                .putInt(MonitorService.SETTING_SCREEN_SHOT_INTERVAL, screenShotInterval)
                .putBoolean(MonitorService.SETTING_IS_SHOW, showIcon)
                .putBoolean(MonitorService.SETTING_AUTO_SCREEN_SHOT, autoShot)
                .apply();
    }

    private View.OnClickListener mServiceStatusViewClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mService == null){
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            else {
                PackageManager pm = getPackageManager();
                Intent appStartIntent = pm.getLaunchIntentForPackage(MonitorService.INGRESS_PACKAGE_NAME);
                if (null != appStartIntent)
                {
                    startActivity(appStartIntent);
                }
            }
        }
    };

    private View.OnLongClickListener mServiceStatusViewLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (mService != null){
                mSetting.edit().clear().apply();
                loadSettings();
                Toast.makeText(MainActivity.this, R.string.setting_reset, Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        }
    };

    private void checkServiceStatus() {
        mService = MonitorService.getSharedInstance();
        if (mService != null){
            mDisableOverlay.setVisibility(View.GONE);
            mServiceStatus.setText(getString(R.string.service_enable));
            mService = MonitorService.getSharedInstance();
        }
        else {
            mDisableOverlay.setVisibility(View.VISIBLE);
            mServiceStatus.setText(getString(R.string.service_disable));
        }
    }

    private View.OnClickListener mViewClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.show_icon:
                    mShowIconToggle.setChecked(!mShowIconToggle.isChecked());
                    break;
                case R.id.auto_shot:
                    mAutoShotToggle.setChecked(!mAutoShotToggle.isChecked());
                    break;
            }
        }
    };

    private CheckBox.OnCheckedChangeListener mCheckBoxChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            switch (buttonView.getId()){
                case R.id.show_icon_toggle:
                    showIcon = isChecked;
                    break;
                case R.id.auto_shot_toggle:
                    autoShot = isChecked;
                    break;
            }
            updateToggle();
        }
    };

    private void updateToggle(){
        mService.updateToggleSetting(showIcon, autoShot);
    }

    private SeekBar.OnSeekBarChangeListener mSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            switch (seekBar.getId()){
                case R.id.icon_top_offset:
                    changeIconTopOffset(progress);
                    break;
                case R.id.icon_right_offset:
                    changeIconRightOffset(progress);
                    break;
                case R.id.screenshot_top_offset:
                    changeScreenShotTopOffset(progress);
                    break;
                case R.id.screenshot_right_offset:
                    changeScreenShotRightOffset(progress);
                    break;
                case R.id.screenshot_size:
                    changeScreenShotSize(progress);
                    break;
                case R.id.screenshot_interval:
                    changeScreenShotInterval(progress);
                    break;
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    private void changeIconTopOffset(int offset){
        mIconTopOffsetValue.setText(Integer.toString(offset));
        iconTopOffset = offset;
        updateIcon();
    }

    private void updateIcon() {
        if (mService != null){
            mService.updateIconSetting(iconTopOffset, iconRightOffset);
        }
    }

    private void changeIconRightOffset(int offset){
        mIconRightOffsetValue.setText(Integer.toString(offset));
        iconRightOffset = offset;
        updateIcon();
    }

    private void changeScreenShotTopOffset(int offset){
        mScreenShotTopOffsetValue.setText(Integer.toString(offset));
        screenShotTopOffset = offset;
        updateScreenShots();
    }

    private void changeScreenShotRightOffset(int offset){
        mScreenShotRightOffsetValue.setText(Integer.toString(offset));
        screenShotRightOffset = offset;
        updateScreenShots();
    }

    private void updateScreenShots(){
        if (mService != null){
            mService.updateScreenShotsSetting(
                    screenShotTopOffset,
                    screenShotRightOffset,
                    screenShotSize,
                    screenShotInterval
            );
        }
    }

    private void changeScreenShotSize(int size){
        mScreenShotSizeValue.setText(Integer.toString(size));
        screenShotSize = size;
        updateScreenShots();
    }

    private void changeScreenShotInterval(int interval){
        mScreenShotIntervalValue.setText(Integer.toString(interval));
        screenShotInterval = interval;
        updateScreenShots();
    }
}
