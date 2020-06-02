package com.asen.codec.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.asen.codec.encoder.EncodeConfig.audioStop;
import static com.asen.codec.encoder.EncodeConfig.encodeStart;
import static com.asen.codec.encoder.EncodeConfig.surfaceChange;
import static com.asen.codec.encoder.EncodeConfig.surfaceCreate;
import static com.asen.codec.encoder.EncodeConfig.videoStop;

/**
 * 编解码管理类
 */
public class AVEncodeManager {

    private static final String TAG = AVEncodeManager.class.getName();
    /**
     * 返回给录制对象，用于数据流输入
     */
    private Surface surface;
    /**
     * 音视频合成器
     */
    private MediaMuxer mediaMuxer;
    /**
     * 音频编码器
     */
    private MediaCodec audioCodec;
    private MediaCodec.BufferInfo audioBuffer;
    private AudioThread audioCapture; // 录音回调
    /**
     * 视频编码器
     */
    private MediaCodec videoCodec;
    private MediaCodec.BufferInfo videoBuffer;
    /**
     * 默认配置
     */
    private String audioType = "audio/mp4a-latm";
    private String videoType = "video/avc";
    private int mediaFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    private int sampleRate = 44100;
    private int channelCount = 2; // //单声道 channelCount=1 , 双声道  channelCount=2
    private int audioFormat = 16; // 用作计算pcm一帧的时间戳
    /**
     * 时间戳，控制录音
     */
    private long presentationTimeUs;
    /**
     * 音视频编码线程
     */
    private AudioEncode audioThread;
    private VideoEncode videoThread;


    public AVEncodeManager() {
        audioCapture = new AudioThread();
    }

    /**
     * 回调给相机或opengl进行录制
     *
     * @return
     */
    public Surface getSurface() {
        return surface;
    }

    /**
     * 初始化编码器
     *
     * @param filePath     {@link #initMuxer}
     * @param mediaFormat  {@link #initMuxer}
     * @param audioType    {@link #initAudio}
     * @param sampleRate   {@link #initAudio}
     * @param channelCount {@link #initAudio}
     * @param audioFormat  {@link #setPcmSource}
     * @param videoType    {@link #initAudio}
     * @param width        {@link #initAudio}
     * @param height       {@link #initAudio}
     * @param orientation  {@link #initMuxer}
     */
    public void init(String filePath, int mediaFormat, String audioType, int sampleRate,
                     int channelCount, int audioFormat, String videoType, int width, int height, int orientation) {
        this.audioType = audioType;
        this.videoType = videoType;
        this.mediaFormat = mediaFormat;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        this.audioFormat = audioFormat;
        // 初始化 路径 媒体格式 视频旋转角度
        initMuxer(filePath, mediaFormat, orientation);
        // 初始化 视频格式MP4 视频宽 视频高
        initVideo(videoType, width, height);
        // 初始化 音频格式aac 采样率 声道
        initAudio(audioType, sampleRate, channelCount);

        videoThread = new VideoEncode(videoCodec, videoBuffer, mediaMuxer, mListener);
        audioThread = new AudioEncode(audioCodec, audioBuffer, mediaMuxer, mListener);
    }

    /**
     * 合成器
     *
     * @param filePath    录制文件路径
     * @param mediaFormat 媒体格式
     * @param orientation 视频录制旋转方向
     */
    private void initMuxer(String filePath, int mediaFormat, int orientation) {
        try {
            mediaMuxer = new MediaMuxer(filePath, mediaFormat);
            mediaMuxer.setOrientationHint(orientation);
        } catch (IOException e) {
            Log.e(TAG, "initMediaMuxer: 文件打开失败,path=" + filePath);
        }
    }

    /**
     * 初始化视频
     *
     * @param videoType 视频格式MP4
     * @param width     视频宽
     * @param height    视频高
     */
    private void initVideo(String videoType, int width, int height) {
        try {
            videoCodec = MediaCodec.createEncoderByType(videoType);
            MediaFormat videoFormat = MediaFormat.createVideoFormat(videoType, width, height);

            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            // MediaFormat.KEY_FRAME_RATE -- 可通过Camera#Parameters#getSupportedPreviewFpsRange获取
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            // width*height*N  N标识码率低、中、高，类似可设置成1，3，5，码率越高视频越大，也越清晰
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4);
            // 每秒关键帧数
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
                videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
            }

            videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            videoBuffer = new MediaCodec.BufferInfo();
            // 传递给录制对象 可以是 opengl camera camrea2 或是自己绘制的编码视频流
            surface = videoCodec.createInputSurface();
        } catch (IOException e) {
            Log.e(TAG, "initVideoCodec: 视频类型无效");
        }
    }

    /**
     * 初始化音频
     *
     * @param audioType  音频格式aac
     * @param sampleRate 采样率
     * @param channels   声道 》 单声道 channels=1 , 双声道  channels=2
     */
    private void initAudio(String audioType, int sampleRate, int channels) {
        try {
            audioCodec = MediaCodec.createEncoderByType(audioType);
            MediaFormat audioFormat = MediaFormat.createAudioFormat(audioType, sampleRate, channels);
            int BIT_RATE = 96000;
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            int MAX_INOUT_SIZE = 8192;
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INOUT_SIZE);

            audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            audioBuffer = new MediaCodec.BufferInfo();

        } catch (IOException e) {
            Log.e(TAG, "initAudioCodec: 音频类型无效");
        }
    }

    /**
     * 监听视频录制
     */
    private final EncodeChangeListener mListener = new EncodeChangeListener() {

        @Override
        public void onMediaMuxerChangeListener(int type) {
            if (type == EncodeConfig.MUXER_START) {
                Log.d(TAG, "onMediaMuxerChangeListener --- " + "视频录制开始了");
                // 监听音频数据回调
                if (audioCapture.getCaptureListener() == null)
                    audioCapture.setCaptureListener((audioSource, audioReadSize) -> {
                        if (EncodeConfig.audioStop || EncodeConfig.videoStop) {
                            return;
                        }
                        setPcmSource(audioSource, audioReadSize);
                        //计算分贝的一种方式
//                        new Handler().postDelayed(() -> {
//                            double dBValue = ByteUtils.calcDecibelValue(audioSource, audioReadSize);
//                            Log.d(TAG, "calcDecibelLevel: 分贝值 = " + dBValue);
//                        }, 200);
                    });
            }
        }

        @Override
        public void onMediaInfoListener(int time) {
            Log.d(TAG, "视频录制时长 --- " + time);
        }
    };

    /**
     * 回调音频数据进行编码合成
     *
     * @param pcmBuffer
     * @param buffSize
     */
    private void setPcmSource(byte[] pcmBuffer, int buffSize) {
        if (audioCodec == null) {
            return;
        }
        try {

            int buffIndex = audioCodec.dequeueInputBuffer(0);
            if (buffIndex < 0) {
                return;
            }
            ByteBuffer byteBuffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                byteBuffer = audioCodec.getInputBuffer(buffIndex);
            } else {
                byteBuffer = audioCodec.getInputBuffers()[buffIndex];
            }
            if (byteBuffer == null) {
                return;
            }
            byteBuffer.clear();
            byteBuffer.put(pcmBuffer);
            // presentationTimeUs = 1000000L * (buffSize / 2) / sampleRate
            // 一帧音频帧大小 int size = 采样率 x 位宽 x 采样时间 x 通道数
            // 1s时间戳计算公式  presentationTimeUs = 1000000L * (totalBytes / sampleRate/ audioFormat / channelCount / 8 )
            // totalBytes : 传入编码器的总大小
            // 1000 000L : 单位为 微秒，换算后 = 1s,
            //除以8     : pcm原始单位是bit, 1 byte = 8 bit, 1 short = 16 bit, 用 Byte[]、Short[] 承载则需要进行换算
            presentationTimeUs += (long) (1.0 * buffSize / (sampleRate * channelCount * (audioFormat / 8)) * 1000000.0);
            Log.d(TAG, "pcm一帧时间戳 = " + presentationTimeUs / 1000000.0f);
            audioCodec.queueInputBuffer(buffIndex, 0, buffSize, presentationTimeUs, 0);
        } catch (java.lang.IllegalStateException e) {
            //audioCodec 线程对象已释放MediaCodec对象
            Log.d(TAG, "setPcmSource: " + "MediaCodec对象已释放");
        }
    }

    /**
     * 开始编码
     */
    public void startEncode() {
        if (surface == null) {
            Log.e(TAG, "startEncode: createInputSurface创建失败");
            return;
        }
        encodeStart = false;
        videoThread.start();
        audioThread.start();

        surfaceCreate = true;
        surfaceChange = true;

        audioStop = false;
        videoStop = false;

        // 开始录音
        audioCapture.start();
    }

    /**
     * 结束编码
     */
    public void stopEncode() {
        encodeStart = false;

        audioThread.stopAudioCodec();
        audioThread = null;
        videoThread.stopVideoCodec();
        videoThread = null;

        surfaceCreate = false;
        surfaceChange = false;

        // 结束录音
        if (audioCapture != null) {
            audioCapture.setCaptureListener(null); // 停止录音传输
            audioCapture.stop();
            audioCapture = null;
        }

    }
}
