package com.example.administrator.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 摄像头帮助类
 */
public class CameraHelper implements ICamera{

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private CameraManager mCameraManager;

    /**
     * 摄像头的id集合
     */
    private String[] mCameraIds;

    /**
     * 摄像头支持的最大size
     */
    private Size mLargest;

    private Size mZoomSize;

    private Size mVideoSize;

    private Context mContext;

    /**
     * 需要打开的摄像头id
     */
    private String mCameraId;

    private MediaRecorder mMediaRecorder;

    private CaptureRequest.Builder mPreviewBuilder;

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mPreviewSession;

    private Surface mSurface;
    private TextureView mTextureView;
    /**
     * 后台线程
     */
    private HandlerThread mBackgroundThread;

    /**
     * 摄像头支持的分辨率流集合
     */
    private StreamConfigurationMap mMap;

    /**
     * 后台handle
     */
    private Handler mBackgroundHandler;

    private AtomicBoolean mIsRecordVideo = new AtomicBoolean();

    private CameraType mNowCameraType;

    /**
     * 拍照的图片读取类
     */
    private ImageReader mImageReader;

    /**
     * 是否支持闪光灯
     */
    private boolean mFlashSupported;

    /**
     * 图片的路径
     */
    private String mPhotoPath;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * 最大的放大倍数
     */
    private float mMaxZoom = 0;

    /**
     * 放大的矩阵，拍照使用
     */
    private Rect mRect;

