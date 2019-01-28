package com.example.administrator.camera;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoPlayer implements IVideoControl {

    private MediaPlayer mMediaPlayer;
    private Surface mSurface;
    private String mVideoFilePath;

    private IVideoControl.PlaySeekTimeListener mPlaySeekTimeListener;
    private IVideoControl.PlayStateListener mPlayStateListener;

    private HandlerThread mHandlerThread;
    private Handler mHandle;

    private AtomicBoolean mIsNowSeekTime;

    public VideoPlayer()
    {
        mMediaPlayer = new MediaPlayer();
        mHandlerThread = new HandlerThread(VideoPlayer.class.getSimpleName());
        mHandlerThread.start();
        mHandle = new Handler(mHandlerThread.getLooper());
        mIsNowSeekTime = new AtomicBoolean(false);
    }

    public void setLoopPlay(boolean isLoop)
    {
        mMediaPlayer.setLooping(isLoop);
    }

    public void setDataSourceAndPlay(String path)
    {
        mVideoFilePath = path;
        try {
            mMediaPlayer.setDataSource(mVideoFilePath);
            mMediaPlayer.setSurface(this.mSurface);
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    stopVideoSeekTime();
                    return false;
                }
            });
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    play();
                    startVideoSeekTime();
                }
            });
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if(mPlayStateListener != null)
                        mPlayStateListener.onCompletionListener();
                }
            });
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            mMediaPlayer = null;
        }
    }

    @Override
    public void play() {
        if(mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
            if(mPlayStateListener != null)
                mPlayStateListener.onStartListener(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
        }
    }

    @Override
    public void pause() {
        if(mMediaPlayer != null && mMediaPlayer.isPlaying())
            mMediaPlayer.pause();
    }

    @Override
    public void resume() {
        if(mMediaPlayer != null && !mMediaPlayer.isPlaying())
            mMediaPlayer.start();
    }

    @Override
    public void stop() {
        if(mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.reset();
            stopVideoSeekTime();
        }
    }

    @Override
    public void seekTo(int timeStamp) {
        if(mMediaPlayer != null)
            mMediaPlayer.seekTo(timeStamp);
    }

    @Override
    public void setPlaySeekTimeListener(PlaySeekTimeListener playSeekTimeListener) {
        this.mPlaySeekTimeListener = playSeekTimeListener;
    }

    @Override
    public void setPlayStateListener(PlayStateListener playStateListener) {
        this.mPlayStateListener = playStateListener;
    }

    public void setVideoPlayWindow(Surface surface)
    {
        this.mSurface = surface;
    }

    /**
     * 视频播放进度
     */
    private void startVideoSeekTime()
    {
        if(mPlaySeekTimeListener == null)
            return;
        mIsNowSeekTime.set(true);
        mHandle.post(new Runnable() {
            @Override
            public void run() {
                while (mIsNowSeekTime.get())
                {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (mMediaPlayer == null)
                        return;
                    synchronized (mMediaPlayer) {
                        if (mMediaPlayer == null)
                            return;
                        mPlaySeekTimeListener.onSeekTime(mMediaPlayer.getDuration(), mMediaPlayer.getCurrentPosition());
                    }
                }
            }
        });
    }

    private void stopVideoSeekTime()
    {
        mIsNowSeekTime.set(false);
    }

    public void destroy()
    {
        mHandlerThread.quitSafely();
        if(mMediaPlayer != null) {
            synchronized (mMediaPlayer) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }
    }


}
