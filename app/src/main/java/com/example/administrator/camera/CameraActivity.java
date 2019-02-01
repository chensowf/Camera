package com.example.administrator.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

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
IVideoControl.PlayStateListener,SensorEventListener, ICamera.TakePhotoListener {

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

    private CameraHelper cameraHelper;

    private ICamera.CameraType mNowCameraType;

    private VideoPlayer mVideoPlayer;

    private MenuAdapter mMenuAdapter;

    private CameraTouch mCameraTouch;

    /**
     * 视频播放时模式下的视频路径
     */
    private String mVideoPath;

    /**
     * 播放视频启动的模式
     * @param activity
     * @param path
     */
    public static void startCameraActivityForPlayVideo(Activity activity, String path)
    {
        Intent intent = new Intent(activity, CameraActivity.class);
        intent.putExtra("mode", VIDEO_MODE);
        intent.putExtra("videoPath", path);
        activity.startActivity(intent);
    }

    /**
     * 摄像头录像和拍照启动的模式
     * @param activity
     * @param requestCode
     */
    public static void startCamearActivityForCamear(Activity activity, int requestCode)
    {
        Intent intent = new Intent(activity, CameraActivity.class);
        intent.putExtra("mode", CAMERA_MODE);
        activity.startActivityForResult(intent, requestCode);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent));
        }

        mVideoPlayer = new VideoPlayer();
        //设置时间戳回调
        mVideoPlayer.setPlaySeekTimeListener(this);

        MODE = getIntent().getIntExtra("mode", CAMERA_MODE);
        if(MODE == CAMERA_MODE)   //摄像头模式
        {
            initCameraMode();
        }
        else if(MODE == VIDEO_MODE)    //视频播放模式
        {
            mVideoPath = getIntent().getStringExtra("videoPath");
            String path = Environment.getExternalStorageDirectory().getPath()+"/video.mkv";
            mVideoPath = getVideoFilePath(this);
            initVideoMode();
        }

    }

    /**
     * 显示播放界面的控件出来
     */
    private void showPlayView()
    {
        showVideoPlaySeekBar();
        mMiniPlayImageButton.setVisibility(View.VISIBLE);
        mPlayImageButton.setVisibility(View.VISIBLE);
        mCloseImageButton.setVisibility(View.VISIBLE);
        mVideoSeekTimeTextView.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏播放界面的控件出来
     */
    private void hindPlayView()
    {
        hindVideoPlaySeekBar();
        mMiniPlayImageButton.setVisibility(View.GONE);
        mPlayImageButton.setVisibility(View.GONE);
        mCloseImageButton.setVisibility(View.GONE);
        mVideoSeekTimeTextView.setVisibility(View.GONE);
    }

    /**
     * 初始化摄像头模式
     */
    private void initCameraMode()
    {
        cameraHelper = new CameraHelper(this);
        cameraHelper.setTakePhotoListener(this);
        mVideoPlayer.setLoopPlay(true);

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0x123);
            }
        }

        mNowCameraType = ICamera.CameraType.BACK;

        List<String> menus = new ArrayList<>();
        menus.add("拍照");
        menus.add("小视频");
        mMenuAdapter = new MenuAdapter(this, menus);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mAutoLocateHorizontalView.setLayoutManager(linearLayoutManager);
        mAutoLocateHorizontalView.setAdapter(mMenuAdapter);
        mAutoLocateHorizontalView.setOnSelectedPositionChangedListener(new AutoLocateHorizontalView.OnSelectedPositionChangedListener() {
            @Override
            public void selectedPositionChanged(int pos) {
                if(pos == 0)
                {
                    NOW_MODE = VIDEO_TAKE_PHOTO;   //拍照模式
                }
                if(pos == 1)
                {
                    NOW_MODE = VIDEO_RECORD_MODE;  //录像模式
                }
            }
        });
        mCameraTouch = new CameraTouch();

        textureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        mCameraTouch.onScaleStart(event);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        mCameraTouch.onScale(event);
                        break;
                    case MotionEvent.ACTION_UP:
                        mCameraTouch.onScaleEnd(event);
                        break;
                }
                return true;
            }
        });

        cutPadding();
        registerSensor();
    }

    /**
     * 初始化视频播放模式
     */
    private void initVideoMode()
    {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0x123);
            }
        }

        hindMenu();
        hindSwitchCamera();
        hindVideoRecordSeekBar();
        mCloseImageButton.setImageResource(R.drawable.ic_video_close);
        mVideoPlayer.setPlayStateListener(this);
        mRecordImageButton.setVisibility(View.GONE);
        textureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mMiniPlayImageButton.getVisibility() == View.VISIBLE)
                {
                    hindPlayView();
                }else {
                    showPlayView();
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            hindPlayView();
                        }
                    }, 5000);
                }
            }
        });

        mVideoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            private int progress;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser)
                {
                    this.progress = progress;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mVideoPlayer.seekTo(progress);
            }
        });
    }

    /**
     * 重新设置录像的进度条样式
     */
    private void cutPadding()
    {
        Point point = new Point();
        getWindowManager().getDefaultDisplay().getSize(point);
        int width = point.x;
        int padding = mVideoRecordSeekBar.getPaddingLeft();
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mVideoRecordSeekBar.getLayoutParams();
        layoutParams.width = width+padding;
        mVideoRecordSeekBar.setLayoutParams(layoutParams);
        mVideoRecordSeekBar.setPadding(0,0,0,0);
    }

    /**
     * 初始化摄像头
     * @param cameraType
     */
    private void initCamera(ICamera.CameraType cameraType)
    {
        if(cameraHelper == null )
            return;
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED){
            return;
        }
        cameraHelper.setTextureView(textureView);
        cameraHelper.openCamera(cameraType);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 0x123)
        {
            initCamera(mNowCameraType);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(cameraHelper != null)
            cameraHelper.startBackgroundThread();

        if(textureView.isAvailable()){
            if(MODE == CAMERA_MODE) {
                if (TEXTURE_STATE == TEXTURE_PREVIEW_STATE)  //预览状态
                    initCamera(mNowCameraType);
                else if (TEXTURE_STATE == TEXTURE_PLAY_STATE)   //视频播放状态
                    mVideoPlayer.play();
                mVideoPlayer.setVideoPlayWindow(new Surface(textureView.getSurfaceTexture()));
            }
        }
        else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    if(MODE == CAMERA_MODE) {
                        if (TEXTURE_STATE == TEXTURE_PREVIEW_STATE)  //预览状态
                            initCamera(mNowCameraType);
                        else if (TEXTURE_STATE == TEXTURE_PLAY_STATE)   //视频播放状态
                            mVideoPlayer.play();
                        mVideoPlayer.setVideoPlayWindow(new Surface(textureView.getSurfaceTexture()));
                    }else if(MODE == VIDEO_MODE)
                    {
                        mVideoPlayer.setVideoPlayWindow(new Surface(textureView.getSurfaceTexture()));
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
        if(MODE == CAMERA_MODE) {
            if (TEXTURE_STATE == TEXTURE_PREVIEW_STATE) {
                closeCamera();
            } else if (TEXTURE_STATE == TEXTURE_PLAY_STATE) {
                mVideoPlayer.pause();
            }
        }
    }

    /**
     * 切换摄像头
     */
    @OnClick(R.id.video_switch_camera)
    public void switchCamera()
    {
        if(mNowCameraType == ICamera.CameraType.FRONT) {
            cameraHelper.switchCamera(ICamera.CameraType.BACK);
            mNowCameraType = ICamera.CameraType.BACK;
        }
        else
        {
            cameraHelper.switchCamera(ICamera.CameraType.FRONT);
            mNowCameraType = ICamera.CameraType.FRONT;
        }
    }

    private boolean isRecording = false;

    /**
     * 视频录制
     */
    @OnClick(R.id.video_record)
    public void recordVideoOrTakePhoto()
    {
        //录像模式
        if(NOW_MODE == VIDEO_RECORD_MODE) {
            if (!isRecording) {
                isRecording = cameraHelper.startVideoRecord(getVideoFilePath(this), MediaRecorder.OutputFormat.MPEG_4);
                if (isRecording) {
                    mRecordImageButton.setImageResource(R.drawable.ic_recording);
                    hindSwitchCamera();
                    recordCountDown();
                    hindMenu();
                    mCloseImageButton.setVisibility(View.GONE);
                    TEXTURE_STATE = TEXTURE_RECORD_STATE;
                }
            } else {
                isRecording = false;
                cameraHelper.stopVideoRecord();
                mRecordImageButton.setImageResource(R.drawable.ic_record);
                mRecordImageButton.setVisibility(View.GONE);
                mCloseImageButton.setVisibility(View.VISIBLE);
                showRecordEndView();
                stopRecordCountTime();
                hindVideoRecordSeekBar();
                playVideo();
                Bitmap bitmap = getVideoFirstFrame(new File(getVideoFilePath(this)));
                Log.e("bitmap","bitmap:"+bitmap.getByteCount());
            }
        }

        //拍照模式
        if(NOW_MODE == VIDEO_TAKE_PHOTO)
        {
            if(cameraHelper.takePhote(getPhotoFilePath(this),ICamera.MediaType.JPEG)) {

            }
        }
    }

    /**
     * 隐藏切换菜单
     */
    private void hindMenu()
    {
        mAutoLocateHorizontalView.setVisibility(View.GONE);
    }

    /**
     * 显示切换菜单
     */
    private void showMeun()
    {
        mAutoLocateHorizontalView.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏切换摄像头按钮
     */
    private void hindSwitchCamera()
    {
        mSwitchCameraButton.setVisibility(View.GONE);
    }

    /**
     * 显示切换摄像头按钮
     */
    private void showSwitchCamera()
    {
        mSwitchCameraButton.setVisibility(View.VISIBLE);
    }

    /**
     * 显示视频录像的进度条
     */
    private void showVideoRecordSeekBar()
    {
        mVideoRecordSeekBar.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏视频录像的进度条
     */
    private void hindVideoRecordSeekBar()
    {
        mVideoRecordSeekBar.setVisibility(View.GONE);
    }

    /**
     * 中止计时
     */
    private void stopRecordCountTime()
    {
        if(mDisposable != null && !mDisposable.isDisposed())
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
    private void recordCountDown()
    {
        mTimeTextView.setVisibility(View.VISIBLE);
        showVideoRecordSeekBar();
        final int count = 15;
        mDisposable = Observable.interval(1,1,TimeUnit.SECONDS)
                .take(count+1)
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
                        if(time < 10)
                            mTimeTextView.setText("0:0"+String.valueOf(time));
                        else
                            mTimeTextView.setText("0:"+String.valueOf(time));
                        mVideoRecordSeekBar.setProgress((int) time);
                        if(time == VIDEO_MAX_TIME)
                        {
                            recordVideoOrTakePhoto();
                            hindVideoRecordSeekBar();
                        }
                    }
                });
    }

    /**
     * 显示录像完成后底部两个按钮
     */
    private void showRecordEndView()
    {
        mSaveImageButton.setVisibility(View.VISIBLE);
        mDeleteImageButton.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏录像完成后底部两个按钮
     */
    private void hindRecordEndView()
    {
        mSaveImageButton.setVisibility(View.GONE);
        mDeleteImageButton.setVisibility(View.GONE);
    }

    /**
     * 播放视频
     */
    public void playVideo()
    {
        closeCamera();
        mVideoPlayer.setDataSourceAndPlay(getVideoFilePath(this));
        isPlaying = true;

        TEXTURE_STATE = TEXTURE_PLAY_STATE;  //视频播放状态
    }

    private boolean isPlaying = false;
    /**
     * 暂停或者播放视频
     */
    @OnClick({R.id.video_mine_play, R.id.video_play})
    public void playOrPause()
    {
        if(!isPlaying)
        {
            mMiniPlayImageButton.setImageResource(R.drawable.ic_pause);
            mPlayImageButton.setImageResource(R.drawable.ic_pause);
            mVideoPlayer.play();
            isPlaying = true;
        }
        else
        {
            mMiniPlayImageButton.setImageResource(R.drawable.ic_play);
            mPlayImageButton.setImageResource(R.drawable.ic_play);
            isPlaying = false;
            mVideoPlayer.pause();
        }
    }

    @OnClick(R.id.video_delete)
    public void deleteVideoOrPicture()
    {
        if(TEXTURE_STATE == TEXTURE_PLAY_STATE)
        {
            mVideoPlayer.stop();
            cameraHelper.startBackgroundThread();
            cameraHelper.openCamera(mNowCameraType);
            mCameraTouch.resetScale();  //重新打开摄像头重置一下放大倍数
            File file = new File(getVideoFilePath(this));
            if(file.exists())
                file.delete();
        }else if(TEXTURE_STATE == TEXTURE_PHOTO_STATE)
        {
            File file = new File(getPhotoFilePath(this));
            if(file.exists())
                file.delete();
            cameraHelper.startPreview();
        }

        TEXTURE_STATE = TEXTURE_PREVIEW_STATE;

        hindRecordEndView();
        showSwitchCamera();
        showMeun();
        mRecordImageButton.setVisibility(View.VISIBLE);
        mTimeTextView.setVisibility(View.GONE);
    }

    public void close(View view)
    {
        finish();
    }

    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + "test" + ".mp4";
    }

    private String getPhotoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + "image" + ".jpeg";
    }

    /**
     * 关闭摄像头
     */
    private void closeCamera()
    {
        cameraHelper.closeCamera();
        cameraHelper.stopBackgroundThread();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(MODE == CAMERA_MODE)
        {
            //如果正在录像，就停止并且删除
            if(TEXTURE_STATE == TEXTURE_RECORD_STATE)
            {
                cameraHelper.stopVideoRecord();
                closeCamera();
                deleteVideoOrPicture();
            }

            if(TEXTURE_STATE == TEXTURE_PHOTO_STATE)
            {
                closeCamera();
                deleteVideoOrPicture();
            }
        }
        if(mSensorManager != null)
            mSensorManager.unregisterListener(this);
        if(isPlaying)
            mVideoPlayer.stop();
        mVideoPlayer.destroy();
    }

    @Override
    public void onSeekTime(int allTime, final int time) {
        if(mVideoSeekBar.getVisibility() != View.VISIBLE)
            return;
        if(mVideoSeekBar.getMax() != allTime)
            mVideoSeekBar.setMax(allTime);
        mVideoSeekBar.setProgress(time);
        mVideoSeekTimeTextView.post(new Runnable() {
            @Override
            public void run() {
                float t = (float)time/1000.0f;
                mVideoSeekTimeTextView.setText(secToTime(Math.round(t)));
            }
        });
    }

    /**
     * 显示视频播放进度条
     */
    private void showVideoPlaySeekBar()
    {
        mVideoSeekBar.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏视频播放进度条
     */
    private void hindVideoPlaySeekBar()
    {
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
    }

    /**
     * 视频拍完之后获取第一帧
     * @param file
     * @return
     */
    private Bitmap getVideoFirstFrame(File file)
    {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(file.getAbsolutePath());
        return mmr.getFrameAtTime();
    }


    /**
     * 整数s转 xx:xx:xx
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

    /**
     * 注册陀螺仪传感器
     */
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private void registerSensor()
    {
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        if(mSensor == null)
            return;
        mSensorManager.registerListener(this,mSensor,Sensor.TYPE_ORIENTATION);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float z = event.values[2];
        float y = event.values[1];
        if(z > 55.0f)
        {
            //向右横屏
            cameraHelper.setDeviceRotation(1);
        }else if(z < -55.0f)
        {
            //向左横屏
            cameraHelper.setDeviceRotation(3);
        }else if(y > 60.0f){
            //是倒竖屏
            cameraHelper.setDeviceRotation(2);
        }else
        {
            //正竖屏
            cameraHelper.setDeviceRotation(0);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onTakePhotoFinish() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hindSwitchCamera();
                hindMenu();
                showRecordEndView();
                mRecordImageButton.setVisibility(View.GONE);
                TEXTURE_STATE = TEXTURE_PHOTO_STATE;
            }
        });
    }


    private class CameraTouch
    {
        private float mOldScale = 1.0f;
        private float mScale;
        private float mSpan = 0;
        private float mOldSpan;
        private float mFirstDistance = 0;

        public void onScale(MotionEvent event)
        {
            if(event.getPointerCount() == 2)
            {
                if(mFirstDistance == 0)
                    mFirstDistance = distance(event);

                float distance = distance(event);
                float scale = 1.0f;
                if(distance > mFirstDistance) {
                    scale = (distance - mFirstDistance) / 80;
                    //  if(scale <= mSpan) {
                    scale = scale + mSpan;
                    //  }
                    mOldSpan = scale;
                    mScale = scale*1.0f;
                }
                else if(distance < mFirstDistance){
                    scale = distance / mFirstDistance;
                    mOldSpan = scale;
                    mScale = scale*mOldScale;
                }else
                {
                    return;
                }
                cameraHelper.cameraZoom(mScale);
            }
        }

        public void onScaleStart(MotionEvent event)
        {
            mFirstDistance = 0;
        }

        public void onScaleEnd(MotionEvent event)
        {
            if(mScale < 1.0f)
                mOldScale = 1.0f;
            else if(mScale > cameraHelper.getMaxZoom())
                mOldScale = cameraHelper.getMaxZoom();
            else
                mOldScale = mScale;
            mSpan = mOldSpan;
        }

        public void resetScale()
        {
            mOldScale = 1.0f;
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
    }
}
