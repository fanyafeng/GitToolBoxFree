package com.pom.gittoolbox.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 专为中文开发者定制的相对时间格式化工具：
 * 采用不可变、线程安全的 DateTimeFormatter，消除 SimpleDateFormat 反复实例化的 GC 开销
 */
public class ChineseTimeAgo {

    private static final long ONE_MINUTE = 60 * 1000L;
    private static final long ONE_HOUR = 60 * ONE_MINUTE;
    private static final long ONE_DAY = 24 * ONE_HOUR;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 将秒级时间戳转换为中文相对时间
     */
    public static String formatFromSeconds(long epochSeconds) {
        return formatFromMillis(epochSeconds * 1000L);
    }

    /**
     * 将毫秒级时间戳转换为中文相对时间
     */
    public static String formatFromMillis(long timeMillis) {
        if (timeMillis <= 0) {
            return "未知时间";
        }

        long now = System.currentTimeMillis();
        long diff = now - timeMillis;

        if (diff < ONE_MINUTE) {
            return "刚刚";
        }
        if (diff < ONE_HOUR) {
            long minutes = diff / ONE_MINUTE;
            return minutes + "分钟前";
        }

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime nowTime = Instant.ofEpochMilli(now).atZone(zone);
        ZonedDateTime targetTime = Instant.ofEpochMilli(timeMillis).atZone(zone);

        int nowYear = nowTime.getYear();
        int targetYear = targetTime.getYear();
        int nowDayOfYear = nowTime.getDayOfYear();
        int targetDayOfYear = targetTime.getDayOfYear();

        if (nowYear == targetYear) {
            int dayDiff = nowDayOfYear - targetDayOfYear;
            if (dayDiff == 0) {
                long hours = diff / ONE_HOUR;
                return hours + "小时前";
            } else if (dayDiff == 1) {
                return "昨天 " + TIME_FORMAT.format(targetTime);
            } else if (dayDiff == 2) {
                return "前天 " + TIME_FORMAT.format(targetTime);
            } else if (dayDiff < 30) {
                return dayDiff + "天前";
            } else if (dayDiff < 365) {
                int months = dayDiff / 30;
                return (months <= 0 ? 1 : months) + "个月前";
            }
        }

        int yearDiff = nowYear - targetYear;
        if (yearDiff <= 1 && diff < 365 * ONE_DAY) {
            int months = (int) (diff / (30 * ONE_DAY));
            return (months <= 0 ? 1 : months) + "个月前";
        }

        if (yearDiff > 0) {
            return yearDiff + "年前";
        }

        return DATE_FORMAT.format(targetTime);
    }

    /**
     * 格式化为标准绝对日期 (yyyy-MM-dd HH:mm)
     */
    public static String formatAbsoluteDate(long epochSeconds) {
        if (epochSeconds <= 0) return "";
        ZonedDateTime dt = Instant.ofEpochMilli(epochSeconds * 1000L).atZone(ZoneId.systemDefault());
        return DATE_TIME_FORMAT.format(dt);
    }
}
