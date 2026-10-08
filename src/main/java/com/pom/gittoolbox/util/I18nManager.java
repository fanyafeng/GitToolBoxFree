package com.pom.gittoolbox.util;

import com.pom.gittoolbox.settings.GitToolBoxSettings;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 国际化多语言管理工具（支持中文与英文无缝切换，采用静态高性能 DateTimeFormatter）
 */
public class I18nManager {

    private static final long ONE_MINUTE = 60 * 1000L;
    private static final long ONE_HOUR = 60 * ONE_MINUTE;
    private static final long ONE_DAY = 24 * ONE_HOUR;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static boolean isEnglish() {
        GitToolBoxSettings.State state = GitToolBoxSettings.getInstance().getState();
        return state != null && "en".equalsIgnoreCase(state.language);
    }

    /**
     * 根据设置的语言格式化相对时间
     */
    public static String formatTimeAgo(long epochSeconds) {
        if (epochSeconds <= 0) {
            return isEnglish() ? "unknown" : "未知时间";
        }

        long timeMillis = epochSeconds * 1000L;
        long now = System.currentTimeMillis();
        long diff = now - timeMillis;

        boolean en = isEnglish();

        if (diff < ONE_MINUTE) {
            return en ? "just now" : "刚刚";
        }
        if (diff < ONE_HOUR) {
            long minutes = diff / ONE_MINUTE;
            return en ? minutes + "m ago" : minutes + "分钟前";
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
                return en ? hours + "h ago" : hours + "小时前";
            } else if (dayDiff == 1) {
                String timeStr = TIME_FORMAT.format(targetTime);
                return en ? "yesterday " + timeStr : "昨天 " + timeStr;
            } else if (dayDiff == 2) {
                String timeStr = TIME_FORMAT.format(targetTime);
                return en ? "2d ago" : "前天 " + timeStr;
            } else if (dayDiff < 30) {
                return en ? dayDiff + "d ago" : dayDiff + "天前";
            } else if (dayDiff < 365) {
                int months = Math.max(1, dayDiff / 30);
                return en ? months + "mo ago" : months + "个月前";
            }
        }

        int yearDiff = nowYear - targetYear;
        if (yearDiff <= 1 && diff < 365 * ONE_DAY) {
            int months = (int) (diff / (30 * ONE_DAY));
            months = Math.max(1, months);
            return en ? months + "mo ago" : months + "个月前";
        }

        if (yearDiff > 0) {
            return en ? yearDiff + "y ago" : yearDiff + "年前";
        }

        return DATE_FORMAT.format(targetTime);
    }

    public static String getStatusUpToDate() {
        return isEnglish() ? "[up to date]" : "[已最新]";
    }

    public static String getStatusNoUpstream() {
        return isEnglish() ? "[no upstream]" : "[未关联远程]";
    }

    public static String getStatusAhead(int count) {
        return isEnglish() ? "↑" + count + " ahead" : "↑" + count + " 超前";
    }

    public static String getStatusBehind(int count) {
        return isEnglish() ? "↓" + count + " behind" : "↓" + count + " 落后";
    }

    public static String getMenuPull() {
        return isEnglish() ? "📥 Pull (Git Pull)" : "📥 立即拉取最新代码 (Git Pull)";
    }

    public static String getMenuPush() {
        return isEnglish() ? "📤 Push (Git Push)" : "📤 推送本地提交 (Git Push)";
    }

    public static String getMenuRefresh() {
        return isEnglish() ? "🔄 Refresh Status (Fetch & Refresh)" : "🔄 立即刷新状态 (Fetch & Refresh)";
    }
}
