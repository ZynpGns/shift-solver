 package org.acme.employeescheduling.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class Shift {
    @PlanningId
    private String id;

    private LocalDateTime start;
    private LocalDateTime end; //vardiya zamanı

    private String location; //bizde departman bilgisi
    private String requiredSkill;

    @PlanningVariable
    private Employee employee;

    public Shift() {
    }

    public Shift(LocalDateTime start, LocalDateTime end, String location, String requiredSkill) {
        this(start, end, location, requiredSkill, null);
    }

    public Shift(LocalDateTime start, LocalDateTime end, String location, String requiredSkill, Employee employee) {
        this(null, start, end, location, requiredSkill, employee);
    }

    public Shift(String id, LocalDateTime start, LocalDateTime end, String location, String requiredSkill, Employee employee) {
        this.id = id;
        this.start = start;
        this.end = end;
        this.location = location;
        this.requiredSkill = requiredSkill;
        this.employee = employee;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getRequiredSkill() {
        return requiredSkill;
    }

    public void setRequiredSkill(String requiredSkill) {
        this.requiredSkill = requiredSkill;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

        // Toplam vardiya süresi (mola DAHİL), dakika cinsinden
    public int getDurationMinutes() {
        return (int) ChronoUnit.MINUTES.between(start, end);
    }

    // Mola süresi (senin verdiğin tabloya göre)
    public int getBreakMinutes() {
        int duration = getDurationMinutes();
        if (duration <= 4 * 60) {              // 4 saat ve altı
            return 15;
        } else if (duration <= 6 * 60) {       // 4-6 saat arası
            return 30;
        } else if (duration <= 11 * 60) {      // 6-11 saat arası
            return 60;
        } else {                               // 11 saat üstü
            return 120;
        }
    }

    // Mola HARİÇ gerçek çalışma süresi (dakika)
    public int getWorkMinutesWithoutBreak() {
        return getDurationMinutes() - getBreakMinutes();
    }

    
    //Belirli bir günle çakışıyor mu?
    public boolean isOverlappingWithDate(LocalDate date) {
        return getStart().toLocalDate().equals(date) || getEnd().toLocalDate().equals(date);
    }
    //Bir günde kaç dakika çalışıyor?
    public int getOverlappingDurationInMinutes(LocalDate date) {
        LocalDateTime startDateTime = LocalDateTime.of(date, LocalTime.MIN);
        LocalDateTime endDateTime = LocalDateTime.of(date, LocalTime.MAX);
        return getOverlappingDurationInMinutes(startDateTime, endDateTime, getStart(), getEnd());
    }

    private int getOverlappingDurationInMinutes(LocalDateTime firstStartDateTime, LocalDateTime firstEndDateTime,
            LocalDateTime secondStartDateTime, LocalDateTime secondEndDateTime) {
        LocalDateTime maxStartTime = firstStartDateTime.isAfter(secondStartDateTime) ? firstStartDateTime : secondStartDateTime;
        LocalDateTime minEndTime = firstEndDateTime.isBefore(secondEndDateTime) ? firstEndDateTime : secondEndDateTime;
        long minutes = maxStartTime.until(minEndTime, ChronoUnit.MINUTES);
        return minutes > 0 ? (int) minutes : 0;
    }
    //ileride buraya shiftType eklenebilir(SABAH / AKŞAM / GECE gibi bir enum)
    @Override
    public String toString() {
        return location + " " + start + "-" + end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Shift shift)) {
            return false;
        }
        return Objects.equals(getId(), shift.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
}
