/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.policy.impl;

import com.android.internal.R;
import com.android.internal.telephony.IccCard;
import com.android.internal.view.FastBitmapDrawable;
import com.android.internal.widget.CircularSelector;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.RotarySelector;
import com.android.internal.widget.SenseLikeLock;
import com.android.internal.widget.SlidingTab;
import com.android.internal.widget.UnlockRing;
import com.android.internal.widget.SenseLikeLock.OnSenseLikeSelectorTriggerListener;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

/**
 * The screen within {@link LockPatternKeyguardView} that shows general
 * information about the device depending on its state, and how to get
 * past it, as applicable.
 */
class LockScreen extends LinearLayout implements KeyguardScreen, KeyguardUpdateMonitor.InfoCallback,
        KeyguardUpdateMonitor.SimStateCallback,  CircularSelector.OnCircularSelectorTriggerListener, UnlockRing.OnHoneyTriggerListener, 
        SlidingTab.OnTriggerListener, RotarySelector.OnDialTriggerListener, SenseLikeLock.OnSenseLikeSelectorTriggerListener {

    private static final boolean DBG = true;
    private static final String TAG = "LockScreen";
    private static final String ENABLE_MENU_KEY_FILE = "/data/local/enable_menu_key";
    private static final Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");

    private Status mStatus = Status.Normal;

    private LockPatternUtils mLockPatternUtils;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private KeyguardScreenCallback mCallback;

    private TextView mCarrier;

    /* Unlockers */
    private SlidingTab mSelector;
	private RotarySelector mRotarySelector;
	private CircularSelector mCircularSelector;
	private UnlockRing mUnlockRing;
	private SenseLikeLock mSenseRingSelector;
	
	/* Other views */
    private TextView mTime;
    private TextView mDate;
    private TextView mStatus1;
    private TextView mStatus2;
    private TextView mScreenLocked;
    private TextView mEmergencyCallText;
    private String mCarrierCap;
    private Button mEmergencyCallButton;
    
    /* Music Controls */
    private ImageView mHideMusicControlsButton;
    private ImageView mDisplayMusicControlsButton;
    private ImageButton mPlayIcon;
    private ImageButton mPauseIcon;
    private ImageButton mRewindIcon;
    private ImageButton mForwardIcon;
    private ImageButton mAlbumArt;
    private ImageButton mLockSMS;
    private ImageButton mLockPhone;
    
    
    


    private AudioManager am = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
    private boolean mWasMusicActive = false;
    private boolean mIsMusicActive = am.isMusicActive();

    private boolean mAreMusicControlsVisible = false;

    // current configuration state of keyboard and display
    private int mKeyboardHidden;
    private int mCreationOrientation;

    // are we showing battery information?
    private boolean mShowingBatteryInfo = false;

    // last known plugged in state
    private boolean mPluggedIn = false;

    // last known battery level
    private int mBatteryLevel = 100;

    private String mNextAlarm = null;
    private Drawable mAlarmIcon = null;
    private String mCharging = null;
    private Drawable mChargingIcon = null;

    private boolean mSilentMode;
    private AudioManager mAudioManager;
    private String mDateFormatString;
    private java.text.DateFormat mTimeFormat;
    private boolean mEnableMenuKeyInLockScreen;

    private TextView mNowPlayingArtist;
    private TextView mNowPlayingAlbum;

    private boolean mLockAlwaysBattery = (Settings.System.getInt(mContext.getContentResolver(),
	    Settings.System.LOCKSCREEN_ALWAYS_BATTERY, 1) == 1);

    private boolean mTrackpadUnlockScreen = (Settings.System.getInt(mContext.getContentResolver(),
	    Settings.System.TRACKPAD_UNLOCK_SCREEN, 0) == 1);

    private boolean mMenuUnlockScreen = (Settings.System.getInt(mContext.getContentResolver(),
	    Settings.System.MENU_UNLOCK_SCREEN, 0) == 1);

    private boolean mLockscreenShortcuts = (Settings.System.getInt(mContext.getContentResolver(),
	    Settings.System.LOCKSCREEN_SHORTCUTS, 0) == 1);
    
    private boolean mUseTab   = (Settings.System.getInt(mContext.getContentResolver(),
    	    Settings.System.LOCKSCREEN_TYPE, 1) == Settings.System.USE_TAB_LOCKSCREEN);
    private boolean mUseRotary  = (Settings.System.getInt(mContext.getContentResolver(),
    	    Settings.System.LOCKSCREEN_TYPE, 1) == Settings.System.USE_ROTARY_LOCKSCREEN);
    private boolean mUseCircular  = (Settings.System.getInt(mContext.getContentResolver(),
    	    Settings.System.LOCKSCREEN_TYPE, 1) == Settings.System.USE_HCC_LOCKSCREEN);
    private boolean mUseHoney  = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_TYPE, 1) == Settings.System.USE_HONEYCOMB_LOCKSCREEN);
    private boolean mUseSenseLike  = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_TYPE, 1) == Settings.System.USE_SENSELIKE_LOCKSCREEN);
    
    private int mLockscreenStyle = Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_TYPE, 1);
    
    //lockscreen constants
    public static final int LOCKCSREEN_TAB = 1;
    public static final int LOCKSCREEN_ROTARY = 2;
    public static final int LOCKSCREEN_HC_CONCEPT = 3;
    public static final int LOCKSCREEN_HC = 4;
    
    //custom quadrants for honeycomb
    // can also be used for the sense like 
    // app selection
    private String[] mCustomQuandrants = {(Settings.System.getString(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_CUSTOM_APP_HONEY_1)),(Settings.System.getString(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_CUSTOM_APP_HONEY_2)),(Settings.System.getString(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_CUSTOM_APP_HONEY_3)),(Settings.System.getString(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_CUSTOM_APP_HONEY_4))};
    
    Intent[] mCustomApps;
    
    // Default to show
    private boolean mShouldShowMusicControls = (Settings.System.getInt(mContext.getContentResolver(),
    	    Settings.System.LOCKSCREEN_MUSIC_ON, 1) == 1);

    // Default to portrait
    private boolean mLockScreenOrientationLand = (Settings.System.getInt(mContext.getContentResolver(),
    	    Settings.System.LOCKSCREEN_ORIENTATION, Configuration.ORIENTATION_PORTRAIT) == Configuration.ORIENTATION_LANDSCAPE);
    
    /**
     * The status of this lock screen.
     */
    enum Status {
        /**
         * Normal case (sim card present, it's not locked)
         */
        Normal(true),

        /**
         * The sim card is 'network locked'.
         */
        NetworkLocked(true),

        /**
         * The sim card is missing.
         */
        SimMissing(false),

        /**
         * The sim card is missing, and this is the device isn't provisioned, so we don't let
         * them get past the screen.
         */
        SimMissingLocked(false),

        /**
         * The sim card is PUK locked, meaning they've entered the wrong sim unlock code too many
         * times.
         */
        SimPukLocked(false),

        /**
         * The sim card is locked.
         */
        SimLocked(true);

        private final boolean mShowStatusLines;

        Status(boolean mShowStatusLines) {
            this.mShowStatusLines = mShowStatusLines;
        }

        /**
         * @return Whether the status lines (battery level and / or next alarm) are shown while
         *         in this state.  Mostly dictated by whether this is room for them.
         */
        public boolean showStatusLines() {
            return mShowStatusLines;
        }
    }

    /**
     * In general, we enable unlocking the insecure key guard with the menu key. However, there are
     * some cases where we wish to disable it, notably when the menu button placement or technology
     * is prone to false positives.
     *
     * @return true if the menu key should be enabled
     */
    private boolean shouldEnableMenuKey() {
        final Resources res = getResources();
        final boolean configDisabled = res.getBoolean(R.bool.config_disableMenuKeyInLockScreen);
        final boolean isMonkey = SystemProperties.getBoolean("ro.monkey", false);
        final boolean fileOverride = (new File(ENABLE_MENU_KEY_FILE)).exists();
        return !configDisabled || isMonkey || fileOverride;
    }

    /**
     * @param context Used to setup the view.
     * @param configuration The current configuration. Used to use when selecting layout, etc.
     * @param lockPatternUtils Used to know the state of the lock pattern settings.
     * @param updateMonitor Used to register for updates on various keyguard related
     *    state, and query the initial state at setup.
     * @param callback Used to communicate back to the host keyguard view.
     */
    LockScreen(Context context, Configuration configuration, LockPatternUtils lockPatternUtils,
            KeyguardUpdateMonitor updateMonitor,
            KeyguardScreenCallback callback) {
        super(context);
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;

        mEnableMenuKeyInLockScreen = shouldEnableMenuKey();

        mCreationOrientation = configuration.orientation;

        mKeyboardHidden = configuration.hardKeyboardHidden;

        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** CREATING LOCK SCREEN", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + " res orient=" + context.getResources().getConfiguration().orientation);
        }

        Log.v(TAG, "Force landscape orientation = " + Boolean.toString(mLockScreenOrientationLand));
            
        final LayoutInflater inflater = LayoutInflater.from(context);
        if (DBG) Log.v(TAG, "Creation orientation = " + mCreationOrientation);
        
        if (mCreationOrientation == Configuration.ORIENTATION_PORTRAIT) {
            inflater.inflate(R.layout.keyguard_screen_widgets_unlock, this, true);
        } else {
            inflater.inflate(R.layout.keyguard_screen_widgets_unlock_land, this, true);
        }

        mCarrier = (TextView) findViewById(R.id.carrier);
        // Required for Marquee to work
        mCarrier.setSelected(true);
        mCarrier.setTextColor(0xffffffff);

        mDate = (TextView) findViewById(R.id.date);
        mStatus1 = (TextView) findViewById(R.id.status1);
        mStatus2 = (TextView) findViewById(R.id.status2);

        mScreenLocked = (TextView) findViewById(R.id.screenLocked);
        
        // Set up the selectors
        
        mSelector = (SlidingTab) findViewById(R.id.tab_selector);
        mSelector.setHoldAfterTrigger(true, false);
        mSelector.setLeftHintText(R.string.lockscreen_unlock_label);
        mSelector.setOnTriggerListener(this);
        
        mRotarySelector = (RotarySelector) this.findViewById(R.id.rotary_selector);
        mRotarySelector.setOnDialTriggerListener(this);
        mRotarySelector.setLeftHandleResource(R.drawable.ic_jog_dial_unlock);

        
        mCircularSelector = (CircularSelector) findViewById(R.id.circular_selector);
        mCircularSelector.setOnCircularSelectorTriggerListener(this);
        
        mUnlockRing = (UnlockRing) findViewById(R.id.unlock_ring);
        mUnlockRing.setOnHoneyTriggerListener(this);
        
        // end selector setup
        
        mSenseRingSelector = (SenseLikeLock) findViewById(R.id.sense_selector);
        mSenseRingSelector.setOnSenseLikeSelectorTriggerListener(this);
        setupSenseLikeRingShortcuts();



        mEmergencyCallText = (TextView) findViewById(R.id.emergencyCallText);
        mEmergencyCallButton = (Button) findViewById(R.id.emergencyCallButton);
        mEmergencyCallButton.setText(R.string.lockscreen_emergency_call);

        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
        mEmergencyCallButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.takeEmergencyCallAction();
            }
        });

    
	SetUpShortCuts();
	SetUpMusicControls();

       

	

        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mUpdateMonitor.registerInfoCallback(this);
        mUpdateMonitor.registerSimStateCallback(this);

        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mSilentMode = isSilentMode();

        mSelector.setLeftTabResources(
                R.drawable.ic_jog_dial_unlock,
                R.drawable.jog_tab_target_green,
                R.drawable.jog_tab_bar_left_unlock,
                R.drawable.jog_tab_left_unlock);

        updateRightTabResources();


        resetStatusInfo(updateMonitor);
    }
    
    private void SetUpShortCuts() {
    	//
    	


        mLockSMS = (ImageButton) findViewById(R.id.smsShortcutButton);
	mLockPhone = (ImageButton) findViewById(R.id.phoneShortcutButton);

	mLockSMS.setVisibility(View.GONE);
	mLockPhone.setVisibility(View.GONE);
	//
	
	/*int commompadding = 15;
	*mLockPhone.setPadding(commompadding, commompadding, commompadding, commompadding);
	*mLockSMS.setPadding(commompadding, commompadding, commompadding, commompadding);
	* 
	* //Now set the top,bottom padding depending on the lockscreen type
	* 
	* 
	*mLockPhone.setPadding(commompadding, commompadding, commompadding*3, commompadding);
	*mLockSMS.setPadding(commompadding, commompadding, commompadding*3, commompadding);
	*
	*/
    	////
    	mLockPhone.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
	        mCallback.pokeWakelock();
		Vibrator vibe = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                long[] pattern = {
                                0, 100
		};
		vibe.vibrate(pattern, -1);
                Intent i = new Intent();
                Intent intent = new Intent(Intent.ACTION_DIAL); 
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                mCallback.goToUnlockScreen();
		return true;
	    }
	});

	mLockSMS.setOnLongClickListener(new View.OnLongClickListener() {
	    public boolean onLongClick(View v) {
		mCallback.pokeWakelock();
		Vibrator vibe = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                long[] pattern = {
                                0, 100
		};
		vibe.vibrate(pattern, -1);
                Intent i = new Intent();
                Intent mmsIntent = new Intent(Intent.ACTION_VIEW);
		mmsIntent.setClassName("com.android.mms","com.android.mms.ui.ConversationList");
                mmsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        getContext().startActivity(mmsIntent);
                mCallback.goToUnlockScreen();
		return true;
	    }
	});
		// TODO Auto-generated method stub
    	if(!mLockscreenShortcuts) {
    	    mLockPhone.setVisibility(View.GONE);
    	    mLockSMS.setVisibility(View.GONE);
    	} else {
    	    mLockPhone.setVisibility(View.VISIBLE);
    	    mLockSMS.setVisibility(View.VISIBLE);
    	}
	}

	private void SetUpMusicControls(){
    	
        mHideMusicControlsButton = (ImageView) findViewById(R.id.hide_music_controls_button);
        mDisplayMusicControlsButton = (ImageView) findViewById(R.id.display_music_controls_button);

        mPlayIcon = (ImageButton) findViewById(R.id.musicControlPlay);
        mPauseIcon = (ImageButton) findViewById(R.id.musicControlPause);
        mRewindIcon = (ImageButton) findViewById(R.id.musicControlPrevious);
        mForwardIcon = (ImageButton) findViewById(R.id.musicControlNext);
        mAlbumArt = (ImageButton) findViewById(R.id.albumArt);
        
        mNowPlayingArtist = (TextView) findViewById(R.id.musicNowPlayingArtist);
        mNowPlayingArtist.setSelected(true); // set focus to TextView to allow scrolling
        mNowPlayingArtist.setTextColor(0xffffffff);

        mNowPlayingAlbum = (TextView) findViewById(R.id.musicNowPlayingAlbum);
        mNowPlayingAlbum.setSelected(true); // set focus to TextView to allow scrolling
        mNowPlayingAlbum.setTextColor(0xffffffff);
    	
    	mHideMusicControlsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.pokeWakelock();
                mAreMusicControlsVisible = false;
                mDisplayMusicControlsButton.setVisibility(View.VISIBLE);
                mHideMusicControlsButton.setVisibility(View.GONE);
                    mPauseIcon.setVisibility(View.GONE);
                    mPlayIcon.setVisibility(View.GONE);
                    mRewindIcon.setVisibility(View.GONE);
                    mForwardIcon.setVisibility(View.GONE);
                    mNowPlayingAlbum.setVisibility(View.GONE);
                    mNowPlayingArtist.setVisibility(View.GONE);
                    mAlbumArt.setVisibility(View.GONE);
            }
        });

        mDisplayMusicControlsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.pokeWakelock();
                mAreMusicControlsVisible = true;
		
		if (mIsMusicActive) {
			
                mDisplayMusicControlsButton.setVisibility(View.GONE);
                mHideMusicControlsButton.setVisibility(View.VISIBLE);
                    mPauseIcon.setVisibility(View.VISIBLE);
                    mPlayIcon.setVisibility(View.GONE);
                    mRewindIcon.setVisibility(View.VISIBLE);
                    mForwardIcon.setVisibility(View.VISIBLE);
                    mNowPlayingAlbum.setVisibility(View.VISIBLE);
                    mNowPlayingArtist.setVisibility(View.VISIBLE);
                    mAlbumArt.setVisibility(View.VISIBLE);
                    // Set album art
                    Uri uri = getArtworkUri(getContext(), KeyguardViewMediator.SongId(),
                    KeyguardViewMediator.AlbumId());
                    if (uri != null) {
                        mAlbumArt.setImageURI(uri); 
                    }
		}
		
		// 
		if (mWasMusicActive) {
                mDisplayMusicControlsButton.setVisibility(View.GONE);
                mHideMusicControlsButton.setVisibility(View.VISIBLE);
                    mPauseIcon.setVisibility(View.GONE);
                    mPlayIcon.setVisibility(View.VISIBLE);
                    mRewindIcon.setVisibility(View.GONE);
                    mForwardIcon.setVisibility(View.GONE);
                    mNowPlayingAlbum.setVisibility(View.GONE);
                    mNowPlayingArtist.setVisibility(View.GONE);
                    mAlbumArt.setVisibility(View.GONE);
                }
            }
        });
    	

        mPlayIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.pokeWakelock();
                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
		mWasMusicActive = false;
            }
        });

        mPauseIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.pokeWakelock();
                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
		mWasMusicActive = true;
            }
        });

        mRewindIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.pokeWakelock();
                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                mWasMusicActive = false;
            }
        });

        mForwardIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.pokeWakelock();
                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
                mWasMusicActive = false;
            }
        });

	//TODO: Launch Music app on long press.
        mHideMusicControlsButton.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                Intent musicIntent = new Intent(Intent.ACTION_VIEW);
                musicIntent.setClassName("com.android.music","com.android.music.MediaPlaybackActivity");
                musicIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(musicIntent);
                mCallback.goToUnlockScreen();
                                return true;           
            }
        });

        //TODO: Launch Music app on long press.
        mDisplayMusicControlsButton.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                Intent musicIntent = new Intent(Intent.ACTION_VIEW);
                musicIntent.setClassName("com.android.music","com.android.music.MediaPlaybackActivity");
                musicIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(musicIntent);
                mCallback.goToUnlockScreen();
                                return true;           
            }
        });
        
        mAlbumArt.setVisibility(View.GONE);
        mDisplayMusicControlsButton.setVisibility(View.GONE);
        mHideMusicControlsButton.setVisibility(View.GONE);
        
    	
    }

    private boolean isSilentMode() {
        return mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
    }

    private void updateRightTabResources() {
        boolean vibe = mSilentMode
            && (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);

        
        mRotarySelector
        .setRightHandleResource(mSilentMode ? (vibe ? R.drawable.ic_jog_dial_vibrate_on
                : R.drawable.ic_jog_dial_sound_off) : R.drawable.ic_jog_dial_sound_on);

        
        mSelector.setRightTabResources(
                mSilentMode ? ( vibe ? R.drawable.ic_jog_dial_vibrate_on
                                     : R.drawable.ic_jog_dial_sound_off )
                            : R.drawable.ic_jog_dial_sound_on,
                mSilentMode ? R.drawable.jog_tab_target_yellow
                            : R.drawable.jog_tab_target_gray,
                mSilentMode ? R.drawable.jog_tab_bar_right_sound_on
                            : R.drawable.jog_tab_bar_right_sound_off,
                mSilentMode ? R.drawable.jog_tab_right_sound_on
                            : R.drawable.jog_tab_right_sound_off);
    }

    private void resetStatusInfo(KeyguardUpdateMonitor updateMonitor) {
        mShowingBatteryInfo = updateMonitor.shouldShowBatteryInfo();
        mPluggedIn = updateMonitor.isDevicePluggedIn();
        mBatteryLevel = updateMonitor.getBatteryLevel();

        mStatus = getCurrentStatus(updateMonitor.getSimState());
        updateLayout(mStatus);

        refreshBatteryStringAndIcon();
        refreshAlarmDisplay();

        refreshMusicMod();

        mTimeFormat = DateFormat.getTimeFormat(getContext());
        mDateFormatString = getContext().getString(R.string.full_wday_month_day_no_year);
        refreshTimeAndDateDisplay();
        updateStatusLines();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER && mTrackpadUnlockScreen)
                || (keyCode == KeyEvent.KEYCODE_MENU && mMenuUnlockScreen)
                || (keyCode == KeyEvent.KEYCODE_MENU && mEnableMenuKeyInLockScreen)) {

            mCallback.goToUnlockScreen();
        }
        return false;
    }

    public void OnCircularSelectorGrabbedStateChanged(View v, int GrabState) {
        // TODO Auto-generated method stub
        mCallback.pokeWakelock();

    }
    
    public void onHoneyTrigger(View v, int trigger) {
        final String TOGGLE_SILENT = "silent_mode";
        
        if (trigger == UnlockRing.OnHoneyTriggerListener.UNLOCK_HANDLE) {
            mCallback.goToUnlockScreen();

        } else if (mCustomQuandrants[0] != null
                && trigger == UnlockRing.OnHoneyTriggerListener.QUADRANT_1) {
            if (mCustomQuandrants[0].equals(TOGGLE_SILENT)) {
                toggleSilentMode();
                mCallback.pokeWakelock();
                mSelector.reset(false);
            } else {
                try {
                    Intent i = Intent.parseUri(mCustomQuandrants[0], 0);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    mContext.startActivity(i);
                    mCallback.goToUnlockScreen();
                } catch (Exception e) {
                    mSelector.reset(false);
                }
            }
        } else if (mCustomQuandrants[1] != null
                && trigger == UnlockRing.OnHoneyTriggerListener.QUADRANT_2) {
            if (mCustomQuandrants[1].equals(TOGGLE_SILENT)) {
                toggleSilentMode();
                mSelector.reset(false);
                mCallback.pokeWakelock();
            } else {
                try {
                    Intent i = Intent.parseUri(mCustomQuandrants[1], 0);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    mContext.startActivity(i);
                    mCallback.goToUnlockScreen();
                } catch (Exception e) {
                    mSelector.reset(false);
                }
            }
        } else if (mCustomQuandrants[2] != null
                && trigger == UnlockRing.OnHoneyTriggerListener.QUADRANT_3) {
            if (mCustomQuandrants[2].equals(TOGGLE_SILENT)) {
                toggleSilentMode();
                mSelector.reset(false);
                mCallback.pokeWakelock();
            } else {
                try {
                    Intent i = Intent.parseUri(mCustomQuandrants[2], 0);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    mContext.startActivity(i);
                    mCallback.goToUnlockScreen();
                } catch (Exception e) {
                    mSelector.reset(false);
                }
            }
        } else if (mCustomQuandrants[3] != null
                && trigger == UnlockRing.OnHoneyTriggerListener.QUADRANT_4) {
            if (mCustomQuandrants[3].equals(TOGGLE_SILENT)) {
                toggleSilentMode();
                mSelector.reset(false);
                mCallback.pokeWakelock();
            } else {
                try {
                    Intent i = Intent.parseUri(mCustomQuandrants[3], 0);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    mContext.startActivity(i);
                    mCallback.goToUnlockScreen();
                } catch (Exception e) {
                    mSelector.reset(false);
                }
            }
        }
    }

    public void onCircularSelectorTrigger(View v, int Trigger) {

        mCallback.goToUnlockScreen();
        //

    }
	 /** {@inheritDoc} */
    public void onDialTrigger(View v, int whichHandle) {
        boolean mUnlockTrigger=false;
        boolean mCustomAppTrigger=false;

        if(whichHandle == RotarySelector.OnDialTriggerListener.LEFT_HANDLE){
            mUnlockTrigger=true;
        }

        if (mUnlockTrigger) {
            mCallback.goToUnlockScreen();
        } 
        
        if (whichHandle == RotarySelector.OnDialTriggerListener.RIGHT_HANDLE) {
            // toggle silent mode
            toggleSilentMode();
            updateRightTabResources();

            String message = mSilentMode ? getContext().getString(
                    R.string.global_action_silent_mode_on_status) : getContext().getString(
                    R.string.global_action_silent_mode_off_status);

            final int toastIcon = mSilentMode ? R.drawable.ic_lock_ringer_off
                    : R.drawable.ic_lock_ringer_on;
            final int toastColor = mSilentMode ? getContext().getResources().getColor(
                    R.color.keyguard_text_color_soundoff) : getContext().getResources().getColor(
                    R.color.keyguard_text_color_soundon);
            toastMessage(mScreenLocked, message, toastColor, toastIcon);
            mCallback.pokeWakelock();
        }
    }
    private void toggleSilentMode() {
        // tri state silent<->vibrate<->ring if silent mode is enabled, otherwise toggle silent mode
        final boolean mVolumeControlSilent = Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.VOLUME_CONTROL_SILENT, 0) != 0;
        mSilentMode = mVolumeControlSilent
            ? ((mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) || !mSilentMode)
            : !mSilentMode;
        if (mSilentMode) {
            final boolean vibe = mVolumeControlSilent
            ? (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE)
            : (Settings.System.getInt(
                getContext().getContentResolver(),
                Settings.System.VIBRATE_IN_SILENT, 1) == 1);

            mAudioManager.setRingerMode(vibe
                ? AudioManager.RINGER_MODE_VIBRATE
                : AudioManager.RINGER_MODE_SILENT);
        } else {
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
}

    /** {@inheritDoc} */
    public void onTrigger(View v, int whichHandle) {
        if (whichHandle == SlidingTab.OnTriggerListener.LEFT_HANDLE) {
            mCallback.goToUnlockScreen();
        } else if (whichHandle == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
            // toggle silent mode
            mSilentMode = !mSilentMode;
            if (mSilentMode) {
                final boolean vibe = (Settings.System.getInt(
                    getContext().getContentResolver(),
                    Settings.System.VIBRATE_IN_SILENT, 1) == 1);

                mAudioManager.setRingerMode(vibe
                    ? AudioManager.RINGER_MODE_VIBRATE
                    : AudioManager.RINGER_MODE_SILENT);
            } else {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }

            updateRightTabResources();

            String message = mSilentMode ?
                    getContext().getString(R.string.global_action_silent_mode_on_status) :
                    getContext().getString(R.string.global_action_silent_mode_off_status);

            final int toastIcon = mSilentMode
                ? R.drawable.ic_lock_ringer_off
                : R.drawable.ic_lock_ringer_on;

            final int toastColor = mSilentMode
                ? getContext().getResources().getColor(R.color.keyguard_text_color_soundoff)
                : getContext().getResources().getColor(R.color.keyguard_text_color_soundon);
            toastMessage(mScreenLocked, message, toastColor, toastIcon);
            mCallback.pokeWakelock();
        }
    }

    /** {@inheritDoc} */
    public void onGrabbedStateChange(View v, int grabbedState) {
        if (grabbedState == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
            mSilentMode = isSilentMode();
            mSelector.setRightHintText(mSilentMode ? R.string.lockscreen_sound_on_label
                    : R.string.lockscreen_sound_off_label);
        }
        // Don't poke the wake lock when returning to a state where the handle is
        // not grabbed since that can happen when the system (instead of the user)
        // cancels the grab.
        if (grabbedState != SlidingTab.OnTriggerListener.NO_HANDLE) {
            mCallback.pokeWakelock();
        }
    }
    
    public void onHoneyGrabbedStateChange(View v, int grabbedState) {
        if (grabbedState != UnlockRing.OnHoneyTriggerListener.NO_HANDLE) {
            mCallback.pokeWakelock();
        }
    }

    /**
     * Displays a message in a text view and then restores the previous text.
     * @param textView The text view.
     * @param text The text.
     * @param color The color to apply to the text, or 0 if the existing color should be used.
     * @param iconResourceId The left hand icon.
     */
    private void toastMessage(final TextView textView, final String text, final int color, final int iconResourceId) {
        if (mPendingR1 != null) {
            textView.removeCallbacks(mPendingR1);
            mPendingR1 = null;
        }
        if (mPendingR2 != null) {
            mPendingR2.run(); // fire immediately, restoring non-toasted appearance
            textView.removeCallbacks(mPendingR2);
            mPendingR2 = null;
        }

        final String oldText = textView.getText().toString();
        final ColorStateList oldColors = textView.getTextColors();

        mPendingR1 = new Runnable() {
            public void run() {
                textView.setText(text);
                if (color != 0) {
                    textView.setTextColor(color);
                }
                textView.setCompoundDrawablesWithIntrinsicBounds(iconResourceId, 0, 0, 0);
            }
        };

        textView.postDelayed(mPendingR1, 0);
        mPendingR2 = new Runnable() {
            public void run() {
                textView.setText(oldText);
                textView.setTextColor(oldColors);
                textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
        };
        textView.postDelayed(mPendingR2, 3500);
    }
    private Runnable mPendingR1;
    private Runnable mPendingR2;

    private void refreshAlarmDisplay() {
        mNextAlarm = mLockPatternUtils.getNextAlarm();
        if (mNextAlarm != null) {
            mAlarmIcon = getContext().getResources().getDrawable(R.drawable.ic_lock_idle_alarm);
        }
        updateStatusLines();
    }

    /** {@inheritDoc} */
    public void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn,
            int batteryLevel) {
        if (DBG) Log.d(TAG, "onRefreshBatteryInfo(" + showBatteryInfo + ", " + pluggedIn + ")");
        mShowingBatteryInfo = showBatteryInfo;
        mPluggedIn = pluggedIn;
        mBatteryLevel = batteryLevel;

        refreshBatteryStringAndIcon();
        updateStatusLines();
    }

    private void refreshBatteryStringAndIcon() {
        if (!mShowingBatteryInfo && !mLockAlwaysBattery) {
            mCharging = null;
            return;
        }

        if (mPluggedIn) {
            mChargingIcon =
                getContext().getResources().getDrawable(R.drawable.ic_lock_idle_charging);
            if (mBatteryLevel >= 100) {
                mCharging = getContext().getString(R.string.lockscreen_charged);
            } else {
                mCharging = getContext().getString(R.string.lockscreen_plugged_in, mBatteryLevel);
            }
        } else {
            if (mBatteryLevel <= 20) {
                mChargingIcon =
                    getContext().getResources().getDrawable(R.drawable.ic_lock_idle_low_battery);
                mCharging = getContext().getString(R.string.lockscreen_low_battery, mBatteryLevel);
            } else {
                mChargingIcon =
                    getContext().getResources().getDrawable(R.drawable.ic_lock_idle_discharging);
                mCharging = getContext().getString(R.string.lockscreen_discharging, mBatteryLevel);
            }
        }
    }

    private void sendMediaButtonEvent(int code) {
        long eventtime = SystemClock.uptimeMillis();

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        getContext().sendOrderedBroadcast(downIntent, null);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, code, 0);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        getContext().sendOrderedBroadcast(upIntent, null);
    }

    private void refreshMusicMod() {
        String nowPlayingArtist = KeyguardViewMediator.NowPlayingArtist();
        mNowPlayingArtist.setText(nowPlayingArtist);
        String nowPlayingAlbum = KeyguardViewMediator.NowPlayingAlbum();
        mNowPlayingAlbum.setText(nowPlayingAlbum);

        if ((mIsMusicActive )) {
	Log.d(TAG, "IsMusicActive");
		if ((mAreMusicControlsVisible)) {
		    mDisplayMusicControlsButton.setVisibility(View.GONE);
                    mHideMusicControlsButton.setVisibility(View.VISIBLE);
                    mPauseIcon.setVisibility(View.VISIBLE);
                    mPlayIcon.setVisibility(View.GONE);
                    mRewindIcon.setVisibility(View.VISIBLE);
                    mForwardIcon.setVisibility(View.VISIBLE);
                    mNowPlayingAlbum.setVisibility(View.VISIBLE);
                    mNowPlayingArtist.setVisibility(View.VISIBLE);
                    mAlbumArt.setVisibility(View.VISIBLE);
                    // Set album art
                    Uri uri = getArtworkUri(getContext(), KeyguardViewMediator.SongId(),
                    KeyguardViewMediator.AlbumId());
            	    if (uri != null) {
                	mAlbumArt.setImageURI(uri); 
		    } 
		} else {
                    if(mShouldShowMusicControls)mDisplayMusicControlsButton.setVisibility(View.VISIBLE);
                    else mDisplayMusicControlsButton.setVisibility(View.GONE);
                    
                    mHideMusicControlsButton.setVisibility(View.GONE);
                    mPauseIcon.setVisibility(View.GONE);
                    mPlayIcon.setVisibility(View.GONE);
                    mRewindIcon.setVisibility(View.GONE);
                    mForwardIcon.setVisibility(View.GONE);
                    mNowPlayingAlbum.setVisibility(View.GONE);
                    mNowPlayingArtist.setVisibility(View.GONE);
                    mAlbumArt.setVisibility(View.GONE);
		}
	}
	
	if ((mWasMusicActive)) {
	Log.d(TAG, "WasMusicActive");
              if ((mAreMusicControlsVisible)) {
                    mDisplayMusicControlsButton.setVisibility(View.GONE);
                    mHideMusicControlsButton.setVisibility(View.VISIBLE);
                    mPauseIcon.setVisibility(View.GONE);
                    mPlayIcon.setVisibility(View.VISIBLE);
                    mRewindIcon.setVisibility(View.GONE);
                    mForwardIcon.setVisibility(View.GONE);
                    mNowPlayingAlbum.setVisibility(View.GONE);
                    mNowPlayingArtist.setVisibility(View.GONE);
                    mAlbumArt.setVisibility(View.GONE);
                } else {
                    mDisplayMusicControlsButton.setVisibility(View.VISIBLE);
                    mHideMusicControlsButton.setVisibility(View.GONE);
                    mPauseIcon.setVisibility(View.GONE);
                    mPlayIcon.setVisibility(View.GONE);
                    mRewindIcon.setVisibility(View.GONE);
                    mForwardIcon.setVisibility(View.GONE);
                    mNowPlayingAlbum.setVisibility(View.GONE);
                    mNowPlayingArtist.setVisibility(View.GONE);
                    mAlbumArt.setVisibility(View.GONE);
                }
	}
	
	if ((!mIsMusicActive && !mWasMusicActive)) {
                    mDisplayMusicControlsButton.setVisibility(View.GONE);
                    mHideMusicControlsButton.setVisibility(View.GONE);
                    mPauseIcon.setVisibility(View.GONE);
                    mPlayIcon.setVisibility(View.GONE);
                    mRewindIcon.setVisibility(View.GONE);
                    mForwardIcon.setVisibility(View.GONE);
                    mNowPlayingAlbum.setVisibility(View.GONE);
                    mNowPlayingArtist.setVisibility(View.GONE);
                    mAlbumArt.setVisibility(View.GONE);
	}
    }

    /** {@inheritDoc} */
    public void onMusicChanged() {
        refreshMusicMod();
    }

    /** {@inheritDoc} */
    public void onTimeChanged() {
        refreshTimeAndDateDisplay();
    }

    private void refreshTimeAndDateDisplay() {
        mRotarySelector.invalidate();
        mDate.setText(DateFormat.format(mDateFormatString, new Date()));
    }

    private void updateStatusLines() {
        if (!mStatus.showStatusLines()
                || (mCharging == null && mNextAlarm == null)) {
            mStatus1.setVisibility(View.INVISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);
        } else if (mCharging != null && mNextAlarm == null) {
            // charging only
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);

            mStatus1.setText(mCharging);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mChargingIcon, null, null, null);
        } else if (mNextAlarm != null && mCharging == null) {
            // next alarm only
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);

            mStatus1.setText(mNextAlarm);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mAlarmIcon, null, null, null);
        } else if (mCharging != null && mNextAlarm != null) {
            // both charging and next alarm
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.VISIBLE);

            mStatus1.setText(mCharging);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mChargingIcon, null, null, null);
            mStatus2.setText(mNextAlarm);
            mStatus2.setCompoundDrawablesWithIntrinsicBounds(mAlarmIcon, null, null, null);
        }
    }

    /** {@inheritDoc} */
    public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn) {
        if (DBG) Log.d(TAG, "onRefreshCarrierInfo(" + plmn + ", " + spn + ")");
        updateLayout(mStatus);
    }

    /**
     * Determine the current status of the lock screen given the sim state and other stuff.
     */
    private Status getCurrentStatus(IccCard.State simState) {
        boolean missingAndNotProvisioned = (!mUpdateMonitor.isDeviceProvisioned()
                && simState == IccCard.State.ABSENT);
        if (missingAndNotProvisioned) {
            return Status.SimMissingLocked;
        }

        switch (simState) {
            case ABSENT:
                return Status.SimMissing;
            case NETWORK_LOCKED:
                return Status.SimMissingLocked;
            case NOT_READY:
                return Status.SimMissing;
            case PIN_REQUIRED:
                return Status.SimLocked;
            case PUK_REQUIRED:
                return Status.SimPukLocked;
            case READY:
                return Status.Normal;
            case UNKNOWN:
                return Status.SimMissing;
        }
        return Status.SimMissing;
    }

    
    
    /**
     * Update the layout to match the current status.
     */
    private void updateLayout(Status status) {
        // The emergency call button no longer appears on this screen.
        if (DBG) Log.d(TAG, "updateLayout: status=" + status);

        if (DBG) Log.d(TAG, "the lockscreen type is " + Settings.System.getInt(mContext.getContentResolver() , Settings.System.LOCKSCREEN_TYPE,1 ));

        mEmergencyCallButton.setVisibility(View.GONE); // in almost all cases

        mLockscreenStyle = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_TYPE, 1);
        
        switch (status) {
            case Normal:
            	mCarrierCap = Settings.System.getString(getContext().getContentResolver(), Settings.System.CARRIER_CAP);
            	if (mCarrierCap != null){
            		mCarrier.setText(mCarrierCap);
            	} else {
            		mCarrierCap = getContext().getString(R.string.lockscreen_carrier_default);
            		mCarrier.setText(mCarrierCap);
            	}

                // Empty now, but used for sliding tab feedback
                mScreenLocked.setText("");
                mScreenLocked.setVisibility(View.VISIBLE);
                
                // layout
                
                // Set lock visibility
                resetLockView();
                resolveLockscreenType(mLockscreenStyle);
                
                mEmergencyCallText.setVisibility(View.GONE);
                break;
            case NetworkLocked:
                // The carrier string shows both sim card status (i.e. No Sim Card) and
                // carrier's name and/or "Emergency Calls Only" status
                mCarrier.setText(
                        getCarrierString(
                                mUpdateMonitor.getTelephonyPlmn(),
                                getContext().getText(R.string.lockscreen_network_locked_message)));
                mScreenLocked.setText(R.string.lockscreen_instructions_when_pattern_disabled);

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);
                
                resetLockView();
                resolveLockscreenType(mLockscreenStyle);
                
                mEmergencyCallText.setVisibility(View.GONE);
                break;
            case SimMissing:
                // text
                mCarrier.setText(R.string.lockscreen_missing_sim_message_short);
                mScreenLocked.setText(R.string.lockscreen_missing_sim_instructions);

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);     
                resetLockView();
                resolveLockscreenType(mLockscreenStyle);
                
                mEmergencyCallText.setVisibility(View.VISIBLE);
                // do not need to show the e-call button; user may unlock
                break;
            case SimMissingLocked:
                // text
                mCarrier.setText(
                        getCarrierString(
                                mUpdateMonitor.getTelephonyPlmn(),
                                getContext().getText(R.string.lockscreen_missing_sim_message_short)));
                mScreenLocked.setText(R.string.lockscreen_missing_sim_instructions);

                // layout
                	resetLockView(); // Cannot unlock
                mEmergencyCallText.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.VISIBLE);
                break;
            case SimLocked:
                // text
                mCarrier.setText(
                        getCarrierString(
                                mUpdateMonitor.getTelephonyPlmn(),
                                getContext().getText(R.string.lockscreen_sim_locked_message)));

                // layout
                mScreenLocked.setVisibility(View.INVISIBLE);   
                resetLockView();
                resolveLockscreenType(mLockscreenStyle);
    
                 
                
                mEmergencyCallText.setVisibility(View.GONE);
                break;
            case SimPukLocked:
                // text
                mCarrier.setText(
                        getCarrierString(
                                mUpdateMonitor.getTelephonyPlmn(),
                                getContext().getText(R.string.lockscreen_sim_puk_locked_message)));
                mScreenLocked.setText(R.string.lockscreen_sim_puk_locked_instructions);

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);
                resetLockView(); // cannot unlock
                mEmergencyCallText.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.VISIBLE);
                break;
        }
    }

    static CharSequence getCarrierString(CharSequence telephonyPlmn, CharSequence telephonySpn) {
        if (telephonyPlmn != null && telephonySpn == null) {
            return telephonyPlmn;
        } else if (telephonyPlmn != null && telephonySpn != null) {
            return telephonyPlmn + "|" + telephonySpn;
        } else if (telephonyPlmn == null && telephonySpn != null) {
            return telephonySpn;
        } else {
            return "";
        }
    }

    public void onSimStateChanged(IccCard.State simState) {
        if (DBG) Log.d(TAG, "onSimStateChanged(" + simState + ")");
        mStatus = getCurrentStatus(simState);
        updateLayout(mStatus);
        updateStatusLines();
    }

    void updateConfiguration() {
        Configuration newConfig = getResources().getConfiguration();
        
        Log.d(TAG, "Update configuration is: " + newConfig.toString());
   
        if (newConfig.orientation != mCreationOrientation) {
            mCallback.recreateMe(newConfig);
        } else if (newConfig.hardKeyboardHidden != mKeyboardHidden) {
            mKeyboardHidden = newConfig.hardKeyboardHidden;
            final boolean isKeyboardOpen = mKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
            if (mUpdateMonitor.isKeyguardBypassEnabled() && isKeyboardOpen) {
                mCallback.goToUnlockScreen();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** LOCK ATTACHED TO WINDOW");
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + getResources().getConfiguration());
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.w(TAG, "***** LOCK CONFIG CHANGING", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + newConfig);
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    public boolean needsInput() {
        return false;
    }

    /** {@inheritDoc} */
    public void onPause() {

    }

    /** {@inheritDoc} */
    public void onResume() {
        resetStatusInfo(mUpdateMonitor);
        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
    }

    /** {@inheritDoc} */
    public void cleanUp() {
        mUpdateMonitor.removeCallback(this); // this must be first
        mLockPatternUtils = null;
        mUpdateMonitor = null;
        mCallback = null;
    }

    /** {@inheritDoc} */
    public void onRingerModeChanged(int state) {
        boolean silent = AudioManager.RINGER_MODE_NORMAL != state;
        if (silent != mSilentMode) {
            mSilentMode = silent;
            updateRightTabResources();
        }
    }

    public void onPhoneStateChanged(String newState) {
        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
    }

    // shameless kang of music widgets
    public static Uri getArtworkUri(Context context, long song_id, long album_id) {

        if (album_id < 0) {
            // This is something that is not in the database, so get the album art directly
            // from the file.
            if (song_id >= 0) {
                return getArtworkUriFromFile(context, song_id, -1);
            }
            return null;
        }

       ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);
        if (uri != null) {
            InputStream in = null;
            try {
                in = res.openInputStream(uri);
                return uri;
            } catch (FileNotFoundException ex) {
                // The album art thumbnail does not actually exist. Maybe the user deleted it, or
                // maybe it never existed to begin with.
                return getArtworkUriFromFile(context, song_id, album_id);
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ex) {
                }
            }
        }
        return null;
    }

   private static Uri getArtworkUriFromFile(Context context, long songid, long albumid) {

        if (albumid < 0 && songid < 0) {
            return null;
        }

        try {
            if (albumid < 0) {
                Uri uri = Uri.parse("content://media/external/audio/media/" + songid + "/albumart");
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) {
                    return uri;
               }
            } else {
                Uri uri = ContentUris.withAppendedId(sArtworkUri, albumid);
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) {
                    return uri;
                }
            }
        } catch (FileNotFoundException ex) {
        }
        return null;
    }
   
   @Override
   public void OnSenseLikeSelectorGrabbedStateChanged(View v, int GrabState) {
   // TODO Auto-generated method stub
	   mCallback.pokeWakelock();

   }
   @Override
   public void onSenseLikeSelectorTrigger(View v, int Trigger) {
   // TODO Auto-generated method stub
	   
	   Vibrator vibe = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
           long[] pattern = {
                           0, 100
	};
	   switch(Trigger){
	   case OnSenseLikeSelectorTriggerListener.LOCK_ICON_SHORTCUT_ONE_TRIGGERED:
                 
		   vibe.vibrate(pattern, -1);
		   mCustomApps[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           getContext().startActivity(mCustomApps[0]);
           mCallback.goToUnlockScreen();

		   break;
	   case OnSenseLikeSelectorTriggerListener.LOCK_ICON_SHORTCUT_TWO_TRIGGERED:
		   vibe.vibrate(pattern, -1);
		   mCustomApps[1].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           getContext().startActivity(mCustomApps[1]);
           mCallback.goToUnlockScreen();
		   break;
	   case OnSenseLikeSelectorTriggerListener.LOCK_ICON_SHORTCUT_THREE_TRIGGERED:
		   vibe.vibrate(pattern, -1);
		   mCustomApps[2].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           getContext().startActivity(mCustomApps[2]);
           mCallback.goToUnlockScreen();
		   break;
	   case OnSenseLikeSelectorTriggerListener.LOCK_ICON_SHORTCUT_FOUR_TRIGGERED:
		   vibe.vibrate(pattern, -1);
		   mCustomApps[3].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           getContext().startActivity(mCustomApps[3]);
           mCallback.goToUnlockScreen();
		   break;
	   case OnSenseLikeSelectorTriggerListener.LOCK_ICON_TRIGGERED:
		   mCallback.goToUnlockScreen();
		   break;
	   }
	   
	
	   
   }
   
   /**
    * Function used to resolve the lockscreen type 
    * to show to the user. Uses the settings.system
    * variables to determine the type. 
    * 
    * @param settingsType Lockscreen type number
    */
   private void resolveLockscreenType(final int settingsType){
	   
	   switch(settingsType)
	   {
	   case Settings.System.USE_HONEYCOMB_LOCKSCREEN:
		   mUnlockRing.setVisibility(View.VISIBLE);
		   break;
	   case Settings.System.USE_ROTARY_LOCKSCREEN:
		   mRotarySelector.setVisibility(View.VISIBLE); 
		   break;
		   
	   case Settings.System.USE_SENSELIKE_LOCKSCREEN: 
		   mSenseRingSelector.setVisibility(View.VISIBLE);
		   break;
		   
	   case Settings.System.USE_TAB_LOCKSCREEN: 
		   mSelector.setVisibility(View.VISIBLE);
		   break;
	   case Settings.System.USE_HCC_LOCKSCREEN: 
		   mCircularSelector.setVisibility(View.VISIBLE);
		   break;
		   
	   default: 
		   mSelector.setVisibility(View.VISIBLE);
		   break;
	   
	   
	   }
	   
  
	   
   }
   
   private void setupSenseLikeRingShortcuts(){
	   
	   int numapps = 0;
	   Intent intent = new Intent();
	   PackageManager pm = mContext.getPackageManager();
	   mCustomApps = new Intent[4];
	   
	   FastBitmapDrawable[] shortcutsicons;
	   for(int i = 0; i < mCustomQuandrants.length ; i++){
		   if(mCustomQuandrants[i] != null){
			   numapps++;
		   }
		}
	   
	   if(numapps == 0){
		   mCustomApps = mSenseRingSelector.setDefaultIntents();
		   numapps = 4;
	   }else for(int i = 0; i < numapps ; i++){
			  
				try{
					intent = Intent.parseUri(mCustomQuandrants[i], 0);
				}catch (java.net.URISyntaxException ex) {
					Log.w(TAG, "Invalid hotseat intent: " + mCustomQuandrants[i]);
		               // bogus; leave intent=null
		        }
				
				 
				 ResolveInfo bestMatch = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
		         List<ResolveInfo> allMatches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
		         
		         if (DBG) { 
		                Log.d(TAG, "Best match for intent: " + bestMatch);
		                Log.d(TAG, "All matches: ");
		                for (ResolveInfo ri : allMatches) {
		                    Log.d(TAG, "  --> " + ri);
		                }
		            }
		         
		         ComponentName com = new ComponentName(
	                        bestMatch.activityInfo.applicationInfo.packageName,
	                        bestMatch.activityInfo.name);
		         
		         mCustomApps[i] = new Intent(Intent.ACTION_MAIN).setComponent(com);
		
		   
		   
		   
	   }
	   
	   shortcutsicons = new FastBitmapDrawable[numapps];
	   
	  float iconScale =0.80f;
	  
	   for(int i = 0; i < numapps ; i++){
		   try {
           	
			   shortcutsicons[i] = (FastBitmapDrawable) pm.getActivityIcon( mCustomApps[i]);
			   shortcutsicons[i] = (FastBitmapDrawable) scaledDrawable(shortcutsicons[i], mContext ,iconScale);
           } catch (ArrayIndexOutOfBoundsException ex) {
               Log.w(TAG, "Missing shortcut_icons array item #" + i);
               shortcutsicons[i] = null;
           } catch (PackageManager.NameNotFoundException e) {
           	//Do-Nothing
           }
	   }
	   
	   
       if(numapps == 4){
    	 
    	 mSenseRingSelector.setToTwoShortcuts(false);
    	 mSenseRingSelector.setShortCutsDrawables(shortcutsicons[0], shortcutsicons[1], shortcutsicons[2], shortcutsicons[3]);  
       }
       else if(numapps == 2) {
    	   mSenseRingSelector.setToTwoShortcuts(true);
    	   mSenseRingSelector.setShortCutsDrawables(shortcutsicons[0], null, null,shortcutsicons[2]);
       }
	   
   }
   
   static Drawable scaledDrawable(Drawable icon,Context context, float scale) {
		final Resources resources=context.getResources();
		int sIconHeight= (int) resources.getDimension(android.R.dimen.app_icon_size);
		int sIconWidth = sIconHeight;

		int width = sIconWidth;
		int height = sIconHeight;
		Bitmap original;
		try{
		    original= Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		} catch (OutOfMemoryError e) {
		   return icon;
		}
		Canvas canvas = new Canvas(original);
		canvas.setBitmap(original);
		icon.setBounds(0,0, width, height);
		icon.draw(canvas);
		try{
		    Bitmap endImage=Bitmap.createScaledBitmap(original, (int)(width*scale), (int)(height*scale), true);
		    original.recycle();
		    return new FastBitmapDrawable(endImage);
		} catch (OutOfMemoryError e) {
		    return icon;
		}
	    }
   
   private void resetLockView(){
	   

   	mCircularSelector.setVisibility(View.GONE);
   	mSelector.setVisibility(View.GONE);
   	mRotarySelector.setVisibility(View.GONE);
   	mUnlockRing.setVisibility(View.GONE);
   	mSenseRingSelector.setVisibility(View.GONE);
	   
   }
}
