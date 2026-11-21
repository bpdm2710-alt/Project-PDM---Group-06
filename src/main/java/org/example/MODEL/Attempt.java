package org.example.MODEL;

import java.sql.Timestamp;

public class Attempt {
    private int attemptNo;
    private int regisId;
    private int paperId;
    private int examId;
    private Timestamp startTime;
    private Timestamp endTime;

    public Attempt(int attemptNo, int regisId, int paperId, int examId,
                   Timestamp startTime, Timestamp endTime) {
        this.attemptNo = attemptNo;
        this.regisId = regisId;
        this.paperId = paperId;
        this.examId = examId;
        this.startTime = startTime;
        this.endTime = endTime;
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

    public int getPaperId() {
        return paperId;
    }

    public void setPaperId(int paperId) {
        this.paperId = paperId;
    }

    public int getExamId() {
        return examId;
    }

    public void setExamId(int examId) {
        this.examId = examId;
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }

    public Timestamp getEndTime() {
        return endTime;
    }

    public void setEndTime(Timestamp endTime) {
        this.endTime = endTime;
    }

    /**
     * In-progress attempts store EndTime = StartTime because the live schema
     * has EndTime NOT NULL. A real submission moves EndTime after StartTime.
     */
    public boolean isSubmitted() {
        // 21-table schema: EndTime IS NULL = in progress, NOT NULL = submitted
        return endTime != null;
    }

    @Override
    public String toString() {
        return "Attempt{" +
                "attemptNo=" + attemptNo +
                ", regisId=" + regisId +
                ", paperId=" + paperId +
                ", examId=" + examId +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}
