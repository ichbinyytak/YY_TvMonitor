package com.tv.cpu;

import android.content.Context;
import android.content.SharedPreferences;

public class HudConfig {
    public static final String PREF_NAME = "tv_cpu_config";

    public static final String KEY_ALPHA = "alpha";
    public static final String KEY_MINIMAL_MODE = "minimal_mode"; // true: 简洁模式, false: 详细模式
    public static final String KEY_TEXT_SIZE_SP = "text_size_sp"; // 连续字号大小 (5 ~ 24 SP)

    // 自选显示内容开关
    public static final String KEY_SHOW_CPU = "show_cpu";
    public static final String KEY_SHOW_GPU = "show_gpu";
    public static final String KEY_SHOW_RAM = "show_ram";
    public static final String KEY_SHOW_FREQ = "show_freq";
    public static final String KEY_SHOW_NET_SPEED = "show_net_speed";

    public static final String ACTION_CONFIG_CHANGED = "com.tv.cpu.ACTION_CONFIG_CHANGED";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static int getAlpha(Context context) {
        return getPrefs(context).getInt(KEY_ALPHA, 50); // 默认 50%
    }

    public static void setAlpha(Context context, int alpha) {
        getPrefs(context).edit().putInt(KEY_ALPHA, Math.max(10, Math.min(100, alpha))).apply();
    }

    public static boolean isMinimalMode(Context context) {
        return getPrefs(context).getBoolean(KEY_MINIMAL_MODE, true); // 默认简洁模式
    }

    public static void setMinimalMode(Context context, boolean minimal) {
        getPrefs(context).edit().putBoolean(KEY_MINIMAL_MODE, minimal).apply();
    }

    public static int getTextSizeSp(Context context) {
        return getPrefs(context).getInt(KEY_TEXT_SIZE_SP, 13); // 默认 13sp
    }

    public static void setTextSizeSp(Context context, int sp) {
        getPrefs(context).edit().putInt(KEY_TEXT_SIZE_SP, Math.max(5, Math.min(24, sp))).apply();
    }

    public static boolean isShowCpu(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_CPU, true);
    }

    public static void setShowCpu(Context context, boolean show) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_CPU, show).apply();
    }

    public static boolean isShowGpu(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_GPU, true);
    }

    public static void setShowGpu(Context context, boolean show) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_GPU, show).apply();
    }

    public static boolean isShowRam(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_RAM, true);
    }

    public static void setShowRam(Context context, boolean show) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_RAM, show).apply();
    }

    public static boolean isShowFreq(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_FREQ, true);
    }

    public static void setShowFreq(Context context, boolean show) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_FREQ, show).apply();
    }

    public static boolean isShowNetSpeed(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_NET_SPEED, true);
    }

    public static void setShowNetSpeed(Context context, boolean show) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_NET_SPEED, show).apply();
    }
}
