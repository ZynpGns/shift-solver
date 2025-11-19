package org.acme.employeescheduling.solver;

import static ai.timefold.solver.core.api.score.stream.Joiners.equal;
import static ai.timefold.solver.core.api.score.stream.Joiners.lessThanOrEqual;
import static ai.timefold.solver.core.api.score.stream.Joiners.overlapping;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

import ai.timefold.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.common.LoadBalance;

import org.acme.employeescheduling.domain.Employee;
import org.acme.employeescheduling.domain.Shift;

import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import org.acme.employeescheduling.domain.Rules;

public class EmployeeSchedulingConstraintProvider implements ConstraintProvider {

    private static int getMinuteOverlap(Shift shift1, Shift shift2) {
        // The overlap of two timeslot occurs in the range common to both timeslots.
        // Both timeslots are active after the higher of their two start times,
        // and before the lower of their two end times.
        LocalDateTime shift1Start = shift1.getStart();
        LocalDateTime shift1End = shift1.getEnd();
        LocalDateTime shift2Start = shift2.getStart();
        LocalDateTime shift2End = shift2.getEnd();
        return (int) Duration.between((shift1Start.isAfter(shift2Start)) ? shift1Start : shift2Start,
                (shift1End.isBefore(shift2End)) ? shift1End : shift2End).toMinutes();
    }
    private static boolean overlapsWindow(java.time.LocalDateTime s, java.time.LocalDateTime e,
                                      java.time.LocalDate date, int sh, int sm, int eh, int em) {
    var ws = java.time.LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), sh, sm);
    var we = java.time.LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), eh, em);
    return !e.isBefore(ws) && !s.isAfter(we);
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
                weeklyMaxMinutes(constraintFactory), // <-- ekle
                dailyMaxMinutes(constraintFactory), // <— eklendi
                weeklyMinWorkingDays(constraintFactory),
                mustWorkSaturday(constraintFactory),
                mustWorkSunday(constraintFactory),
                peakTueManager(constraintFactory),
                peakTueTotal(constraintFactory),
                peakSatManager(constraintFactory),
                peakSatTotal(constraintFactory),
                // Soft constraints
                undesiredDayForEmployee(constraintFactory),
                desiredDayForEmployee(constraintFactory),
                balanceEmployeeShiftAssignments(constraintFactory)
        };
    }
    

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
                .filter((firstShift,
                        secondShift) -> Duration.between(firstShift.getEnd(), secondShift.getStart()).toHours() < 10)
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

    Constraint undesiredDayForEmployee(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
                .join(Employee.class, equal(Shift::getEmployee, Function.identity()))
                .flattenLast(Employee::getUndesiredDates)
                .filter(Shift::isOverlappingWithDate)
                .penalize(HardSoftBigDecimalScore.ONE_SOFT, Shift::getOverlappingDurationInMinutes)
                .asConstraint("Undesired day for employee");
    }

    Constraint desiredDayForEmployee(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
                .join(Employee.class, equal(Shift::getEmployee, Function.identity()))
                .flattenLast(Employee::getDesiredDates)
                .filter(Shift::isOverlappingWithDate)
                .reward(HardSoftBigDecimalScore.ONE_SOFT, Shift::getOverlappingDurationInMinutes)
                .asConstraint("Desired day for employee");
    }

    Constraint balanceEmployeeShiftAssignments(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
                .groupBy(Shift::getEmployee, ConstraintCollectors.count())
                .complement(Employee.class, e -> 0) // Include all employees which are not assigned to any shift.c
                .groupBy(ConstraintCollectors.loadBalance((employee, shiftCount) -> employee,
                        (employee, shiftCount) -> shiftCount))
                .penalizeBigDecimal(HardSoftBigDecimalScore.ONE_SOFT, LoadBalance::unfairness)
                .asConstraint("Balance employee shift assignments");
    }

    Constraint weeklyMaxMinutes(ConstraintFactory factory) {
    return factory.forEach(Shift.class)
        .groupBy(
            Shift::getEmployee,
            s -> s.getStart().toLocalDate()
                  .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            ConstraintCollectors.sumLong(s ->
                Duration.between(s.getStart(), s.getEnd()).toMinutes())
        )
        .join(Rules.class)
        .filter((emp, weekStart, minutes, rules) -> minutes > rules.getMaxWeeklyMinutes())
        .penalize(HardSoftBigDecimalScore.ONE_HARD,
                  (emp, weekStart, minutes, rules) -> (int) (minutes - rules.getMaxWeeklyMinutes()))
        .asConstraint("Weekly max minutes");
}
    Constraint dailyMaxMinutes(ConstraintFactory factory) {
    return factory.forEach(Shift.class)
        .groupBy(
            Shift::getEmployee,
            s -> s.getStart().toLocalDate(),
            ConstraintCollectors.sumLong(s ->
                Duration.between(s.getStart(), s.getEnd()).toMinutes())
        )
        .join(Rules.class)
        .filter((emp, day, minutes, rules) -> minutes > rules.getDailyMaxMinutes())
        .penalize(HardSoftBigDecimalScore.ONE_HARD,
                  (emp, day, minutes, rules) -> (int)(minutes - rules.getDailyMaxMinutes()))
        .asConstraint("Daily max minutes");
}
    // <—Haftada en az 6 farklı gün çalışma
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
// <— Cumartesi ve pazar mutlaka çalışsın.
    Constraint mustWorkSaturday(ConstraintFactory f) {
    return f.forEach(Shift.class)
        .groupBy(
            Shift::getEmployee,
            s -> s.getStart().toLocalDate()
                  .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            ConstraintCollectors.sumLong(s ->
                s.getStart().getDayOfWeek() == DayOfWeek.SATURDAY ? 1L : 0L)
        )
        .filter((emp, weekStart, satCount) -> satCount == 0L)
        .penalize(HardSoftBigDecimalScore.ONE_HARD)
        .asConstraint("Must work on Saturday");
}
Constraint mustWorkSunday(ConstraintFactory f) {
    return f.forEach(Shift.class)
        .groupBy(
            Shift::getEmployee,
            s -> s.getStart().toLocalDate()
                  .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            ConstraintCollectors.sumLong(s ->
                s.getStart().getDayOfWeek() == DayOfWeek.SUNDAY ? 1L : 0L)
        )
        .filter((emp, weekStart, sunCount) -> sunCount == 0L)
        .penalize(HardSoftBigDecimalScore.ONE_HARD)
        .asConstraint("Must work on Sunday");
}
    Constraint peakTueManager(ConstraintFactory f) {
    return f.forEach(Shift.class)
        .filter(s ->
            s.getStart().getDayOfWeek() == DayOfWeek.TUESDAY &&
            overlapsWindow(s.getStart(), s.getEnd(), s.getStart().toLocalDate(), 14,0, 20,0) &&
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
            overlapsWindow(s.getStart(), s.getEnd(), s.getStart().toLocalDate(), 14,0, 20,0))
        .groupBy(s -> s.getStart().toLocalDate(), ConstraintCollectors.count())
        .filter((date, total) -> total < 5)
        .penalize(HardSoftBigDecimalScore.ONE_HARD, (date, total) -> 5 - total)
        .asConstraint("Tue 14-20 at least five total");
}
    Constraint peakSatManager(ConstraintFactory f) {
    return f.forEach(Shift.class)
        .filter(s ->
            s.getStart().getDayOfWeek() == DayOfWeek.SATURDAY &&
            overlapsWindow(s.getStart(), s.getEnd(), s.getStart().toLocalDate(), 14,0, 20,0) &&
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
            overlapsWindow(s.getStart(), s.getEnd(), s.getStart().toLocalDate(), 14,0, 20,0))
        .groupBy(s -> s.getStart().toLocalDate(), ConstraintCollectors.count())
        .filter((date, total) -> total < 5)
        .penalize(HardSoftBigDecimalScore.ONE_HARD, (date, total) -> 5 - total)
        .asConstraint("Sat 14-20 at least five total");
}

}
