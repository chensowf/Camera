package com.example.administrator.camera;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.Permission;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public class CameraActivity extends AppCompatActivity implements IVideoControl.PlaySeekTimeListener,
        IVideoControl.PlayStateListener, ICamera.TakePhotoListener, SensorEventListener, ICamera.CameraReady {

    public static final String ACTION_EXIT = "action_exit";

    /**
     * 摄像头模式
     */
    public final static int CAMERA_MODE = 0;
    /**
     * 视频播放器模式
     */
    public final static int VIDEO_MODE = 1;

    /**
     * 视频最长的时长是15s
     */
    private final static int VIDEO_MAX_TIME = 15;

    /**
     * 视频播放模式
     */
    public final static int VIDEO_PLAY_MODE = 0;

    /**
     * 视频录像模式
     */
    public final static int VIDEO_RECORD_MODE = 1;

    /**
     * 拍照模式
     */
    public final static int VIDEO_TAKE_PHOTO = 2;

    /**
     * 当前面板是预览状态
     */
    public final static int TEXTURE_PREVIEW_STATE = 0;

    /**
     * 当前面板是录像状态
     */
    public final static int TEXTURE_RECORD_STATE = 1;

    /**
     * 当前面板是图片状态
     */
    public final static int TEXTURE_PHOTO_STATE = 2;

    /**
     * 当前面板是视频播放状态
     */

    public final static int TEXTURE_PLAY_STATE = 3;

    /**
     * 当前是摄像头模式还是视频播放模式
     */
    private int MODE;

    /**
     * 当前的模式，默认为拍照模式
     */
    private int NOW_MODE = VIDEO_TAKE_PHOTO;

    /**
     * 当前的显示面板状态
     */
    private int TEXTURE_STATE = TEXTURE_PREVIEW_STATE;

    @BindView(R.id.video_menu)
    AutoLocateHorizontalView mAutoLocateHorizontalView;

    @BindView(R.id.video_texture)
    AutoFitTextureView textureView;

    @BindView(R.id.video_close)
    ImageButton mCloseImageButton;

    @BindView(R.id.video_time)
    TextView mTimeTextView;

    @BindView(R.id.video_switch_camera)
    ImageButton mSwitchCameraButton;

    @BindView(R.id.video_play)
    ImageButton mPlayImageButton;

    @BindView(R.id.video_delete)
    ImageButton mDeleteImageButton;

    @BindView(R.id.video_record)
    ImageButton mRecordImageButton;

    @BindView(R.id.video_save)
    ImageButton mSaveImageButton;

    @BindView(R.id.video_mine_play)
    ImageButton mMiniPlayImageButton;

    @BindView(R.id.video_seek_bar)
    SeekBar mVideoSeekBar;

    @BindView(R.id.video_seek_time)
    TextView mVideoSeekTimeTextView;

    @BindView(R.id.video_record_seek_bar)
    SeekBar mVideoRecordSeekBar;

    @BindView(R.id.video_hint_text)
    TextView mVideoHintText;

    @BindView(R.id.video_switch_flash)
    ImageButton mFlashSwitch;

    @BindView(R.id.video_photo)
    ImageView mPhotoImageView;
    @BindView(R.id.video_scale_bar_layout)
    RelativeLayout mSeekBarLayout;
    @BindView(R.id.video_scale)
    SeekBar mScaleSeekBar;
    @BindView(R.id.video_fouces)
    ImageView mFoucesImage;

    private CameraHelper cameraHelper;

    private ICamera.CameraType mNowCameraType = ICamera.CameraType.BACK;

    private VideoPlayer mVideoPlayer;

    private MenuAdapter mMenuAdapter;

    /**
     * 视频播放时模式下的视频路径
     */
    private String mVideoPath;

    /**
     * 录像保存或者图片保存的路径
     */
    private String mMediaPath;


    private LocalBroadcastManager mLocalBroadcastManager;
    private ExitBroadcastReceiver mExitBroadcastReceiver;

    private RxPermissions mRxPermissions;

    private CameraTouch mCameraTouch;

    /**
     * 视频播放模式控件隐藏
     */
    private Runnable mHindViewRunnable = new Runnable() {
        @Override
        public void run() {
            hindPlayView();
        }
    };

    /**
     * 3s后隐藏的runnable
     */
    private Runnable SeekBarLayoutRunnalbe = new Runnable() {
        @Override
        public void run() {
            mSeekBarLayout.setVisibility(View.GONE);
        }
    };

    private Runnable mImageFoucesRunnable = new Runnable() {
        @Override
        public void run() {
            mFoucesImage.setVisibility(View.GONE);
        }
    };

    private boolean isNoPremissionPause = false;

    private FoucesAnimation mFoucesAnimation;

    private class FoucesAnimation extends Animation {

        private int width = dip2px(CameraActivity.this, 150);
        private int W = dip2px(CameraActivity.this, 65);

        private int oldMarginLeft;
        private int oldMarginTop;

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {

            FrameLayout.LayoutParams layoutParams =
                    (FrameLayout.LayoutParams) mFoucesImage.getLayoutParams();
            int w = (int) (width * (1 - interpolatedTime));
            if (w < W) {
                w = W;
            }
            layoutParams.width = w;
            layoutParams.height = w;
            if(w == W) {
                mFoucesImage.setLayoutParams(layoutParams);
                return;
            }
            layoutParams.leftMargin = oldMarginLeft - (w/2);
            layoutParams.topMargin = oldMarginTop + (w/8);
            mFoucesImage.setLayoutParams(layoutParams);
        }

        public void setOldMargin(int oldMarginLeft, int oldMarginTop)
        {
            this.oldMarginLeft = oldMarginLeft;
            this.oldMarginTop = oldMarginTop;
            removeImageFoucesRunnable();
            imageFoucesDelayedHind();
        }
    }

        /**
         * 播放视频启动的模式
         *
         * @param activity
         * @param path
         */
        public static void startCameraActivityForPlayVideo(Activity activity, String path) {
            Intent intent = new Intent(activity, CameraActivity.class);
            intent.putExtra("mode", VIDEO_MODE);
            intent.putExtra("videoPath", path);
            activity.startActivity(intent);
        }

        /**
         * 摄像头录像和拍照启动的模式
         *
         * @param activity
         * @param requestCode
         */
        public static void startCamearActivityForCamear(Activity activity, int requestCode) {
            Intent intent = new Intent(activity, CameraActivity.class);
            intent.putExtra("mode", CAMERA_MODE);
            activity.startActivityForResult(intent, requestCode);
        }


        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            mRxPermissions = new RxPermissions(this);
            ButterKnife.bind(this);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent));
            }

            mVideoPlayer = new VideoPlayer();
            //设置时间戳回调
            mVideoPlayer.setPlaySeekTimeListener(this);

            MODE = getIntent().getIntExtra("mode", CAMERA_MODE);
            if (MODE == CAMERA_MODE)   //摄像头模式
            {
                initCameraMode();
            } else if (MODE == VIDEO_MODE)    //视频播放模式
            {
                mVideoPath = getIntent().getStringExtra("videoPath");
                initVideoMode();
            }

            /**
             * 退出app的监听
             */
            mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
            IntentFilter intentFilter = new IntentFilter(ACTION_EXIT);
            mExitBroadcastReceiver = new ExitBroadcastReceiver();
            mLocalBroadcastManager.registerReceiver(mExitBroadcastReceiver, intentFilter);

            mFoucesAnimation = new FoucesAnimation();
        }

        /**
         * 显示播放界面的控件出来
         */
        private void showPlayView() {
            showVideoPlaySeekBar();
            mMiniPlayImageButton.setVisibility(View.VISIBLE);
            mPlayImageButton.setVisibility(View.VISIBLE);
            mCloseImageButton.setVisibility(View.VISIBLE);
            mVideoSeekTimeTextView.setVisibility(View.VISIBLE);
        }

        /**
         * 隐藏播放界面的控件出来
         */
        private void hindPlayView() {
            hindVideoPlaySeekBar();
            mMiniPlayImageButton.setVisibility(View.GONE);
            mPlayImageButton.setVisibility(View.GONE);
            mCloseImageButton.setVisibility(View.GONE);
            mVideoSeekTimeTextView.setVisibility(View.GONE);
        }

        /**
         * 初始化摄像头模式
         */
        private void initCameraMode() {

            if(ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                    ||
                    ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    ||
                    ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ||
                    ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    )
            {
                isNoPremissionPause = true;
            }
            mRxPermissions.requestEach(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .subscribe(new Consumer<Permission>() {
                        @Override
                        public void accept(Permission permission) {
                            if (permission.granted && permission.name.equals(Manifest.permission.CAMERA)) {
                                initCamera(mNowCameraType);
                            }
                        }
                    });

            cameraHelper = new CameraHelper(this);
            cameraHelper.setTakePhotoListener(this);
            cameraHelper.setCameraReady(this);
            mVideoPlayer.setLoopPlay(true);

            List<String> menus = new ArrayList<>();
            menus.add("拍照");
            menus.add("录像");
            mMenuAdapter = new MenuAdapter(this, menus, mAutoLocateHorizontalView);
            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
            linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            mAutoLocateHorizontalView.setLayoutManager(linearLayoutManager);
            mAutoLocateHorizontalView.setAdapter(mMenuAdapter);
            mAutoLocateHorizontalView.setOnSelectedPositionChangedListener(new AutoLocateHorizontalView.OnSelectedPositionChangedListener() {
                @Override
                public void selectedPositionChanged(int pos) {
                    if (pos == 0) {
                        NOW_MODE = VIDEO_TAKE_PHOTO;   //拍照模式
                        cameraHelper.setCameraState(ICamera.CameraMode.TAKE_PHOTO);
                        mVideoHintText.setText("点击拍照");
                    }
                    if (pos == 1) {
                        NOW_MODE = VIDEO_RECORD_MODE;  //录像模式
                        cameraHelper.setCameraState(ICamera.CameraMode.RECORD_VIDEO);
                        mVideoHintText.setText("点击录像");
                    }
                }
            });
            mCameraTouch = new CameraTouch();
            mAutoLocateHorizontalView.setOnTouchListener(new View.OnTouchListener() {

                private long mClickOn;
                private float mLastX;
                private float mLastY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            if (event.getPointerCount() == 1) {
                                mClickOn = System.currentTimeMillis();
                                mLastX = event.getX();
                                mLastY = event.getY();
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            if (event.getPointerCount() == 1) {
                                if((System.currentTimeMillis() - mClickOn) < 500)
                                {
                                    moveFouces((int) event.getX(), (int) event.getY());
                                }
                            }
                            break;
                        case MotionEvent.ACTION_POINTER_DOWN:
                            mCameraTouch.onScaleStart(event);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            if (event.getPointerCount() == 2) {
                                mCameraTouch.onScale(event);
                                return true;
                            }
                            else
                            {
                                float x = event.getX()-mLastX;
                                float y = event.getY()-mLastY;
                                if(Math.abs(x) >= 10 || Math.abs(y) >= 10) {
                                    mClickOn = 0;
                                }
                            }
                            break;
                        case MotionEvent.ACTION_POINTER_UP:
                            mCameraTouch.onScaleEnd(event);
                            return true;
                    }
                    return false;
                }
            });

            textureView.setOnTouchListener(new View.OnTouchListener() {

                private long mClickOn;
                private float mLastX;
                private float mLastY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (TEXTURE_STATE == TEXTURE_PLAY_STATE)
                        return true;
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            if (event.getPointerCount() == 1) {
                                mClickOn = System.currentTimeMillis();
                                mLastX = event.getX();
                                mLastY = event.getY();
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            if (event.getPointerCount() == 1) {
                                if((System.currentTimeMillis() - mClickOn) < 500)
                                {
                                    moveFouces((int) event.getX(), (int) event.getY());
                                }
                            }
                            break;
                        case MotionEvent.ACTION_POINTER_DOWN:
                            mCameraTouch.onScaleStart(event);
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (event.getPointerCount() == 2){
                                mCameraTouch.onScale(event);
                             }
                            else
                            {
                                float x = event.getX()-mLastX;
                                float y = event.getY()-mLastY;
                                if(Math.abs(x) >= 10 || Math.abs(y) >= 10) {
                                    mClickOn = 0;
                                }
                            }
                            break;
                        case MotionEvent.ACTION_POINTER_UP:
                            mCameraTouch.onScaleEnd(event);
                            break;
                    }
                    return true;
                }
            });

            cutPadding();
            registerSensor();
            initScaleSeekbar();
        }

        public int dip2px(Context context,float dipValue) {

            return (int) (dipValue * context.getResources().getDisplayMetrics().density + 0.5f);
        }

        /**
         * 移动焦点图标
         * @param x
         * @param y
         */
        private void moveFouces(int x, int y) {
            mFoucesImage.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams layoutParams
                    = (FrameLayout.LayoutParams) mFoucesImage.getLayoutParams();
            mFoucesImage.setLayoutParams(layoutParams);
            mFoucesAnimation.setDuration(500);
            mFoucesAnimation.setRepeatCount(0);
            mFoucesAnimation.setOldMargin(x, y);
            mFoucesImage.startAnimation(mFoucesAnimation);
            cameraHelper.requestFocus(x,y);
        }

        /**
         * 初始化视频播放模式
         */
        private void initVideoMode() {
            hindMenu();
            hindSwitchCamera();
            hindVideoRecordSeekBar();
            mCloseImageButton.setVisibility(View.GONE);
            mCloseImageButton.setImageResource(R.drawable.ic_video_close);
            mVideoPlayer.setPlayStateListener(this);
            mRecordImageButton.setVisibility(View.GONE);
            mVideoHintText.setVisibility(View.GONE);
            textureView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {     //单机屏幕显示出控件
                    if (mMiniPlayImageButton.getVisibility() == View.VISIBLE) {
                        hindPlayView();
                    } else {
                        showPlayView();
                        textureView.postDelayed(mHindViewRunnable, 3000);
                    }
                }
            });

            mVideoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                private int progress;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        this.progress = progress;
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    //触摸进度条取消几秒后隐藏的事件
                    textureView.removeCallbacks(mHindViewRunnable);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    mVideoPlayer.seekTo(progress);
                    textureView.postDelayed(mHindViewRunnable, 3000);
                }
            });
        }

        /**
         * 重新设置录像的进度条样式
         */
        private void cutPadding() {
            Point point = new Point();
            getWindowManager().getDefaultDisplay().getSize(point);
            int width = point.x;
            int padding = mVideoRecordSeekBar.getPaddingLeft();
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mVideoRecordSeekBar.getLayoutParams();
            layoutParams.width = width + padding;
            mVideoRecordSeekBar.setLayoutParams(layoutParams);
            mVideoRecordSeekBar.setPadding(0, 0, 0, 0);
        }

        /**
         * 初始化摄像头
         *
         * @param cameraType
         */
        private void initCamera(ICamera.CameraType cameraType) {
            if (cameraHelper == null)
                return;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            cameraHelper.setTextureView(textureView);
            cameraHelper.openCamera(cameraType);
        }

        @Override
        protected void onResume() {
            super.onResume();
            if (cameraHelper != null)
                cameraHelper.startBackgroundThread();

            if (textureView.isAvailable()) {
                if (MODE == CAMERA_MODE) {
                    if (TEXTURE_STATE == TEXTURE_PREVIEW_STATE)  //预览状态
                        initCamera(mNowCameraType);
                    else if (TEXTURE_STATE == TEXTURE_PLAY_STATE)   //视频播放状态
                        mVideoPlayer.play();
                    mVideoPlayer.setVideoPlayWindow(new Surface(textureView.getSurfaceTexture()));
                }
            } else {
                textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                        if (MODE == CAMERA_MODE) {
                            if (TEXTURE_STATE == TEXTURE_PREVIEW_STATE)  //预览状态
                                initCamera(mNowCameraType);
                            else if (TEXTURE_STATE == TEXTURE_PLAY_STATE)   //视频播放状态
                                mVideoPlayer.play();
                            mVideoPlayer.setVideoPlayWindow(new Surface(textureView.getSurfaceTexture()));
                        } else if (MODE == VIDEO_MODE) {
                            mVideoPlayer.setVideoPlayWindow(new Surface(textureView.getSurfaceTexture()));
                            Log.e("videoPath", "path:" + mVideoPath);
                            mVideoPlayer.setDataSourceAndPlay(mVideoPath);
                            isPlaying = true;
                            TEXTURE_STATE = TEXTURE_PLAY_STATE;  //视频播放状态
                        }
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                        return true;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                    }
                });
            }
        }

        @Override
        protected void onPause() {
            super.onPause();
            if(isNoPremissionPause) {
                isNoPremissionPause = false;
                return;
            }
            Log.e("camera", "mode:" + MODE);
            if (MODE == CAMERA_MODE) {
                if (TEXTURE_STATE == TEXTURE_PREVIEW_STATE) {
                    cameraHelper.closeCamera();
                    cameraHelper.stopBackgroundThread();
                } else if (TEXTURE_STATE == TEXTURE_PLAY_STATE) {
                    mVideoPlayer.pause();
                }
            }
        }

        /**
         * 切换摄像头
         */
        @OnClick(R.id.video_switch_camera)
        public void switchCamera() {
            if (mNowCameraType == ICamera.CameraType.FRONT) {
                cameraHelper.switchCamera(ICamera.CameraType.BACK);
                mNowCameraType = ICamera.CameraType.BACK;
            } else {
                cameraHelper.switchCamera(ICamera.CameraType.FRONT);
                mNowCameraType = ICamera.CameraType.FRONT;
            }
            mCameraTouch.resetScale();
        }

        private boolean isRecording = false;

        /**
         * 视频录制
         */
        private boolean isRecordClick = false;

        @OnClick(R.id.video_record)
        public void recordVideoOrTakePhoto() {
            if (isRecordClick)
                return;
            isRecordClick = true;
            //录像模式
            if (NOW_MODE == VIDEO_RECORD_MODE) {
                if (!isRecording) {

                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    mMediaPath = getVideoFilePath();
                    isRecording = cameraHelper.startVideoRecord(mMediaPath, MediaRecorder.OutputFormat.MPEG_4);
                    if (isRecording) {
                        mRecordImageButton.setImageResource(R.drawable.ic_recording);
                        hindSwitchCamera();
                        recordCountDown();
                        hindMenu();
                  //      mVideoHintText.setVisibility(View.GONE);
                        mVideoHintText.setText("点击停止");
                        mCloseImageButton.setVisibility(View.GONE);
                        mFlashSwitch.setVisibility(View.GONE);
                        TEXTURE_STATE = TEXTURE_RECORD_STATE;
                    }
                } else {
                    stopRecordCountTime();
                    isRecording = false;
                    cameraHelper.stopVideoRecord();

                    mRecordImageButton.setImageResource(R.drawable.ic_record);
                    mRecordImageButton.setVisibility(View.GONE);
                    mCloseImageButton.setVisibility(View.VISIBLE);
                    mVideoHintText.setVisibility(View.GONE);
                    showRecordEndView();
                    hindVideoRecordSeekBar();
                    playVideo();
                }
            }

            //拍照模式
            if (NOW_MODE == VIDEO_TAKE_PHOTO) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                mMediaPath = getPhotoFilePath();
                if (cameraHelper.takePhone(mMediaPath, ICamera.MediaType.JPEG)) {

                }
            }

            isRecordClick = false;
        }

        /**
         * 隐藏切换菜单
         */
        private void hindMenu() {
            mAutoLocateHorizontalView.setVisibility(View.GONE);
        }

        /**
         * 显示切换菜单
         */
        private void showMeun() {
            mAutoLocateHorizontalView.setVisibility(View.VISIBLE);
        }

        /**
         * 隐藏切换摄像头按钮
         */
        private void hindSwitchCamera() {
            mSwitchCameraButton.setVisibility(View.GONE);
        }

        /**
         * 显示切换摄像头按钮
         */
        private void showSwitchCamera() {
            mSwitchCameraButton.setVisibility(View.VISIBLE);
        }

        /**
         * 显示视频录像的进度条
         */
        private void showVideoRecordSeekBar() {
            mVideoRecordSeekBar.setVisibility(View.VISIBLE);
        }

        /**
         * 隐藏视频录像的进度条
         */
        private void hindVideoRecordSeekBar() {
            mVideoRecordSeekBar.setVisibility(View.GONE);
            mVideoRecordSeekBar.setProgress(0);
        }

        /**
         * 中止计时
         */
        private void stopRecordCountTime() {
            if (mDisposable != null && !mDisposable.isDisposed())
                mDisposable.dispose();
            mDisposable = null;
            mVideoSeekTimeTextView.setVisibility(View.GONE);
        }

        /**
         * 录像倒计时终止器
         */
        private Disposable mDisposable;

        /**
         * 录像时长倒计时
         */
        private void recordCountDown() {
            mTimeTextView.setVisibility(View.VISIBLE);
            showVideoRecordSeekBar();
            final int count = 15;
            mDisposable = Observable.interval(1, 1, TimeUnit.SECONDS)
                    .take(count + 1)
                    .map(new Function<Long, Long>() {
                        @Override
                        public Long apply(Long aLong) {
                            return count - aLong;
                        }
                    }).observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<Long>() {
                        @Override
                        public void accept(Long aLong) {
                            long time = 16 - aLong;
                            if (time < 10)
                                mTimeTextView.setText("0:0" + String.valueOf(time));
                            else
                                mTimeTextView.setText("0:" + String.valueOf(time));
                            mVideoRecordSeekBar.setProgress((int) time);
                            if (time == VIDEO_MAX_TIME) {
                                mTimeTextView.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        recordVideoOrTakePhoto();
                                        hindVideoRecordSeekBar();
                                    }
                                }, 300);

                            }
                        }
                    });
        }

        /**
         * 显示录像完成后底部两个按钮
         */
        private void showRecordEndView() {
            mSaveImageButton.setVisibility(View.VISIBLE);
            mDeleteImageButton.setVisibility(View.VISIBLE);
        }

        /**
         * 隐藏录像完成后底部两个按钮
         */
        private void hindRecordEndView() {
            mSaveImageButton.setVisibility(View.GONE);
            mDeleteImageButton.setVisibility(View.GONE);
        }

        /**
         * 关闭摄像头
         */
        private void closeCamera() {
            mRecordImageButton.setClickable(false);
            cameraHelper.closeCamera();
            cameraHelper.stopBackgroundThread();
        }

        /**
         * 播放视频
         */
        public void playVideo() {
            closeCamera();
            if (mMediaPath != null && mVideoPlayer != null) {
                mVideoPlayer.setDataSourceAndPlay(mMediaPath);
                isPlaying = true;

                TEXTURE_STATE = TEXTURE_PLAY_STATE;  //视频播放状态
            }
        }

        private boolean isPlaying = false;

        /**
         * 暂停或者播放视频
         */
        @OnClick({R.id.video_mine_play, R.id.video_play})
        public void playOrPause() {
            if (!isPlaying) {
                mMiniPlayImageButton.setImageResource(R.drawable.ic_pause);
                mPlayImageButton.setImageResource(R.drawable.ic_pause);
                mPlayImageButton.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mPlayImageButton.setVisibility(View.GONE);
                    }
                }, 1000);
                mVideoPlayer.play();
                isPlaying = true;
            } else {
                mMiniPlayImageButton.setImageResource(R.drawable.ic_play);
                mPlayImageButton.setImageResource(R.drawable.ic_play);
                isPlaying = false;
                mVideoPlayer.pause();
            }
        }

        @OnClick(R.id.video_delete)
        public void deleteVideoOrPicture() {
            if (TEXTURE_STATE == TEXTURE_PLAY_STATE) {
                mVideoPlayer.stop();
                cameraHelper.startBackgroundThread();
                cameraHelper.openCamera(mNowCameraType);
                mCameraTouch.resetScale();  //重新打开摄像头重置一下放大倍数
                File file = new File(mMediaPath);
                if (file.exists())
                    file.delete();
                mVideoHintText.setText("点击录像");
            } else if (TEXTURE_STATE == TEXTURE_PHOTO_STATE) {
                File file = new File(mMediaPath);
                if (file.exists())
                    file.delete();
                /* cameraHelper.resumePreview();*/
                textureView.setVisibility(View.VISIBLE);
                mPhotoImageView.setVisibility(View.GONE);
                mVideoHintText.setText("点击拍照");
            }

            TEXTURE_STATE = TEXTURE_PREVIEW_STATE;

            hindRecordEndView();
            showSwitchCamera();
            showMeun();
            mRecordImageButton.setVisibility(View.VISIBLE);
            mTimeTextView.setVisibility(View.GONE);
            mTimeTextView.setText("0:00");
            mVideoHintText.setVisibility(View.VISIBLE);
            mFlashSwitch.setVisibility(View.VISIBLE);
        }

        /**
         * 发送视频或者图片
         */
        @OnClick(R.id.video_save)
        public void saveVideoOrPhoto() {
            final Intent data;
            data = new Intent();
            data.putExtra("path", mMediaPath);
            if (NOW_MODE == VIDEO_TAKE_PHOTO)
                data.putExtra("mediaType", "image");
            else if (NOW_MODE == VIDEO_RECORD_MODE) {
                data.putExtra("mediaType", "video");
            }

            setResult(RESULT_OK, data);
            finish();

            saveMedia(new File(mMediaPath));
        }

        /**
         * 刷新相册
         *
         * @param mediaFile
         */
        private void saveMedia(File mediaFile) {
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri uri = Uri.fromFile(mediaFile);
            intent.setData(uri);
            sendBroadcast(intent);
        }

        public void close(View view) {
            finish();
        }

        /**
         * 视频录像保存的路径
         *
         * @return
         */
        private String getVideoFilePath() {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsoluteFile();
            return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                    + System.currentTimeMillis() + ".mp4";
        }

        /**
         * 图片拍照的路径
         *
         * @return
         */
        private String getPhotoFilePath() {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsoluteFile();
            return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                    + System.currentTimeMillis() + ".jpeg";
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (mSensorManager != null)
                mSensorManager.unregisterListener(this);
            if (MODE == CAMERA_MODE) {
                //如果正在录像，就停止并且删除
                if (TEXTURE_STATE == TEXTURE_RECORD_STATE) {
                    if (mDisposable != null && !mDisposable.isDisposed())
                        mDisposable.dispose();
                    cameraHelper.stopVideoRecord();
                    closeCamera();
                    deleteVideoOrPicture();
                }

                if (TEXTURE_STATE == TEXTURE_PHOTO_STATE) {
                    closeCamera();
                    deleteVideoOrPicture();
                }
            }
            if (isPlaying)
                mVideoPlayer.stop();
            mVideoPlayer.destroy();
            if (cameraHelper != null)
                cameraHelper.destroy();
            if (mExitBroadcastReceiver != null)
                mLocalBroadcastManager.unregisterReceiver(mExitBroadcastReceiver);
        }

        @Override
        public void onSeekTime(int allTime, final int time) {
            if (mVideoSeekBar.getVisibility() != View.VISIBLE)
                return;
            if (mVideoSeekBar.getMax() != allTime)
                mVideoSeekBar.setMax(allTime);
            mVideoSeekBar.setProgress(time);
            mVideoSeekTimeTextView.post(new Runnable() {
                @Override
                public void run() {
                    float t = (float) time / 1000.0f;
                    mVideoSeekTimeTextView.setText(secToTime(Math.round(t)));
                }
            });
        }

        /**
         * 显示视频播放进度条
         */
        private void showVideoPlaySeekBar() {
            mVideoSeekBar.setVisibility(View.VISIBLE);
        }

        /**
         * 隐藏视频播放进度条
         */
        private void hindVideoPlaySeekBar() {
            mVideoSeekBar.setVisibility(View.GONE);
        }

        @Override
        public void onStartListener(int width, int height) {
            textureView.setVideoAspectRatio(width, height);
            mMiniPlayImageButton.setImageResource(R.drawable.ic_pause);
            mPlayImageButton.setImageResource(R.drawable.ic_pause);
        }

        @Override
        public void onCompletionListener() {
            isPlaying = false;
            mMiniPlayImageButton.setImageResource(R.drawable.ic_play);
            mPlayImageButton.setImageResource(R.drawable.ic_play);
            mPlayImageButton.setVisibility(View.VISIBLE);
        }

        /**
         * 整数s转 xx:xx:xx
         *
         * @param time
         * @return
         */
        private String secToTime(int time) {
            String timeStr;
            int hour;
            int minute;
            int second;
            if (time <= 0)
                return "00:00";
            else {
                minute = time / 60;
                if (minute < 60) {
                    second = time % 60;
                    timeStr = unitFormat(minute) + ":" + unitFormat(second);
                } else {
                    hour = minute / 60;
                    if (hour > 99)
                        return "99:59:59";
                    minute = minute % 60;
                    second = time - hour * 3600 - minute * 60;
                    timeStr = unitFormat(hour) + ":" + unitFormat(minute) + ":" + unitFormat(second);
                }
            }
            return timeStr;
        }

        private String unitFormat(int i) {
            String retStr;
            if (i >= 0 && i < 10)
                retStr = "0" + Integer.toString(i);
            else
                retStr = "" + i;
            return retStr;
        }

        @Override
        public void onTakePhotoFinish(final File file, final int photoRotation, final int width, final int height) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hindSwitchCamera();
                    hindMenu();
                    showRecordEndView();
                    mFlashSwitch.setVisibility(View.GONE);
                    mRecordImageButton.setVisibility(View.GONE);
                    mVideoHintText.setVisibility(View.GONE);
                    TEXTURE_STATE = TEXTURE_PHOTO_STATE;
                    textureView.setVisibility(View.GONE);
                    mPhotoImageView.setImageURI(getUriFromFile(CameraActivity.this,file));
                    mPhotoImageView.setVisibility(View.VISIBLE);
                }
            });
        }

        public Uri getUriFromFile(Context context, File file){
            if (Build.VERSION.SDK_INT >= 24) {
                return FileProvider.getUriForFile(context,getPackageName()+".fileprovider", file);
            } else {
                return Uri.fromFile(file);
            }
        }

        @OnClick(R.id.video_switch_flash)
        public void flashSwitch() {
            Object o = mFlashSwitch.getTag();
            if (o == null || ((int) o) == 0) {
                mFlashSwitch.setImageResource(R.drawable.flash_auto);
                mFlashSwitch.setTag(1);
                cameraHelper.flashSwitchState(ICamera.FlashState.AUTO);
            } else if (((int) o) == 1) {
                mFlashSwitch.setImageResource(R.drawable.flash_open);
                mFlashSwitch.setTag(2);
                cameraHelper.flashSwitchState(ICamera.FlashState.OPEN);
            } else {
                mFlashSwitch.setImageResource(R.drawable.flash_close);
                mFlashSwitch.setTag(0);
                cameraHelper.flashSwitchState(ICamera.FlashState.CLOSE);
            }
        }

        private void initScaleSeekbar() {
            mScaleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float scale = (float) progress / (float) seekBar.getMax() * cameraHelper.getMaxZoom();
                        cameraHelper.cameraZoom(scale);
                        mCameraTouch.setScale(scale);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    Log.e("touch", "touch:start");
                    removeSeekBarRunnable();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Log.e("touch", "touch:stop");
                    seekBarDelayedHind();
                }
            });
        }


        /**
         * 注册陀螺仪传感器
         */
        private SensorManager mSensorManager;
        private Sensor mSensor;
        private Sensor mLightSensor;

        private void registerSensor() {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (mSensor == null)
                return;
            mSensorManager.registerListener(this, mSensor, Sensor.TYPE_ORIENTATION);
            mSensorManager.registerListener(this, mLightSensor, Sensor.TYPE_LIGHT);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
                float x = event.values[0];
                float z = event.values[2];
                float y = event.values[1];
                if (z > 55.0f) {
                    //向右横屏
                    cameraHelper.setDeviceRotation(1);
                } else if (z < -55.0f) {
                    //向左横屏
                    cameraHelper.setDeviceRotation(3);
                } else if (y > 60.0f) {
                    //是倒竖屏
                    cameraHelper.setDeviceRotation(2);
                } else {
                    //正竖屏
                    cameraHelper.setDeviceRotation(0);
                }
            }
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                float light = event.values[0];
                cameraHelper.setLight(light);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        @Override
        public void onCameraReady() {
            mRecordImageButton.setClickable(true);
        }

        private boolean isCanHind;

        private void removeSeekBarRunnable() {
            isCanHind = true;
            mSeekBarLayout.removeCallbacks(SeekBarLayoutRunnalbe);
            Log.e("hind", "nohind");
        }

        private void seekBarDelayedHind() {
            //3s后颖仓seekbar进度条
            Log.e("hind", "hind");
            if (isCanHind)
                mSeekBarLayout.postDelayed(SeekBarLayoutRunnalbe, 3000);
            isCanHind = false;
        }

        private void removeImageFoucesRunnable()
        {
            mFoucesImage.removeCallbacks(mImageFoucesRunnable);
        }

        private void imageFoucesDelayedHind()
        {
            mFoucesImage.postDelayed(mImageFoucesRunnable,1000);
        }

        private class CameraTouch {
            private float mOldScale = 1.0f;
            private float mScale;
            private float mSpan = 0;
            private float mOldSpan;
            private float mFirstDistance = 0;

            public void onScale(MotionEvent event) {
                if (event.getPointerCount() == 2) {
                    if (mFirstDistance == 0)
                        mFirstDistance = distance(event);

                    float distance = distance(event);
                    float scale;
                    if (distance > mFirstDistance) {
                        scale = (distance - mFirstDistance) / 80;
                        scale = scale + mSpan;
                        mOldSpan = scale;
                        mScale = scale;
                    } else if (distance < mFirstDistance) {
                        scale = distance / mFirstDistance;
                        mOldSpan = scale;
                        mScale = scale * mOldScale;
                    } else {
                        return;
                    }

                    cameraHelper.cameraZoom(mScale);
                    mScaleSeekBar.setProgress((int) ((mScale / cameraHelper.getMaxZoom()) * mScaleSeekBar.getMax()));
                    if (mScale < 1.0f)
                        mScaleSeekBar.setProgress(0);
                }
            }

            public void onScaleStart(MotionEvent event) {
                mFirstDistance = 0;
                setScaleMax((int) cameraHelper.getMaxZoom());

                mSeekBarLayout.setVisibility(View.VISIBLE);
                removeSeekBarRunnable();
                Log.e("scale", "scale:start");
            }

            public void onScaleEnd(MotionEvent event) {
                if (mScale < 1.0f)
                    mOldScale = 1.0f;
                else if (mScale > cameraHelper.getMaxZoom())
                    mOldScale = cameraHelper.getMaxZoom();
                else
                    mOldScale = mScale;
                mSpan = mOldSpan;

                if (event != null)
                    seekBarDelayedHind();
                Log.e("scale", "scale:end");
            }

            public void resetScale() {
                mOldScale = 1.0f;
                mSpan = 0f;
                mFirstDistance = 0f;
                mScaleSeekBar.setProgress(0);
            }

            public void setScale(float scale) {
                mScale = scale;
                mOldSpan = scale;
                onScaleEnd(null);
            }

            /**
             * 计算两个手指间的距离
             *
             * @param event
             * @return
             */
            private float distance(MotionEvent event) {
                float dx = event.getX(1) - event.getX(0);
                float dy = event.getY(1) - event.getY(0);
                /** 使用勾股定理返回两点之间的距离 */
                return (float) Math.sqrt(dx * dx + dy * dy);
            }

            private void setScaleMax(int max) {
                mScaleSeekBar.setMax(max * 100);
            }
        }

        private class ExitBroadcastReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        }
    }
