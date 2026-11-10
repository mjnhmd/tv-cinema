package com.fongmi.android.tv.player.mpv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MpvHwDecPolicyTest {
    @Test
    public void usesCopyModeWhereSurfaceConfigurationCrashes() {
        assertEquals("mediacodec-copy", MpvHwDecPolicy.forSdk(24));
        assertEquals("mediacodec-copy", MpvHwDecPolicy.forSdk(28));
        assertEquals("mediacodec,mediacodec-copy", MpvHwDecPolicy.forSdk(29));
        assertEquals("mediacodec,mediacodec-copy", MpvHwDecPolicy.forSdk(36));
    }
}
