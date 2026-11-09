package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.setting.BrowseExperienceSettings;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Prefers;

public final class CinemaProfile {

    private CinemaProfile() {
    }

    public static void initialize() {
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
