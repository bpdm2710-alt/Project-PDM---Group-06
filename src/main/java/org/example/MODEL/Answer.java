package org.example.MODEL;

import java.math.BigDecimal;

public class Answer {
    private int questId;
    private int attemptNo;
    private int regisId;
    private String answerText;
    private BigDecimal markAwarded;
    private Integer selectedOptionNo;

    public Answer(int questId, int attemptNo, int regisId,
                  String answerText, BigDecimal markAwarded,
                  Integer selectedOptionNo) {
        this.questId = questId;
        this.attemptNo = attemptNo;
        this.regisId = regisId;
        this.answerText = answerText;
        this.markAwarded = markAwarded;
        this.selectedOptionNo = selectedOptionNo;
    }

    public int getQuestId() {
        return questId;
    }

    public void setQuestId(int questId) {
        this.questId = questId;
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

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public BigDecimal getMarkAwarded() {
        return markAwarded;
    }

    public void setMarkAwarded(BigDecimal markAwarded) {
        this.markAwarded = markAwarded;
    }

    public Integer getSelectedOptionNo() {
        return selectedOptionNo;
    }

    public void setSelectedOptionNo(Integer selectedOptionNo) {
        this.selectedOptionNo = selectedOptionNo;
    }

    @Override
    public String toString() {
        return "Answer{" +
                "questId=" + questId +
                ", attemptNo=" + attemptNo +
                ", regisId=" + regisId +
                ", answerText='" + answerText + '\'' +
                ", markAwarded=" + markAwarded +
                ", selectedOptionNo=" + selectedOptionNo +
                '}';
    }
}
