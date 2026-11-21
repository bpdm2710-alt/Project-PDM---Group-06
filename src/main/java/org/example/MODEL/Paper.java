package org.example.MODEL;

public class Paper {
    private int paperKey; 
    private int paperId; 
    private int examId;
    
    public Paper(int paperKey, int paperId, int examId) {
        this.paperKey = paperKey; 
        this.paperId = paperId; 
        this.examId = examId;
    }
    
    public int getPaperKey() { 
        return paperKey; }
    
    public void setPaperKey(int paperKey) { 
        this.paperKey = paperKey; }
    
    public int getPaperId() { 
        return paperId; }
    
    public void setPaperId(int paperId) { 
        this.paperId = paperId; }
    
    public int getExamId() { 
        return examId; }
    
    public void setExamId(int examId) { 
        this.examId = examId; }
}