package org.example.MODEL;

public class Paper {
    private int paperKey; 
    private int paperCode;
    private int examId;
    
    public Paper(int paperKey, int paperCode, int examId) {
        this.paperKey = paperKey; 
        this.paperCode = paperCode; 
        this.examId = examId;
    }
    
    public int getPaperKey() { 
        return paperKey; }
    
    public void setPaperKey(int paperKey) { 
        this.paperKey = paperKey; }
    
    public int getPaperCode() { 
        return paperCode; }
    
    public void setPaperCode(int paperCode) { 
        this.paperCode = paperCode; }
    
    public int getExamId() { 
        return examId; }
    
    public void setExamId(int examId) { 
        this.examId = examId; }
}
