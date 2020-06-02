package com.asen.codec;

import android.annotation.SuppressLint;
import android.content.Context;
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
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ToggleButton;

import com.asen.codec.camera2.Camera2Config;
import com.asen.codec.encoder.AVEncodeManager;
import com.asen.codec.util.FileUtil;

import java.util.Arrays;

public class AVCamera2Activity extends AppCompatActivity {

    /**
     * 控件
     */
    private TextureView mPreviewView;  // 预览控件
    private ToggleButton btnRecord;    // 录制视频按钮
    private ToggleButton btnPreview;   // 开启相机预览按钮
    /**
     * 视频 Surface 缓存
     */
    private Surface mRecordSurface;       //上传流使用的surface
    private Size mRecordSize;             // 录制视频尺寸
    /**
     * 编码器
     */
    private AVEncodeManager mAVEncodeManager;
    /**
     * 相机工具
     */
    private CameraDevice mCameraDevice;              // CameraDevice
    private CameraCaptureSession mPreviewSession;    // 摄像头捕获会话
    private CaptureRequest.Builder mPreviewBuilder;  //预览请求构建
    private Size mPreviewSize;     // 相机预览尺寸
    private int mOrientation;      // 屏幕方向

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPreviewView = findViewById(R.id.previewSurface);
        btnRecord = findViewById(R.id.btn_record);
        btnPreview = findViewById(R.id.btn_preview);

        btnRecord.setOnClickListener(v -> {
            if (btnRecord.isChecked()) {
                startRecordSurface();
            } else {
                mAVEncodeManager.stopEncode(); // 结束音视频编码合成
                startPreviewSurface(); // 结束录制返回预览界面
            }
        });
        btnPreview.setOnClickListener(v -> {
            if (btnPreview.isChecked()) {
                openCamera(1920, 1080);
            } else {
                closePreviewSession(); // 关闭预览 --> 如果正在录制视频请不要关闭预览，结果自己尝试
            }
        });
    }

    /**
     * 打开相机
     *
     * @param width
     * @param height
     */
    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {
        try {
            String cameraID = Camera2Config.getCameraID(true); // 获取相机ID
            CameraManager mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            // 获取相机特性配置参数
            CameraCharacteristics mChar = mCameraManager.getCameraCharacteristics(cameraID);
            // 获取设备头角度
            mOrientation = mChar.get(CameraCharacteristics.SENSOR_ORIENTATION);
            StreamConfigurationMap map = mChar.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // 获取相机预览尺寸
            Size[] mPreviewSizes = map.getOutputSizes(SurfaceTexture.class);
            mPreviewSize = Camera2Config.chooseOptimalSize(mPreviewSizes);
            // 录制视频尺寸,相机返回的录制视频尺寸
            Size[] mMediaRecordSizes = map.getOutputSizes(MediaRecorder.class);
            mRecordSize = Camera2Config.getMediaRecorderSize(mMediaRecordSizes);
            // 预览配置角度转换,不设置目前没有报错
            Camera2Config.configureTransform(mPreviewView, mPreviewSize, getWindowManager().getDefaultDisplay().getRotation(), width, height);
            /*获取CameraID列表*/
            mCameraManager.openCamera(cameraID, mStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 摄像头打开回调
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            startPreviewSurface(); // 开始预览
        }

        @Override
        public void onDisconnected(final CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(final CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    /**
     * 开始预览
     */
    private void startPreviewSurface() {
        try {
            // 返回
            if (null == mCameraDevice || !mPreviewView.isAvailable() || null == mRecordSize)
                return;
            closePreviewSession();

            SurfaceTexture texture = mPreviewView.getSurfaceTexture();
            texture.setDefaultBufferSize(mRecordSize.getWidth(), mRecordSize.getHeight());
            Surface previewSurface = new Surface(texture);
            /*创建预览请求*/
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewBuilder.addTarget(previewSurface);
            /*创建会话*/
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mPreviewSession = session;
                    try {
                        mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), previewCallback, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 预览回调 -> 初始化并获取预览回调对象 -> 进行人脸框绘制
     */
    final CameraCaptureSession.CaptureCallback previewCallback = new CameraCaptureSession
            .CaptureCallback() {
        public void onCaptureCompleted(final CameraCaptureSession session,
                                       final CaptureRequest request,
                                       final TotalCaptureResult result) {
            // 绘制人脸框
//            draw(result, isFront, mPixelSize);
        }
    };

    /**
     * 退出界面按钮
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    /**
     * 关闭相机预览
     */
    public void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    /**
     * 获取录制视频旋转方向
     *
     * @return
     */
    private int getOrientationHint() {
        int mDeviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ((int) Math.round(mDeviceOrientation / 90.0) * 90) % 360;
        return (mOrientation + orientation) % 360;
    }

    /**
     * 开始录制视频
     */
    private void startRecordSurface() {
        try {
            // 返回
            if (null == mCameraDevice || !mPreviewView.isAvailable() || null == mRecordSize)
                return;
            closePreviewSession();

            mRecordSurface = getMediaCodecSurface();

            SurfaceTexture mSurfaceTexture = mPreviewView.getSurfaceTexture();
            mSurfaceTexture.setDefaultBufferSize(mRecordSize.getWidth(), mRecordSize.getWidth());
            Surface mPreviewSurface = new Surface(mSurfaceTexture);

            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewBuilder.addTarget(mRecordSurface);
            mPreviewBuilder.addTarget(mPreviewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface, mRecordSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        mPreviewSession = session;
                        // 拍照设置，只有执行 mPreviewSession.capture(） 方法才在 ImageReader.setOnImageAvailableListener 返回图片监听
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(),
                                previewCallback, null);
                        // 开始编码
                        mAVEncodeManager.startEncode();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化编码
     */
    private Surface getMediaCodecSurface() {
        mAVEncodeManager = new AVEncodeManager();

        String filePath = FileUtil.getFileName();
        int mediaFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
        // String audioType = MediaFormat.MIMETYPE_AUDIO_AAC;
        String audioType = "audio/mp4a-latm";
        // String videoType = MediaFormat.MIMETYPE_VIDEO_AVC;
        String videoType = "video/avc";
        int sampleRate = 44100;
        int channelCount = 2; // 单声道 channelCount=1 , 双声道  channelCount=2
        // AudioThread.class类中采集音频采用的位宽：AudioFormat.ENCODING_PCM_16BIT ，
        // 此处应传入16bit， 用作计算pcm一帧的时间戳
        int audioFormat = 16;
        mAVEncodeManager.init(filePath, mediaFormat, audioType, sampleRate,
                channelCount, audioFormat, videoType, mRecordSize.getWidth(),
                mRecordSize.getHeight(), getOrientationHint());
        return mAVEncodeManager.getSurface();
    }


}
