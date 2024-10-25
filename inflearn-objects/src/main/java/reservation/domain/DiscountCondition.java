package reservation.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class DiscountCondition {

    public enum ConditionType {PERIOD_CONDITION, SEQUENCE_CONDITION}

    private Long id;
    private Long policyId;
    private ConditionType conditionType;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer sequence;

    public DiscountCondition() {}

    public DiscountCondition(Long policyId, ConditionType conditionType, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Integer sequence) {
        this(null, policyId, conditionType, dayOfWeek, startTime, endTime, sequence);
    }

    public DiscountCondition(Long id, Long policyId, ConditionType conditionType, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Integer sequence) {
        this.id = id;
        this.policyId = policyId;
        this.conditionType = conditionType;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.sequence = sequence;
    }

    public boolean isSatisFiedBy(Screening screening) {
        if (isPeriodCondition()) {
            if (screening.isPayedIn(this.getDayOfWeek(), this.getStartTime(), this.getEndTime())) {
                return true;
            } else {
                if (this.getSequence().equals(screening.getSequence())) {
                    return true;
                }
            }
        } else {
            if (this.sequence.equals(screening.getSequence())) {
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

    public Long getPolicyId() {
        return policyId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Integer getSequence() {
        return sequence;
    }
}
