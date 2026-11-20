package org.example.ui;

/** How a panel asks its dashboard to switch page. Keeps panels independent of the frame. */
public interface Navigator {

    String OVERVIEW = "Overview";
    String COURSES = "Courses";
    String MY_EXAMS = "My exams";
    String RESULTS = "Results";

    void show(String page);

    /** Starts (or resumes) the attempt for this registration and shows the exam screen. */
    void openExam(int regisId);
}
