package org.example.MODEL;

public class Course {
    private int courseId; 
    private String courseName; 
    private int credits;
    
    public Course(int courseId, String courseName, int credits) { 
        this.courseId = courseId; 
        this.courseName = courseName; 
        this.credits = credits; }
    
    public int getCourseId() { 
        return courseId; }
    
    public void setCourseId(int courseId) { 
        this.courseId = courseId; }
    
    public String getCourseName() { 
        return courseName; }
    
    public void setCourseName(String courseName) { 
        this.courseName = courseName; }
    
    public int getCredits() { 
        return credits; }
    
    public void setCredits(int credits) { 
        this.credits = credits; }
}
