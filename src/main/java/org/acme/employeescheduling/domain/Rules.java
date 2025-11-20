package org.acme.employeescheduling.domain;

public class Rules {

    // ≤ 55 saat (üst sınır)
    private long maxWeeklyMinutes = 3300;

    // ≤ 11 saat (günlük üst sınır)
    private long dailyMaxMinutes = 11 * 60;

    // 55 saate YAKLAŞ hedefi (soft kural için)
    private long targetWeeklyMinutes = 3300;

    public Rules() {}

    public Rules(long maxWeeklyMinutes) {
        this.maxWeeklyMinutes = maxWeeklyMinutes;
    }

    public Rules(long maxWeeklyMinutes, long dailyMaxMinutes, long targetWeeklyMinutes) {
        this.maxWeeklyMinutes = maxWeeklyMinutes;
        this.dailyMaxMinutes = dailyMaxMinutes;
        this.targetWeeklyMinutes = targetWeeklyMinutes;
    }

    public long getMaxWeeklyMinutes() { return maxWeeklyMinutes; }
    public void setMaxWeeklyMinutes(long v) { this.maxWeeklyMinutes = v; }

    public long getDailyMaxMinutes() { return dailyMaxMinutes; }
    public void setDailyMaxMinutes(long v) { this.dailyMaxMinutes = v; }

    public long getTargetWeeklyMinutes() { return targetWeeklyMinutes; }
    public void setTargetWeeklyMinutes(long v) { this.targetWeeklyMinutes = v; }
}
