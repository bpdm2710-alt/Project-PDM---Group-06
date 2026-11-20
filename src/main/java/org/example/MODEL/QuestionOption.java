package org.example.MODEL;

public class QuestionOption {
    private int questionId; 
    private int optionNo; 
    private String content; 
    private boolean isCorrect;

    public QuestionOption(int questionId, int optionNo, String content, boolean isCorrect) {
        this.questionId = questionId; 
        this.optionNo = optionNo; 
        this.content = content; 
        this.isCorrect = isCorrect;
    }
    
    public int getQuestionId() { 
        return questionId; }
    
    public void setQuestionId(int questionId) { 
        this.questionId = questionId; }
    
    public int getOptionNo() { 
        return optionNo; }
    
    public void setOptionNo(int optionNo) { 
        this.optionNo = optionNo; }
    
    public String getContent() { 
        return content; }
    
    public void setContent(String content) { 
        this.content = content; }
    
    public boolean isCorrect() { 
        return isCorrect; }
    
    public void setCorrect(boolean correct) { 
        isCorrect = correct; }
}