package org.example.MODEL;
import java.sql.Timestamp;

public class Examination {
    private int examId; 
    private int courseId; 
    private String title;
    private Timestamp examDate; 
    private int durationMin; 
    private double passMark; 
    private int maxAttempts;
    private String status; 

    public Examination(int examId, int courseId, String title, Timestamp examDate, int durationMin, double passMark, int maxAttempts, String status) {
        this.examId = examId; 
        this.courseId = courseId; 
        this.title = title;
        this.examDate = examDate; 
        this.durationMin = durationMin; 
        this.passMark = passMark; 
        this.maxAttempts = maxAttempts; 
        this.status = status;
    }
    
    public int getExamId() { 
        return examId; }
    
    public void setExamId(int examId) { 
        this.examId = examId; }
    
    public int getCourseId() { 
        return courseId; }
    
    public void setCourseId(int courseId) { 
        this.courseId = courseId; }
    
    public String getTitle() { 
        return title; }
    
    public void setTitle(String title) { 
        this.title = title; }
    
    public Timestamp getExamDate() { 
        return examDate; }
    
    public void setExamDate(Timestamp examDate) { 
        this.examDate = examDate; }
    
    public int getDurationMin() { 
        return durationMin; }
    
    public void setDurationMin(int durationMin) { 
        this.durationMin = durationMin; }
    
    public double getPassMark() { 
        return passMark; }
    
    public void setPassMark(double passMark) { 
        this.passMark = passMark; }
    
    public int getMaxAttempts() { 
        return maxAttempts; }
    
    public void setMaxAttempts(int maxAttempts) { 
        this.maxAttempts = maxAttempts; }
    
    public String getStatus() { 
        return status; }
    
    public void setStatus(String status) { 
        this.status = status; }
}