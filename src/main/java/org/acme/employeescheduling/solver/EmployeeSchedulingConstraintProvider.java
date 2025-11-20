package org.acme.employeescheduling.solver;

import static ai.timefold.solver.core.api.score.stream.Joiners.equal;
import static ai.timefold.solver.core.api.score.stream.Joiners.lessThanOrEqual;
import static ai.timefold.solver.core.api.score.stream.Joiners.overlapping;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.function.Function;

import ai.timefold.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
import ai.timefold.solver.core.api.score.stream.*;
import ai.timefold.solver.core.api.score.stream.common.LoadBalance;

import org.acme.employeescheduling.domain.Employee;
import org.acme.employeescheduling.domain.Shift;
import org.acme.employeescheduling.domain.Rules;

import ai.timefold.solver.core.api.score.stream.ConstraintCollectors

import ai.timefold.solver.core.api.score.stream.common.LoadBalance;


public class EmployeeSchedulingConstraintProvider implements ConstraintProvider {

    private static int getMinuteOverlap(Shift shift1, Shift shift2) {
        LocalDateTime shift1Start = shift1.getStart();
        LocalDateTime shift1End = shift1.getEnd();
        LocalDateTime shift2Start = shift2.getStart();
        LocalDateTime shift2End = shift2.getEnd();
        return (int) Duration.between(
                (shift1Start.isAfter(shift2Start)) ? shift1Start : shift2Start,
                (shift1End.isBefore(shift2End)) ? shift1End : shift2End
        ).toMinutes();
    }

