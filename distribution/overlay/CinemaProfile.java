package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.setting.BrowseExperienceSettings;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Prefers;

public final class CinemaProfile {

    private CinemaProfile() {
    }

    public static void initialize() {
        // Public LogVar instance: DanDanPlay-compatible search and XML comment endpoints.
        if (!Prefers.getPrefers().contains("cinema_danmaku_defaults_v1")) {
            if (!Prefers.getPrefers().contains("danmaku_logvar_url")) {
                DanmakuSetting.putLogvarUrl("https://dmfl.us.ci/87654321");
            }
            if (!Prefers.getPrefers().contains("danmaku_load")) DanmakuSetting.putLoad(true);
            if (!Prefers.getPrefers().contains("danmaku_auto")) DanmakuSetting.putAuto(true);
            Prefers.put("cinema_danmaku_defaults_v1", true);
        }
        // Applies to existing installs too: the profile marker was already written by 0.1.x.
        if (!Prefers.getPrefers().contains("cinema_low_end_preload_v1")) {
            if (!Prefers.getPrefers().contains("preload_next")) PreloadSetting.putNextEpisode(false);
            Prefers.put("cinema_low_end_preload_v1", true);
        }
        if (Prefers.getBoolean("cinema_profile_initialized")) return;
        if (!Prefers.getPrefers().contains("theme_color")) Setting.putThemeColor(Setting.THEME_AMBER_GOLD);
        if (!Prefers.getPrefers().contains("browse_hero_rotation")) {
            BrowseExperienceSettings.putHeroRotationMode(BrowseExperienceSettings.HERO_ROTATION_MANUAL);
        }
        Prefers.put("cinema_profile_initialized", true);
    }

    public static boolean isLightweight() {
        return Prefers.getBoolean("cinema_lightweight", true);
    }
}
