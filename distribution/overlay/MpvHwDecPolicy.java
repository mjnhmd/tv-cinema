package com.fongmi.android.tv.player.mpv;

/** Android 9 CCodec crashes in setSurface when MPV hands it a MediaCodec surface. */
public final class MpvHwDecPolicy {
    static final String SURFACE = "mediacodec,mediacodec-copy";
    static final String COPY = "mediacodec-copy";
    private static final int CRASHING_MAX_SDK = 28;

    private MpvHwDecPolicy() {
    }

    public static String forSdk(int sdk) {
        return sdk <= CRASHING_MAX_SDK ? COPY : SURFACE;
    }
}
