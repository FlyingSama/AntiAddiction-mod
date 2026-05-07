package com.antiaddiction.time;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 未成年人游玩时间判断工具
 *
 * 统一使用后端下发的 game_days 数据（含调休计算），
 * 不再依赖本地硬编码的周规则、节假日列表。
 */
public class PlayTimeChecker {

    private static final ZoneId CHINA_TZ = ZoneId.of("Asia/Shanghai");

    // date(YYYY-MM-DD) -> [playable, startHour, endHour, maxMinutes, isWorkdayOverride]
    private static volatile Map<String, int[]> GAME_DAYS = new HashMap<>();
    private static volatile int DEFAULT_START_HOUR = 19;
    private static volatile int DEFAULT_END_HOUR   = 21;
    private static long sessionStartMs = 0;

    private static LocalDateTime getNow() {
        return ZonedDateTime.now(CHINA_TZ).toLocalDateTime();
    }

    public static boolean isPlayAllowed() {
        LocalDateTime now = getNow();
        String today = now.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);

        int[] gd = GAME_DAYS.get(today);
        if (gd != null) {
            if (gd[0] == 0) return false;               // 标记为不可玩（含调休上班日）
            int hour = now.getHour();
            if (hour < gd[1] || hour >= gd[2]) return false; // 不在时间段内
            if (gd[3] > 0 && getPlayedMinutes() >= gd[3]) return false; // 超过限时
            return true;
        }

        // 未同步数据时的回退：周五/六/日，使用全局默认时段
        int dow = now.getDayOfWeek().getValue();
        if (dow != DayOfWeek.FRIDAY.getValue() &&
            dow != DayOfWeek.SATURDAY.getValue() &&
            dow != DayOfWeek.SUNDAY.getValue()) return false;
        int hour = now.getHour();
        return hour >= DEFAULT_START_HOUR && hour < DEFAULT_END_HOUR;
    }

    public static String getNextAllowedTime() {
        LocalDateTime now = getNow();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM月dd日（EEE）HH:mm", Locale.CHINESE);

        for (int i = 0; i <= 90; i++) {
            LocalDate day = now.toLocalDate().plusDays(i);
            String dateStr = day.format(DateTimeFormatter.ISO_LOCAL_DATE);
            int[] gd = GAME_DAYS.get(dateStr);

            int startH = gd != null ? gd[1] : DEFAULT_START_HOUR;
            if (gd != null && gd[0] == 1) {
                LocalDateTime candidate = day.atTime(startH, 0);
                if (candidate.isAfter(now)) return candidate.format(fmt);
            } else if (gd == null) {
                int dow = day.getDayOfWeek().getValue();
                if (dow == DayOfWeek.FRIDAY.getValue() ||
                    dow == DayOfWeek.SATURDAY.getValue() ||
                    dow == DayOfWeek.SUNDAY.getValue()) {
                    LocalDateTime candidate = day.atTime(DEFAULT_START_HOUR, 0);
                    if (candidate.isAfter(now)) return candidate.format(fmt);
                }
            }
        }
        return "暂无可玩日";
    }

    public static long getRemainingSeconds() {
        if (!isPlayAllowed()) return 0;
        return getRemainingSecondsForCountdown();
    }

    /** 获取剩余秒数（不计 isPlayAllowed 短路），供倒计时 HUD 使用 */
    public static long getRemainingSecondsForCountdown() {
        LocalDateTime now = getNow();
        String today = now.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        int[] gd = GAME_DAYS.get(today);

        int endHour = gd != null ? gd[2] : DEFAULT_END_HOUR;
        LocalDateTime end = now.toLocalDate().atTime(endHour, 0);
        long timeRemaining = Math.max(0, Duration.between(now, end).getSeconds());

        if (gd != null && gd[3] > 0) {
            long limitRemaining = Math.max(0, (gd[3] * 60L) - getPlayedSeconds());
            return Math.min(timeRemaining, limitRemaining);
        }
        return timeRemaining;
    }

    /** 由后端推送更新全部游戏日数据 */
    public static void updateGameDays(Map<String, int[]> data) {
        GAME_DAYS = new HashMap<>(data);
    }

    public static void updateDefaultHours(int startHour, int endHour) {
        DEFAULT_START_HOUR = startHour;
        DEFAULT_END_HOUR   = endHour;
    }

    public static int getDefaultStartHour() { return DEFAULT_START_HOUR; }
    public static int getDefaultEndHour()   { return DEFAULT_END_HOUR; }

    public static Map<String, int[]> getGameDays() {
        return new HashMap<>(GAME_DAYS);
    }

    public static void markSessionStart() {
        sessionStartMs = System.currentTimeMillis();
    }

    public static long getPlayedMinutes() {
        return getPlayedSeconds() / 60;
    }

    public static long getPlayedSeconds() {
        if (sessionStartMs == 0) return 0;
        return Math.max(0, (System.currentTimeMillis() - sessionStartMs) / 1000);
    }
}
