package com.asen.codec.camera2;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * api > 21
 */
public class Camera2Config {

    private static String TAG = "Camera2Config";

    /**
     * 获取相机ID
     *
     * @param isFront
     * @return
     */
    public static String getCameraID(boolean isFront) {
        String cameraID = null;
        if (isFront) {
            cameraID = CameraCharacteristics.LENS_FACING_BACK + "";
        } else {
            cameraID = CameraCharacteristics.LENS_FACING_FRONT + "";
        }
        return cameraID;
    }

    /**
     * 获取拍照尺寸
     *
     * @param mCaptureSizes
     * @param captureSize
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static Size setCaptureSize(Size[] mCaptureSizes, Size captureSize) {
        if (mCaptureSizes != null) {
            for (Size size : mCaptureSizes) {
                if (size.getHeight() == captureSize.getHeight()
                        && size.getWidth() == captureSize.getWidth()) {
                    return captureSize;
                }
            }
        }
        return captureSize;
    }

    /**
     * 预览配置角度转换
     *
     * @param preview_view
     * @param mPreviewSize
     * @param screenRotation
     * @param viewWidth
     * @param viewHeight
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void configureTransform(TextureView preview_view, Size mPreviewSize, int screenRotation, int viewWidth, int viewHeight) {
        if (null == preview_view || null == mPreviewSize) return;
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == screenRotation || Surface.ROTATION_270 == screenRotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (screenRotation - 2), centerX, centerY);
        }
        preview_view.setTransform(matrix);
    }

    /**
     * 选择录制视频的尺寸
     *
     * @param choices
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static Size getMediaRecorderSize(Size[] choices) {
        for (Size size : choices) {
            // TODO: 2019/4/25  待设置分辨率 4:3 16:9
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        return choices[choices.length - 1];
    }

    /**
     * 根据录制视频选择合适的预览尺寸
     *
     * @param choices
     * @param width
     * @param height
     * @param aspectRatio
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static Size getPreviewSize(Size[] choices, int width, int height, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        // TODO: 2019/4/25  待设置分辨率 4:3 16:9
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // 排序
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    private static final int MINIMUM_PREVIEW_SIZE = 320;

    /**
     * 选择预览尺寸
     *
     * @param choices
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static Size chooseOptimalSize(final Size[] choices) {
        final List<Size> bigEnough = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.getHeight() >= MINIMUM_PREVIEW_SIZE && option.getWidth() >= MINIMUM_PREVIEW_SIZE) {
                Log.d(TAG, "Adding size: " + option.getWidth() + "x" + option.getHeight());
                bigEnough.add(option);
            } else {
                Log.d(TAG, "Not adding size: " + option.getWidth() + "x" + option.getHeight());
            }
        }

        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
            Log.d(TAG, "Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            Log.d(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * 获取支持的最高人脸检测级别
     *
     * @param faceDetectModes 人脸识别模式
     * @return
     */
    public static int getFaceDetectMode(int[] faceDetectModes) {
        if (faceDetectModes == null) {
            return CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
        } else {
            return faceDetectModes[faceDetectModes.length - 1];
        }
    }

}
