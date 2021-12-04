package com.sero583.onevsonerm.utils;

/**
 * @author Serhat G. (sero583)
 */
public final class TimeHelper {
    public static boolean hasTimePassed(long till) {
        return (getTime() - till) <= 0;
    }

    /**
     * Check if time has passed/ran out in comparison to now
     * @param then - start time
     * @param durationMs - duration in milliseconds
     * @return result
     */
    public static boolean hasTimePassed(long then, long durationMs) {
        return hasTimePassed(then, durationMs, null);
    }

    /**
     * Check if time has passed/ran out.
     * @param then - start time
     * @param durationMs - duration in milliseconds
     * @param now - if null, current time will be used
     * @return result
     */
    public static boolean hasTimePassed(long then, long durationMs, Long now) {
        return ((now == null ? getTime() : now) - then) >= durationMs;
    }

    /**
     * Get current time
     * @return timestamp
     */
    public static long getTime() {
        return System.currentTimeMillis();
    }

    /**
     * Displays time in a nice format, efficiently using StringBuilder
     * @param timeInSeconds
     * @return formatted time string
     */
    public static String displayTime(int timeInSeconds) {
        StringBuilder builder = new StringBuilder();

        int secondsLeft = timeInSeconds % 3600 % 60;
        int minutes = (int) Math.floor(timeInSeconds % 3600 / 60);
        int hours = (int) Math.floor(timeInSeconds / 3600);

        String MM = ((minutes < 10) ? "0" : "") + minutes;
        String SS = ((secondsLeft < 10) ? "0" : "") + secondsLeft;

        if(hours>0) {
            // show only then
            String HH = ((hours < 10) ? "0" : "") + hours;
            builder.append(HH);
            builder.append(":");
        }

        builder.append(MM);
        builder.append(":");

        builder.append(SS);

        return builder.toString();

    }
}
