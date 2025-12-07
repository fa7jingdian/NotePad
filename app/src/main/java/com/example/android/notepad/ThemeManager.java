package com.example.android.notepad;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 主题管理器，负责处理应用的主题切换和保存用户主题偏好
 */
public class ThemeManager {
    private static final String PREFERENCE_NAME = "theme_preferences";
    private static final String KEY_THEME = "current_theme";
    
    // 主题类型常量
    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    
    // 主题常量定义
    private static final int LIGHT_THEME_RES = R.style.AppTheme_Light;
    private static final int DARK_THEME_RES = R.style.AppTheme_Dark;
    
    /**
     * 保存用户选择的主题
     * @param context 上下文
     * @param themeType 主题类型（THEME_LIGHT 或 THEME_DARK）
     */
    public static void saveTheme(Context context, int themeType) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(KEY_THEME, themeType);
        editor.apply();
    }
    
    /**
     * 获取保存的主题类型
     * @param context 上下文
     * @return 主题类型
     */
    public static int getTheme(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        return preferences.getInt(KEY_THEME, THEME_LIGHT); // 默认使用浅色主题
    }
    
    /**
     * 根据主题类型获取对应的主题资源ID
     * @param context 上下文
     * @return 主题资源ID
     */
    public static int getThemeResId(Context context) {
        int themeType = getTheme(context);
        return themeType == THEME_DARK ? DARK_THEME_RES : LIGHT_THEME_RES;
    }
    
    /**
     * 切换主题
     * @param context 上下文
     */
    public static void toggleTheme(Context context) {
        int currentTheme = getTheme(context);
        int newTheme = (currentTheme == THEME_LIGHT) ? THEME_DARK : THEME_LIGHT;
        saveTheme(context, newTheme);
    }
    
    /**
     * 判断当前是否为深色主题
     * @param context 上下文
     * @return 如果是深色主题则返回true，否则返回false
     */
    public static boolean isDarkTheme(Context context) {
        return getTheme(context) == THEME_DARK;
    }
}