package reservation.domain;

import generic.TimeInterval;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class DiscountCondition {

    public enum ConditionType {PERIOD_CONDITION, SEQUENCE_CONDITION, COMBINED_CONDITION}

    private Long id;
    private Long policyId;
    private ConditionType conditionType;
    private DayOfWeek dayOfWeek;
    private TimeInterval interval;
    private Integer sequence;

    public DiscountCondition() {}

    public DiscountCondition(Long policyId, ConditionType conditionType, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Integer sequence) {
        this(null, policyId, conditionType, dayOfWeek, TimeInterval.of(startTime, endTime), sequence);
    }

    public DiscountCondition(Long id, Long policyId, ConditionType conditionType, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Integer sequence) {
        this(id, policyId, conditionType, dayOfWeek, TimeInterval.of(startTime, endTime), sequence);
    }

    public DiscountCondition(Long id, Long policyId, ConditionType conditionType, DayOfWeek dayOfWeek, TimeInterval interval, Integer sequence) {
        this.id = id;
        this.policyId = policyId;
        this.conditionType = conditionType;
        this.dayOfWeek = dayOfWeek;
        this.interval = interval;
        this.sequence = sequence;
    }

    public boolean isSatisFiedBy(Screening screening) {
        if (isPeriodCondition()) {
            if (screening.isPayedIn(this.getDayOfWeek(), interval.getStartTime(), interval.getEndTime())) {
                return true;
            } else {
                if (this.getSequence().equals(screening.getSequence())) {
                    return true;
                }
            }
        } else if (isSequenceCondition()) {
            if (this.sequence.equals(screening.getSequence())) {
                return true;
            }
        } else if (isCombinedCondition()) {
            if (screening.isPayedIn(this.getDayOfWeek(), interval.getStartTime(), interval.getEndTime()) && this.getSequence().equals(screening.getSequence())) {
                return true;
            }
        }

        return false;
    }

    private boolean isPeriodCondition() {
        return ConditionType.PERIOD_CONDITION.equals(conditionType);
    }

    public boolean isSequenceCondition() {
        return ConditionType.SEQUENCE_CONDITION.equals(conditionType);
    }

    public boolean isCombinedCondition() {
        return ConditionType.COMBINED_CONDITION.equals(conditionType);
    }

    public Long getPolicyId() {
        return policyId;
    }

    private DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    private Integer getSequence() {
        return sequence;
    }
}
