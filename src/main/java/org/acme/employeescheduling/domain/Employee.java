package org.acme.employeescheduling.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

public class Employee {
    @PlanningId
    private String name; //calısan kodu olarak kullanabılırım. BRST01, SM01…
     // --- YENİ ALANLAR ---
    private String storeCode;                // Mağaza kodu: A, B, C...
    private Role role;                       // STORE_MANAGER, BARISTA...
    private EmploymentType employmentType;   // FULL_TIME, PART_TIME
    private int weeklyMinutes;               // Mola hariç haftalık çalışma süresi (dakika)
    private int maxDailyMinutes;             // Mola dahil günlük max (dakika)
    // ---------------------
    private Set<String> skills;

    private Set<LocalDate> unavailableDates;
    private Set<LocalDate> undesiredDates;
    private Set<LocalDate> desiredDates;

    public Employee() {

    }

   /* public Employee(String name, Set<String> skills,
        Set<LocalDate> unavailableDates, Set<LocalDate> undesiredDates, Set<LocalDate> desiredDates) {
        this.name = name;
        
        this.skills = skills;
        this.unavailableDates = unavailableDates;
        this.undesiredDates = undesiredDates;
        this.desiredDates = desiredDates;
    }*/

    public Employee(String name,
                String storeCode,
                Role role,
                EmploymentType employmentType,
                int weeklyMinutes,
                int maxDailyMinutes,
                Set<String> skills,
                Set<LocalDate> unavailableDates,
                Set<LocalDate> undesiredDates,
                Set<LocalDate> desiredDates) {
    this.name = name;                      // çalışan kodu
    this.storeCode = storeCode;
    this.role = role;
    this.employmentType = employmentType;
    this.weeklyMinutes = weeklyMinutes;    // dakika cinsinden
    this.maxDailyMinutes = maxDailyMinutes;
    this.skills = skills;
    this.unavailableDates = unavailableDates;
    this.undesiredDates = undesiredDates;
    this.desiredDates = desiredDates;
}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public int getWeeklyMinutes() {
        return weeklyMinutes;
    }

    public void setWeeklyMinutes(int weeklyMinutes) {
        this.weeklyMinutes = weeklyMinutes;
    }

    public int getMaxDailyMinutes() {
        return maxDailyMinutes;
    }

    public void setMaxDailyMinutes(int maxDailyMinutes) {
        this.maxDailyMinutes = maxDailyMinutes;
    }


    public Set<String> getSkills() {
        return skills;
    }

    public void setSkills(Set<String> skills) {
        this.skills = skills;
    }

    public Set<LocalDate> getUnavailableDates() {
        return unavailableDates;
    }

    public void setUnavailableDates(Set<LocalDate> unavailableDates) {
        this.unavailableDates = unavailableDates;
    }

    public Set<LocalDate> getUndesiredDates() {
        return undesiredDates;
    }

    public void setUndesiredDates(Set<LocalDate> undesiredDates) {
        this.undesiredDates = undesiredDates;
    }

    public Set<LocalDate> getDesiredDates() {
        return desiredDates;
    }

    public void setDesiredDates(Set<LocalDate> desiredDates) {
        this.desiredDates = desiredDates;
    }

    // Küçük yardımcılar (constraint'lerde çok işe yarar):
    public boolean isFullTime() {
        return employmentType == EmploymentType.FULL_TIME;
    }

    public boolean isPartTime() {
        return employmentType == EmploymentType.PART_TIME;
    }


    @Override
    public String toString() {
        return name; // employeeCode gibi
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Employee employee)) {
            return false;
        }
        return Objects.equals(getName(), employee.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
