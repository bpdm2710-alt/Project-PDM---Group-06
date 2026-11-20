package org.example.MODEL;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;

public class Score {
    private int attemptNo;
    private int regisId;
    private BigDecimal totalScore;
    private BigDecimal maxScore;
    private BigDecimal percentage;
    private boolean passed;
    private Timestamp gradedAt;

    public Score(int attemptNo, int regisId,
                 BigDecimal totalScore, BigDecimal maxScore,
                 BigDecimal percentage, boolean passed, Timestamp gradedAt) {
        this.attemptNo = attemptNo;
        this.regisId = regisId;
        this.totalScore = totalScore;
        this.maxScore = maxScore;
        this.percentage = percentage;
        this.passed = passed;
        this.gradedAt = gradedAt;
    }

    public static Score calculate(int attemptNo, int regisId,
                                  BigDecimal totalScore, BigDecimal maxScore,
                                  BigDecimal passMark, Timestamp gradedAt) {
        BigDecimal total = totalScore == null ? BigDecimal.ZERO : totalScore;
        BigDecimal max = maxScore == null ? BigDecimal.ZERO : maxScore;
        total = total.setScale(2, RoundingMode.HALF_UP);
        max = max.setScale(2, RoundingMode.HALF_UP);
        BigDecimal percent;
        if (max.signum() == 0) {
            percent = BigDecimal.ZERO.setScale(2);
        } else {
            percent = total.multiply(BigDecimal.valueOf(100))
                    .divide(max, 2, RoundingMode.HALF_UP);
        }
        boolean pass = passMark != null && total.compareTo(passMark) >= 0;
        return new Score(attemptNo, regisId, total, max, percent, pass, gradedAt);
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(int attemptNo) {
        this.attemptNo = attemptNo;
    }

    public int getRegisId() {
        return regisId;
    }

    public void setRegisId(int regisId) {
        this.regisId = regisId;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public Timestamp getGradedAt() {
        return gradedAt;
    }

    public void setGradedAt(Timestamp gradedAt) {
        this.gradedAt = gradedAt;
    }

    @Override
    public String toString() {
        return "Score{" +
                "attemptNo=" + attemptNo +
                ", regisId=" + regisId +
                ", totalScore=" + totalScore +
                ", maxScore=" + maxScore +
                ", percentage=" + percentage +
                ", passed=" + passed +
                ", gradedAt=" + gradedAt +
                '}';
    }
}
