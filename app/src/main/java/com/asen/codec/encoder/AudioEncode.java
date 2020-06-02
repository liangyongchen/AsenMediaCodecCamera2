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
 * 音频写入thread
 */
public class AudioEncode extends Thread {

    private static final String TAG = AudioEncode.class.getName();
    /**
     * 编码器
     */
    private MediaCodec audioCodec;
    private MediaCodec.BufferInfo bufferInfo;
    // 合成器
    private MediaMuxer mediaMuxer;
    // 是否结束
    private boolean isStop;
    // 时长
    private long pts;
    // 回调监听
    private EncodeChangeListener listener;

    AudioEncode(MediaCodec mediaCodec, MediaCodec.BufferInfo bufferInfo, MediaMuxer mediaMuxer,
                @NonNull EncodeChangeListener listener) {
        this.audioCodec = mediaCodec;
        this.bufferInfo = bufferInfo;
        this.mediaMuxer = mediaMuxer;
        this.listener = listener;
        pts = 0;
        audioTrackIndex = -1;
    }

    @Override
    public void run() {
        super.run();
        isStop = false;
        audioCodec.start();
        while (true) {
            if (isStop) {
                audioCodec.stop();
                audioCodec.release();
                audioCodec = null;
                EncodeConfig.audioStop = true;

                if (EncodeConfig.videoStop) {
                    mediaMuxer.stop();
                    mediaMuxer.release();
                    mediaMuxer = null;
                    listener.onMediaMuxerChangeListener(EncodeConfig.MUXER_STOP);
                    break;
                }
            }

            if (audioCodec == null)
                break;
            int outputBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 0);
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                audioTrackIndex = mediaMuxer.addTrack(audioCodec.getOutputFormat());
                if (videoTrackIndex != -1) {
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

                    ByteBuffer outputBuffer = audioCodec.getOutputBuffers()[outputBufferIndex];
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                    if (pts == 0) {
                        pts = bufferInfo.presentationTimeUs;
                    }
                    bufferInfo.presentationTimeUs = bufferInfo.presentationTimeUs - pts;
                    mediaMuxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo);

                    audioCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 0);
                }
            }
        }
    }

    void stopAudioCodec() {
        isStop = true;
    }
}
