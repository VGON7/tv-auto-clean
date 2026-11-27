package com.tvautoclean.guard;

import android.view.KeyEvent;

/** Friendly Chinese names and safety rules for remote-control keys. */
public final class KeyNames {
    private KeyNames() {
    }

    /**
     * Keys that must keep their normal TV function. In particular Back is needed
     * to leave this screen, and volume/power are system keys that accessibility
     * services should not swallow.
     */
    public static boolean isDisallowed(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_UNKNOWN:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return true;
            default:
                return keyCode <= 0;
        }
    }

    public static String label(int keyCode) {
        if (keyCode <= 0) {
            return "尚未设置";
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                return "菜单键";
            case KeyEvent.KEYCODE_INFO:
                return "信息键";
            case KeyEvent.KEYCODE_GUIDE:
                return "节目指南键";
            case KeyEvent.KEYCODE_TV:
                return "电视键";
            case KeyEvent.KEYCODE_TV_INPUT:
                return "信号源键";
            case KeyEvent.KEYCODE_STB_INPUT:
                return "机顶盒信号源键";
            case KeyEvent.KEYCODE_AVR_INPUT:
                return "音响信号源键";
            case KeyEvent.KEYCODE_SETTINGS:
                return "设置键";
            case KeyEvent.KEYCODE_BOOKMARK:
                return "书签键";
            case KeyEvent.KEYCODE_SEARCH:
                return "搜索键";
            case KeyEvent.KEYCODE_ASSIST:
                return "语音助手键";
            case KeyEvent.KEYCODE_APP_SWITCH:
                return "应用切换键";
            case KeyEvent.KEYCODE_PROG_RED:
                return "红色功能键";
            case KeyEvent.KEYCODE_PROG_GREEN:
                return "绿色功能键";
            case KeyEvent.KEYCODE_PROG_YELLOW:
                return "黄色功能键";
            case KeyEvent.KEYCODE_PROG_BLUE:
                return "蓝色功能键";
            case KeyEvent.KEYCODE_CHANNEL_UP:
                return "频道 + 键";
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                return "频道 - 键";
            case KeyEvent.KEYCODE_LAST_CHANNEL:
                return "回看键";
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return "播放 / 暂停键";
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                return "播放键";
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                return "暂停键";
            case KeyEvent.KEYCODE_MEDIA_STOP:
                return "停止键";
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return "下一曲键";
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return "上一曲键";
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return "快退键";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return "快进键";
            case KeyEvent.KEYCODE_MEDIA_RECORD:
                return "录制键";
            default:
                break;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return "数字键 " + (keyCode - KeyEvent.KEYCODE_0);
        }
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return "字母键 " + (char) ('A' + (keyCode - KeyEvent.KEYCODE_A));
        }
        String name = KeyEvent.keyCodeToString(keyCode);
        if (name == null || name.length() == 0 || "KEYCODE_UNKNOWN".equals(name)) {
            return "按键代码 " + keyCode;
        }
        return name.replace("KEYCODE_", "").replace('_', ' ');
    }
}
