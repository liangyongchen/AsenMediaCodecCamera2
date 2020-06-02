package com.asen.codec.encoder;

/**
 * 录制编码回调监听
 */
public interface EncodeChangeListener {
    /**
     * 音视频合成状态回调 开始 -- 停止
     *
     * @param type int
     */
    void onMediaMuxerChangeListener(int type);

    /**
     * 视频录制时长回调
     *
     * @param time 时长
     */
    void onMediaInfoListener(int time);
}