    /**
     * 根据摄像头管理器获取一个帮助类
     * @param context
     * @return
     */
    public CameraHelper(Context context)
    {
        this.mContext = context;
        CameraManager cameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        mCameraManager = cameraManager;
        try {
            mCameraIds = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void cameraZoom(float scale) {
        if(scale < 1.0f)
            scale = 1.0f;
        if(scale < mMaxZoom) {

            int cropW = (int)((mZoomSize.getWidth()/(mMaxZoom*2.6))*scale);
            int cropH = (int)((mZoomSize.getHeight()/(mMaxZoom*2.6))*scale);

            Rect zoom = new Rect(cropW, cropH,
                    mZoomSize.getWidth() - cropW,
                    mZoomSize.getHeight() - cropH);
            mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            mRect = zoom;
            updatePreview();   //重复更新预览请求
        }
    }

    /**
     * 初始化拍照的图片读取类
     */
    private void initImageReader()
    {
        //实例化拍照用的图片读取类

        Size largest = Collections.max(
                Arrays.asList(mMap.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());

        mImageReader = ImageReader.newInstance(largest.getWidth(),
                largest.getHeight(),ImageFormat.JPEG, 2);


        //设置拍照后的回调监听
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

    }

    /**
     * 初始化预览的大小
     */
    private void initPreviewSize()
    {
        //取最大的分辨率
        Size largest = Collections.max(
                Arrays.asList(mMap.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());

        Point displaySize = new Point();
        ((Activity)mContext).getWindowManager().getDefaultDisplay().getSize(displaySize);

        mLargest = chooseOptimalSize(mMap.getOutputSizes(SurfaceTexture.class),
                this.mTextureView.getWidth(),
                this.mTextureView.getHeight(),
                displaySize.x,
                displaySize.y,
                largest
        );
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean openCamera(CameraType cameraType) {

        mRect = null;
        this.mNowCameraType = cameraType;
        int cameraTypeId;
        switch (cameraType)
        {
            default:
            case BACK:
                cameraTypeId = CameraCharacteristics.LENS_FACING_BACK;
                break;
            case FRONT:
                cameraTypeId = CameraCharacteristics.LENS_FACING_FRONT;
                break;
            case USB:
                cameraTypeId = CameraCharacteristics.LENS_FACING_EXTERNAL;
                break;
        }

        try {
            for (String cameraId : mCameraIds) {
                CameraCharacteristics characteristics
                        = mCameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if(facing != null && facing != cameraTypeId)
                {
                    continue;
                }

                Float maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                if(maxZoom != null)
                {
                    mMaxZoom = maxZoom.floatValue();
                    Log.e("maxZoom","maxZoom:"+maxZoom);
                }

                //获取摄像头支持的流配置信息
                mMap = characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if(mMap == null)
                    return false;

                //初始化预览的宽高
                initPreviewSize();

                //实例化拍照用的图片读取类
                initImageReader();

                mZoomSize = Collections.max(Arrays.asList(mMap.getOutputSizes(SurfaceTexture.class)),
                        new CompareSizesByArea());

                //获取摄像头角度
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                mVideoSize = chooseVideoSize(mMap.getOutputSizes(MediaRecorder.class));
                if(mTextureView != null) {
                    ((AutoFitTextureView) mTextureView).setAspectRatio(mLargest.getHeight(), mLargest.getWidth());
                }

                //检查是否这个摄像头是否支持闪光灯，拍照模式的时候使用
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                this.mCameraId = cameraId;
            }
        }catch (Exception e)
        {
            e.printStackTrace();
        }
        return openCamera(mCameraId);
    }

    @SuppressLint("MissingPermission")
    private boolean openCamera(String cameraId)
    {
        try {
            mCameraManager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public void closeCamera() {
        closePreviewSession();
        if(mCameraDevice != null)
        {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    @Override
    public boolean switchCamera(CameraType cameraType) {
        closeCamera();
        return openCamera(cameraType);
    }

    @Override
    public boolean startPreview() {
        if(mBackgroundHandler == null)
            new Throwable("BackgroundHandler not start, should call startBackgroundThread");

        initPreviewSize();

        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mLargest.getWidth(),mLargest.getHeight());
        mSurface = new Surface(surfaceTexture);

        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);  //创建一个预览请求
            mPreviewBuilder.addTarget(mSurface); //添加预览输出目标画面
        //    Surface photoSurface = mImageReader.getSurface();
        //    mPreviewBuilder.addTarget(photoSurface);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            if(mRect != null)
                mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, mRect);   //放大的矩阵

            mCameraDevice.createCaptureSession(Arrays.asList(mSurface,mImageReader.getSurface()),   //当前线程创建一个预览请求
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            mPreviewSession = session;
                            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            updatePreview();   //重复更新预览请求
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {

                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 更新预览界面
     */
    private void updatePreview()
    {
        if(mCameraDevice == null)
            return;
        try{
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        }catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    @Override
    public void stopPreview() {

    }

    @Override
    public boolean startVideoRecord(String path, int mediaType) {
        if(mIsRecordVideo.get())
            new Throwable("video record is recording");
        if(path == null)
            new Throwable("path can not null");
        if(mediaType != MediaRecorder.OutputFormat.MPEG_4)
            new Throwable("this mediaType can not support");
        if(!setVideoRecordParam(path))
            return false;
        startRecordVideo();
        return true;
    }

    /**
     * 设置录像的参数
     * @param path
     * @return
     */
    private boolean setVideoRecordParam(String path)
    {
        mMediaRecorder = new MediaRecorder();

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(path);
        int bitRate = mVideoSize.getWidth()*mVideoSize.getHeight();
        bitRate = mVideoSize.getWidth() < 1080?bitRate*2:bitRate;
        mMediaRecorder.setVideoEncodingBitRate(bitRate);
        mMediaRecorder.setVideoFrameRate(15);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        mMediaRecorder.setAudioEncodingBitRate(8000);
        mMediaRecorder.setAudioChannels(1);
        mMediaRecorder.setAudioSamplingRate(8000);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        if(mNowCameraType == CameraType.BACK)   //后置摄像头图像要旋转90度
            mMediaRecorder.setOrientationHint(90);
        else
            mMediaRecorder.setOrientationHint(270);   //前置摄像头图像要旋转270度
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public void stopVideoRecord() {
        if(mIsRecordVideo.get())
            mIsRecordVideo.set(false);
        else
            return;
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mMediaRecorder.release();

       // startPreview();
    }

    @Override
    public boolean takePhote(String path, MediaType mediaType) {
        this.mPhotoPath = path;
        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mPreviewBuilder.addTarget(mImageReader.getSurface());

            //设置自动对焦
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            int rotation = ((Activity)mContext).getWindowManager().getDefaultDisplay().getRotation();
            mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            setAutoFlash(); //开启自动闪光灯

            if(mRect != null)
                mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, mRect);   //放大的矩阵

            mPreviewSession.stopRepeating();
            mPreviewSession.abortCaptures();
            //兼容华为这傻逼摄像头,需要延迟后才行
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(200);
                        mPreviewSession.capture(mPreviewBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(CameraCaptureSession session,
                                                           CaptureRequest request,
                                                           TotalCaptureResult result) {
                                //重新启动预览界面
                                //   startPreview();
                            }
                        }, mBackgroundHandler);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        } catch (CameraAccessException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public Size getPreViewSize() {
        return mLargest;
    }

    @Override
    public void setSurface(Surface surface) {
        this.mSurface = surface;
    }

    /**
     * 如果设置了textureView则不用设置Surface
     * @param textureView
     */
    @Override
    public void setTextureView(TextureView textureView) {
        this.mTextureView = textureView;
    }

    /**
     * 开启后台线程
     */
    public void startBackgroundThread()
    {
        mBackgroundThread = new HandlerThread(CameraHelper.class.getSimpleName());
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * 停止后台进程
     */
    public void stopBackgroundThread() {
        if(mBackgroundThread != null)
            mBackgroundThread.quitSafely();
        try {
            if(mBackgroundThread != null)
                mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置闪光灯自动
     */
    private void setAutoFlash()
    {
        if(mFlashSupported)
        {
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    /**
     * 开始录像
     */
    private void startRecordVideo()
    {
        try {
            closePreviewSession();

            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            mLargest = Collections.max(Arrays.asList(mMap.getOutputSizes(SurfaceTexture.class)),
                    new CompareSizesByArea());


            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mLargest.getWidth(),mLargest.getHeight());
            mSurface = new Surface(surfaceTexture);

            mPreviewBuilder.addTarget(mSurface);
            Surface recordSurface = mMediaRecorder.getSurface();
            mPreviewBuilder.addTarget(recordSurface);
            List<Surface> surfaceList = new ArrayList<>();
            surfaceList.add(mSurface);
            surfaceList.add(recordSurface);
            if(mRect != null)
                mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, mRect);   //放大的矩阵
            mCameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mPreviewSession = session;
                    updatePreview();
                    mIsRecordVideo.set(true);
                    mMediaRecorder.start();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            },mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    /**
     * 锁定焦点
     */
    private void lockFocus()
    {
        //告诉摄像头自动对焦
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START);
    }

    private int getOrientation(int rotation) {
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * 异步保存照片
     */
    private class PhotoSaver implements Runnable
    {

        /**
         * 图片文件
         */
        private File mFile;

        /**
         * 拍照的图片
         */
        private Image mImage;

        public PhotoSaver(Image image, File file)
        {
            this.mImage = image;
            this.mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] buffer = new byte[byteBuffer.remaining()];
            byteBuffer.get(buffer);
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(mFile);
                fileOutputStream.write(buffer);
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                mImage.close();
                if(fileOutputStream != null)
                {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 拍照的有效回调
     */
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new PhotoSaver(reader.acquireNextImage(), new File(mPhotoPath)));
        }
    };

    /**
     * 打开摄像头状态回调
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices)
        {
            Log.e("size","x:"+size.getWidth()+", y:"+size.getHeight());
        }
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        return choices[choices.length - 1];
    }


    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}
