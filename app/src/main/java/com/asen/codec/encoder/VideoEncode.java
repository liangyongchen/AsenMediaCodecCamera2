package com.asen.codec.encoder;

import android.media.MediaCodec;
import android.media.MediaMuxer;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;

import java.nio.ByteBuffer;

import static com.asen.codec.encoder.EncodeConfig.audioTrackIndex;
import static com.asen.codec.encoder.EncodeConfig.videoTrackIndex;

/**
 * 视频写入thread
 */
public class VideoEncode extends Thread {

    private static final String TAG = VideoEncode.class.getName();

    /**
     * 编码器
     */
    private MediaCodec videoCodec;
    private MediaCodec.BufferInfo bufferInfo;
    // 合成器
    private MediaMuxer mediaMuxer;
    // 是否结束
    private boolean isStop;
    // 时长
    private long pts;
    // 回调监听
    private EncodeChangeListener listener;

    public VideoEncode(MediaCodec mediaCodec, MediaCodec.BufferInfo bufferInfo, MediaMuxer mediaMuxer,
                       @NonNull EncodeChangeListener listener) {
        this.videoCodec = mediaCodec;
        this.bufferInfo = bufferInfo;
        this.mediaMuxer = mediaMuxer;
        this.listener = listener;
        pts = 0;
        videoTrackIndex = -1;
    }

    @Override
    public void run() {
        super.run();
        isStop = false;
        videoCodec.start();
        while (true) {
            if (isStop) {
                videoCodec.stop();
                videoCodec.release();
                videoCodec = null;
                EncodeConfig.videoStop = true;

                if (EncodeConfig.audioStop) {
                    mediaMuxer.stop();
                    mediaMuxer.release();
                    mediaMuxer = null;
                    listener.onMediaMuxerChangeListener(EncodeConfig.MUXER_STOP);
                    break;
                }
            }

            if (videoCodec == null)
                break;
            int outputBufferIndex = videoCodec.dequeueOutputBuffer(bufferInfo, 0);
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                videoTrackIndex = mediaMuxer.addTrack(videoCodec.getOutputFormat());
                if (audioTrackIndex != -1) {
                    mediaMuxer.start();
                    //标识编码开始
                    EncodeConfig.encodeStart = true;
                    listener.onMediaMuxerChangeListener(EncodeConfig.MUXER_START);
                }
            } else {
                while (outputBufferIndex >= 0) {
                    if (!EncodeConfig.encodeStart) {
                        Log.d(TAG, "run: 线程延迟");
                        SystemClock.sleep(10);
                        continue;
                    }

                    ByteBuffer outputBuffer = videoCodec.getOutputBuffers()[outputBufferIndex];
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                    if (pts == 0) {
                        pts = bufferInfo.presentationTimeUs;
                    }
                    bufferInfo.presentationTimeUs = bufferInfo.presentationTimeUs - pts;
                    mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
                    Log.d(TAG, "视频秒数时间戳 = " + bufferInfo.presentationTimeUs / 1000000.0f);
                    if (bufferInfo != null)
                        listener.onMediaInfoListener((int) (bufferInfo.presentationTimeUs / 1000000));

                    videoCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = videoCodec.dequeueOutputBuffer(bufferInfo, 0);
                }
            }
        }
    }

    public void stopVideoCodec() {
        isStop = true;
    }
}