    private static boolean overlapsWindow(LocalDateTime s, LocalDateTime e,
                                          java.time.LocalDate date, int sh, int sm, int eh, int em) {
        var ws = LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), sh, sm);
        var we = LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), eh, em);
        return !e.isBefore(ws) && !s.isAfter(we);
    }

    private long minutesOf(Shift s) {
        return Duration.between(s.getStart(), s.getEnd()).toMinutes();
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                // Hard constraints
                requiredSkill(constraintFactory),
                noOverlappingShifts(constraintFactory),
                atLeast10HoursBetweenTwoShifts(constraintFactory),
                oneShiftPerDay(constraintFactory),
                unavailableEmployee(constraintFactory),

                weeklyMaxMinutes(constraintFactory),
                dailyMaxMinutes(constraintFactory),

                // Haftada TAM 6 gün (1 gün OFF)
                weeklyMinWorkingDays(constraintFactory),
                weeklyMaxWorkingDays(constraintFactory),

                // Hafta sonu çalışma zorunluluğu
                mustWorkSaturday(constraintFactory),
                mustWorkSunday(constraintFactory),

                // Atanmamış vardiya yasak
                unassignedShiftForbidden(constraintFactory),

                // Soft constraints
                undesiredDayForEmployee(constraintFactory),
                desiredDayForEmployee(constraintFactory),
                balanceEmployeeShiftAssignments(constraintFactory),

                // 55 saate YAKLAŞ (soft hedef)
                weeklyTargetMinutesProximity(constraintFactory)
        };
    }

    // ----- HARD -----
    Constraint requiredSkill(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
                .filter(shift -> !shift.getEmployee().getSkills().contains(shift.getRequiredSkill()))
                .penalize(HardSoftBigDecimalScore.ONE_HARD)
                .asConstraint("Missing required skill");
    }

    Constraint noOverlappingShifts(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachUniquePair(Shift.class, equal(Shift::getEmployee),
                        overlapping(Shift::getStart, Shift::getEnd))
                .penalize(HardSoftBigDecimalScore.ONE_HARD,
                        EmployeeSchedulingConstraintProvider::getMinuteOverlap)
                .asConstraint("Overlapping shift");
    }

    Constraint atLeast10HoursBetweenTwoShifts(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
                .join(Shift.class, equal(Shift::getEmployee), lessThanOrEqual(Shift::getEnd, Shift::getStart))
                .filter((firstShift, secondShift) ->
                        Duration.between(firstShift.getEnd(), secondShift.getStart()).toHours() < 10)
                .penalize(HardSoftBigDecimalScore.ONE_HARD,
                        (firstShift, secondShift) -> {
                            int breakLength = (int) Duration.between(firstShift.getEnd(), secondShift.getStart()).toMinutes();
                            return (10 * 60) - breakLength;
                        })
                .asConstraint("At least 10 hours between 2 shifts");
    }

    Constraint oneShiftPerDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachUniquePair(Shift.class, equal(Shift::getEmployee),
                        equal(shift -> shift.getStart().toLocalDate()))
                .penalize(HardSoftBigDecimalScore.ONE_HARD)
                .asConstraint("Max one shift per day");
    }

    Constraint unavailableEmployee(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
                .join(Employee.class, equal(Shift::getEmployee, Function.identity()))
                .flattenLast(Employee::getUnavailableDates)
                .filter(Shift::isOverlappingWithDate)
                .penalize(HardSoftBigDecimalScore.ONE_HARD, Shift::getOverlappingDurationInMinutes)
                .asConstraint("Unavailable employee");
    }

    Constraint weeklyMaxMinutes(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .groupBy(
                        Shift::getEmployee,
                        s -> s.getStart().toLocalDate()
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        ConstraintCollectors.sumLong(s -> Duration.between(s.getStart(), s.getEnd()).toMinutes())
                )
                .join(Rules.class)
                .filter((emp, weekStart, minutes, rules) -> minutes > rules.getMaxWeeklyMinutes())
                .penalize(HardSoftBigDecimalScore.ONE_HARD,
                        (emp, weekStart, minutes, rules) -> (int) (minutes - rules.getMaxWeeklyMinutes()))
                .asConstraint("Weekly max minutes");
    }

    Constraint dailyMaxMinutes(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .groupBy(
                        Shift::getEmployee,
                        s -> s.getStart().toLocalDate(),
                        ConstraintCollectors.sumLong(s -> Duration.between(s.getStart(), s.getEnd()).toMinutes())
                )
                .join(Rules.class)
                .filter((emp, day, minutes, rules) -> minutes > rules.getDailyMaxMinutes())
                .penalize(HardSoftBigDecimalScore.ONE_HARD,
                        (emp, day, minutes, rules) -> (int) (minutes - rules.getDailyMaxMinutes()))
                .asConstraint("Daily max minutes");
    }

    // Haftada en az 6 farklı gün çalışma (>=6)
    Constraint weeklyMinWorkingDays(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .groupBy(
                        Shift::getEmployee,
                        s -> s.getStart().toLocalDate()
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        ConstraintCollectors.countDistinct(s -> s.getStart().toLocalDate())
                )
                .filter((emp, weekStart, workedDays) -> workedDays < 6)
                .penalize(HardSoftBigDecimalScore.ONE_HARD,
                        (emp, weekStart, workedDays) -> 6 - workedDays)
                .asConstraint("Weekly min working days (>=6)");
    }

    // Haftada en fazla 6 farklı gün çalışma (<=6) => TAM 6 gün için min+max birlikte
    Constraint weeklyMaxWorkingDays(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .groupBy(
                        Shift::getEmployee,
                        s -> s.getStart().toLocalDate()
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        ConstraintCollectors.countDistinct(s -> s.getStart().toLocalDate())
                )
                .filter((emp, weekStart, workedDays) -> workedDays > 6)
                .penalize(HardSoftBigDecimalScore.ONE_HARD,
                        (emp, weekStart, workedDays) -> workedDays - 6)
                .asConstraint("Weekly max working days (<=6)");
    }

    // Cumartesi mutlaka çalış
    Constraint mustWorkSaturday(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .groupBy(
                        Shift::getEmployee,
                        s -> s.getStart().toLocalDate()
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        ConstraintCollectors.sumLong(s -> s.getStart().getDayOfWeek() == DayOfWeek.SATURDAY ? 1L : 0L)
                )
                .filter((emp, weekStart, satCount) -> satCount == 0L)
                .penalize(HardSoftBigDecimalScore.ONE_HARD)
                .asConstraint("Must work on Saturday");
    }

    // Pazar mutlaka çalış
    Constraint mustWorkSunday(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .groupBy(
                        Shift::getEmployee,
                        s -> s.getStart().toLocalDate()
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        ConstraintCollectors.sumLong(s -> s.getStart().getDayOfWeek() == DayOfWeek.SUNDAY ? 1L : 0L)
                )
                .filter((emp, weekStart, sunCount) -> sunCount == 0L)
                .penalize(HardSoftBigDecimalScore.ONE_HARD)
                .asConstraint("Must work on Sunday");
    }

    // Atanmamış vardiya yasak
    private Constraint unassignedShiftForbidden(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .filter(s -> s.getEmployee() == null)
                .penalize(HardSoftBigDecimalScore.ONE_HARD)
                .asConstraint("Unassigned shift forbidden");
    }

    // İstersen bu peak kuralları tekrar açılabilir
    Constraint peakTueManager(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .filter(s ->
                        s.getStart().getDayOfWeek() == DayOfWeek.TUESDAY &&
                                overlapsWindow(s.getStart(), s.getEnd(), s.getStart().toLocalDate(), 14, 0, 20, 0) &&
                                "MANAGER".equalsIgnoreCase(s.getRequiredSkill()))
                .groupBy(s -> s.getStart().toLocalDate(), ConstraintCollectors.count())
                .filter((date, managerCount) -> managerCount < 1)
                .penalize(HardSoftBigDecimalScore.ONE_HARD, (date, c) -> 1 - c)
                .asConstraint("Tue 14-20 at least one MANAGER");
    }

    Constraint peakTueTotal(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .filter(s ->
                        s.getStart().getDayOfWeek() == DayOfWeek.TUESDAY &&
                                overlapsWindow(s.getStart(), s.getEnd(), s.getStart().toLocalDate(), 14, 0, 20, 0))
                .groupBy(s -> s.getStart().toLocalDate(), ConstraintCollectors.count())
                .filter((date, total) -> total < 5)
                .penalize(HardSoftBigDecimalScore.ONE_HARD, (date, total) -> 5 - total)
                .asConstraint("Tue 14-20 at least five total");
    }

    Constraint peakSatManager(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .filter(s ->
                        s.getStart().getDayOfWeek() == DayOfWeek.SATURDAY &&
                                overlapsWindow(s.getStart(), s.getEnd(), s.getStart().toLocalDate(), 14, 0, 20, 0) &&
                                "MANAGER".equalsIgnoreCase(s.getRequiredSkill()))
                .groupBy(s -> s.getStart().toLocalDate(), ConstraintCollectors.count())
                .filter((date, managerCount) -> managerCount < 1)
                .penalize(HardSoftBigDecimalScore.ONE_HARD, (date, c) -> 1 - c)
                .asConstraint("Sat 14-20 at least one MANAGER");
    }

    Constraint peakSatTotal(ConstraintFactory f) {
        return f.forEach(Shift.class)
                .filter(s ->
                        s.getStart().getDayOfWeek() == DayOfWeek.SATURDAY &&
                                overlapsWindow(s.getStart(), s.getEnd(), s.getStart().toLocalDate(), 14, 0, 20, 0))
                .groupBy(s -> s.getStart().toLocalDate(), ConstraintCollectors.count())
                .filter((date, total) -> total < 5)
                .penalize(HardSoftBigDecimalScore.ONE_HARD, (date, total) -> 5 - total)
                .asConstraint("Sat 14-20 at least five total");
    }

    // ----- SOFT -----
    private Constraint weeklyTargetMinutesProximity(ConstraintFactory factory) {
        var byEmployeeWorkedMins = factory.forEach(Shift.class)
                .filter(s -> s.getEmployee() != null)
                .groupBy(Shift::getEmployee, ConstraintCollectors.sumLong(this::minutesOf));

        return byEmployeeWorkedMins
                .join(factory.forEach(Rules.class))
                .penalizeBigDecimal(
                        HardSoftBigDecimalScore.ONE_SOFT,
                        (emp, workedMinutes, rules) -> BigDecimal.valueOf(
                                Math.abs(workedMinutes - rules.getTargetWeeklyMinutes())))
                .asConstraint("weeklyTargetMinutesProximity");
    }
    // SOFT: Çalışanın "istemediği" günlere atama → soft ceza
