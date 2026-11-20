package org.example.MODEL;

import java.util.List; import java.util.ArrayList;

public class Question {
    private int questionId; 
    private int instructorId; 
    private int topicId;
    private String questionType; 
    private String content; 
    private String difficulty; 
    private boolean allowMultipleChoice; 
    private String modelAnswer;
    private List<QuestionOption> options = new ArrayList<>();

    public Question(int questionId, int instructorId, int topicId, String questionType, String content, String difficulty) {
        this.questionId = questionId; 
        this.instructorId = instructorId; 
        this.topicId = topicId;
        this.questionType = questionType; 
        this.content = content; 
        this.difficulty = difficulty;
    }
    
    public int getQuestionId() { 
        return questionId; }
    
    public void setQuestionId(int questionId) { 
        this.questionId = questionId; }
    
    public int getInstructorId() { 
        return instructorId; }
    
    public void setInstructorId(int instructorId) { 
        this.instructorId = instructorId; }
    
    public int getTopicId() { 
        return topicId; }
    
    public void setTopicId(int topicId) { 
        this.topicId = topicId; }
    
    public String getQuestionType() { 
        return questionType; }
    
    public void setQuestionType(String questionType) { 
        this.questionType = questionType; }
    
    public String getContent() { 
        return content; }
    
    public void setContent(String content) { 
        this.content = content; }
    
    public String getDifficulty() { 
        return difficulty; }
    
    public void setDifficulty(String difficulty) { 
        this.difficulty = difficulty; }
    
    public boolean isAllowMultipleChoice() { 
        return allowMultipleChoice; }
    
    public void setAllowMultipleChoice(boolean allowMultipleChoice) { 
        this.allowMultipleChoice = allowMultipleChoice; }
    
    public String getModelAnswer() { 
        return modelAnswer; }
    
    public void setModelAnswer(String modelAnswer) { 
        this.modelAnswer = modelAnswer; }
    
    public List<QuestionOption> getOptions() { 
        return options; }
    
    public void setOptions(List<QuestionOption> options) { 
        this.options = options; }
    
    public static final String TYPE_MCQ = "MCQ";
    public static final String TYPE_SHORT_ANSWER = "Short answer";
}
