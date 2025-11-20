package org.acme.employeescheduling.domain;

public class Rules {

    // dakika cinsinden; örn: 55 saat = 3300 dk
    // Rules.java -> alanlar
    private long targetWeeklyMinutes = 3300; // 55 saat hedef
    public long getTargetWeeklyMinutes() { return targetWeeklyMinutes; }
    public void setTargetWeeklyMinutes(long v) { this.targetWeeklyMinutes = v; }
    
    //Günlük maximum 11 saat çalışma sınırı
    private long dailyMaxMinutes = 11 * 60;

    public Rules() {}

    public Rules(long maxWeeklyMinutes) {
        this.maxWeeklyMinutes = maxWeeklyMinutes;
    }

    public long getMaxWeeklyMinutes() {
        return maxWeeklyMinutes;
    }

    public void setMaxWeeklyMinutes(long maxWeeklyMinutes) {
        this.maxWeeklyMinutes = maxWeeklyMinutes;
    }

    public long getDailyMaxMinutes() { return dailyMaxMinutes; }
    public void setDailyMaxMinutes(long v) { this.dailyMaxMinutes = v; }
}