private Constraint undesiredDayForEmployee(ConstraintFactory cf) {
    return cf.forEach(Shift.class)
        .join(Employee.class, equal(Shift::getEmployee, Function.identity()))
        .flattenLast(Employee::getUndesiredDates) // -> Bi<Shift, LocalDate>
        .filter(Shift::isOverlappingWithDate)
        .penalizeBigDecimal(
            HardSoftBigDecimalScore.ONE_SOFT,
            (s, date) -> BigDecimal.valueOf(s.getOverlappingDurationInMinutes()))
        .asConstraint("Undesired day for employee");
}

// SOFT: Çalışanın "tercih ettiği" günlere atama → soft ödül
private Constraint desiredDayForEmployee(ConstraintFactory cf) {
    return cf.forEach(Shift.class)
        .join(Employee.class, equal(Shift::getEmployee, Function.identity()))
        .flattenLast(Employee::getDesiredDates) // -> Bi<Shift, LocalDate>
        .filter(Shift::isOverlappingWithDate)
        .rewardBigDecimal(
            HardSoftBigDecimalScore.ONE_SOFT,
            (s, date) -> BigDecimal.valueOf(s.getOverlappingDurationInMinutes()))
        .asConstraint("Desired day for employee");
}

// SOFT: Vardiya sayısını çalışanlar arasında dengeli dağıt

private Constraint balanceEmployeeShiftAssignments(ConstraintFactory cf) {
    return cf.forEach(Shift.class)
        .groupBy(Shift::getEmployee, ConstraintCollectors.count())
        .complement(Employee.class, e -> 0) // ataması olmayanları da dahil et
        .groupBy(ConstraintCollectors.loadBalance(
            (emp, cnt) -> emp, (emp, cnt) -> cnt))
        .penalizeBigDecimal(HardSoftBigDecimalScore.ONE_SOFT, LoadBalance::unfairness)
        .asConstraint("Balance employee shift assignments");
}
}
