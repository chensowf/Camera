
package com.example.administrator.camera;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

/**
 * A {@link TextureView} that can be adjusted to a specified aspect ratio.
 */
public class AutoFitTextureView extends TextureView{

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;

        float mRatio = (float) mRatioWidth / (float) mRatioHeight;   //算出相机的缩放比例

        float w = mRatio * getHeight();
        float scale;
        if (w > getWidth())
            scale = w / (float) getWidth();
        else
            scale = (float) getWidth() / w;

        Matrix matrix = new Matrix();
        matrix.postScale(scale, 1, getWidth() / 2, getHeight() / 2);
        setTransform(matrix);
    }

    /**
     * 视频宽度适配
     * @param width
     * @param height
     */
    public void setVideoAspectRatio(int width, int height)
    {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;

        float mRatio = (float) mRatioWidth / (float) mRatioHeight;   //算出相机的缩放比例
        if(mRatio < 1.0)
            return;
        float h = getWidth()/mRatio;
        float scale;
        if (h > getHeight())
            scale = (float)getHeight() / h;
        else
            scale =  h / (float)getHeight();

        Matrix matrix = new Matrix();
        matrix.postScale(1, scale, getWidth() / 2, getHeight() / 2);
        setTransform(matrix);
    }
}
