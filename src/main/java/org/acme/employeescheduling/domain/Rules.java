package org.acme.employeescheduling.domain;

public class Rules {

    // dakika cinsinden; örn: 55 saat = 3300 dk
    private long maxWeeklyMinutes = 3300;
    
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
