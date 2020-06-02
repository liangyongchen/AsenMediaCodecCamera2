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
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ToggleButton;

import com.asen.codec.camera2.Camera2Config;
import com.asen.codec.util.FileUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 视频录制功能
 * 源于camera2
 */
public class VideoCamera2Activity extends AppCompatActivity {

    public String TAG = VideoCamera2Activity.class.getName();
    /**
     * 控件
     */
    private TextureView mPreviewView;  // 预览控件
    private ToggleButton btnRecord;    // 录制视频按钮
    private ToggleButton btnPreview;   // 开启相机预览按钮

    protected static final int TIMEOUT_USEC = 10000;    // 10[msec]

    /**
     * 编码器
     */
    private MediaCodec mMediaCodecVideo;       // 编码器
    private MediaFormat mMediaFormatVideo;     // 上传编码格式
    private MediaCodec.BufferInfo mBufferInfoVideo = null;
    private Surface mRecordSurface;       //上传流使用的surface
    private Size mRecordSize;             // 录制视频尺寸

    /**
     * 相机工具
     */
    private CameraDevice mCameraDevice;              // CameraDevice
    private CameraCaptureSession mPreviewSession;    // 摄像头捕获会话
    private CaptureRequest.Builder mPreviewBuilder;  //预览请求构建
    private Size mPreviewSize;     // 相机预览尺寸
    private int mOrientation;      // 屏幕方向
    /**
     * 录制视频工具
     */
    private RecordThread mRecordThread;   // 录制线程
    private MediaMuxer mMediaMuxer;       // 音视频处理器
    private String mOutputFile;           // 录制视频输出路径
    boolean isRecord = false;             // 是否录制视频
    private boolean notifyEndOfStream;    // 通知流结束:意思是说是否结束正在录制的视频
    private Object object = new Object(); // 录制视频同步锁

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPreviewView = findViewById(R.id.previewSurface);
        btnRecord = findViewById(R.id.btn_record);
        btnPreview = findViewById(R.id.btn_preview);
        // 开启录制线程
        mRecordThread = new RecordThread();
        mRecordThread.start();
        btnRecord.setOnClickListener(v -> {
            if (btnRecord.isChecked()) {
                startRecord();
            } else {
                isRecord = false;
                stopRecord();
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
            // Size[] mMediaRecordSizes = map.getOutputSizes(MediaRecorder.class);
            Size[] mMediaRecordSizes = map.getOutputSizes(MediaCodec.class);
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
     * 开始录制视频
     */
    private void startRecordSurface() {
        try {
            // 返回
            if (null == mCameraDevice || !mPreviewView.isAvailable() || null == mRecordSize)
                return;
            closePreviewSession();

            // region +++++++++++++++++++++++++++++++  编码器配置  +++++++++++++++++++++++++++++++++

            mBufferInfoVideo = new MediaCodec.BufferInfo();
            /*创建编码器*/
            mMediaCodecVideo = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            /*设置编码参数*/
            mMediaFormatVideo = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mRecordSize.getWidth(), mRecordSize.getHeight());
            /*设置颜色格式*/
            mMediaFormatVideo.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            /*设置比特率*/
            mMediaFormatVideo.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
            /*设置帧率*/
            mMediaFormatVideo.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
            /*设置关键帧间隔时间（S）*/
            mMediaFormatVideo.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            /*将设置好的参数配置给编码器*/
            mMediaCodecVideo.configure(mMediaFormatVideo, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            /*使用surface代替mediacodec数据输入buffer*/
            mRecordSurface = mMediaCodecVideo.createInputSurface();
            // -------------------------------------------------------------------------------------
            // 视频编辑器，把MediaCodec编辑的.h264文件转成 mp4 文件
            mOutputFile = FileUtil.getFileName();
            mMediaMuxer = new MediaMuxer(mOutputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mMediaMuxer.setOrientationHint(getOrientationHint(getWindowManager().getDefaultDisplay().getRotation()));

            // endregion ++++++++++++++++++++++++++++++  编码器配置  +++++++++++++++++++++++++++++++

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
                        // 拍照设置，执行 mPreviewSession.capture(） 方法才在调用 ImageReader.setOnImageAvailableListener
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        // 设置人脸检测级别
//                                mPreviewBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
//                                        Camera2Config.getFaceDetectMode(faceDetectModes));
                        mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(),
                                previewCallback, null);

                        mMediaCodecVideo.start(); // 开启录制编码
                        isRecord = true;
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
        stopRecord();
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
     * 开始录制
     */
    public synchronized void startRecord() {
        synchronized (object) {
            notifyEndOfStream = false;
            startRecordSurface();
        }
    }

    /**
     * 结束录制
     */
    public synchronized void stopRecord() {
        synchronized (object) {
            notifyEndOfStream = true;
            if (mMediaCodecVideo != null) {
                mMediaCodecVideo.stop();
                mMediaCodecVideo.release();
                if (mRecordSurface != null) {
                    mRecordSurface.release();
                }
                mMediaCodecVideo = null;
            }
            if (mMediaMuxer != null) {
                mMediaMuxer.stop();
                mMediaMuxer.release();
                mMediaMuxer = null;
                // 执行让录制的文件可以在相册上面查询搜索到
                MediaScannerConnection.scanFile(VideoCamera2Activity.this, new String[]{mOutputFile}, null, null);
            }
        }
    }

    /**
     * 编码录制视频线程
     */
    private class RecordThread extends Thread {
        private int mTrackIndex;

        @Override
        public void run() {
            while (true) {
                if (isRecord) {
                    codec();
                }
            }
        }

        /**
         * 开始编码 --》 直接获取 MediaCodec.getOutputBuffers(); 的数据返回给  MediaMuxer  进行格式转换
         */
        private synchronized void codec() {
            synchronized (object) {
                if (notifyEndOfStream) {
                    // 停止编码器 signalEndOfInputStream只对surface录制有效
                    mMediaCodecVideo.signalEndOfInputStream();
                }
                // 获取输出编码
                ByteBuffer[] outputBuffers = mMediaCodecVideo.getOutputBuffers();
                boolean notDone = true;
                while (notDone) {
                    int outputIndex = mMediaCodecVideo.dequeueOutputBuffer(mBufferInfoVideo, TIMEOUT_USEC);
                    if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) { // 信息稍后再试
                        if (!notifyEndOfStream) {
                            notDone = false;
                        }
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) { // 输出缓冲区已经改变
                        outputBuffers = mMediaCodecVideo.getOutputBuffers();
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { // 格式已经更改
                        MediaFormat newFormat = mMediaCodecVideo.getOutputFormat();
                        mTrackIndex = mMediaMuxer.addTrack(newFormat);
                        mMediaMuxer.start();
                    } else if (outputIndex < 0) {
                        Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + outputIndex);
                    } else {
                        ByteBuffer outputBuffer = outputBuffers[outputIndex];
                        if (outputBuffer == null) {
                            throw new RuntimeException("encoderOutputBuffer " + outputIndex + " was null");
                        }
                        if ((mBufferInfoVideo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            mBufferInfoVideo.size = 0;
                        }
                        if (mBufferInfoVideo.size != 0) {
                            outputBuffer.position(mBufferInfoVideo.offset);
                            outputBuffer.limit(mBufferInfoVideo.offset + mBufferInfoVideo.size);
                            mMediaMuxer.writeSampleData(mTrackIndex, outputBuffer, mBufferInfoVideo);
                        }
                        mMediaCodecVideo.releaseOutputBuffer(outputIndex, false);
                        if ((mBufferInfoVideo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            notDone = false;
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取录制视频旋转方向
     *
     * @param mDeviceOrientation
     * @return
     */
    private int getOrientationHint(int mDeviceOrientation) {
        int orientation = ((int) Math.round(mDeviceOrientation / 90.0) * 90) % 360;
        return (mOrientation + orientation) % 360;
    }

}
