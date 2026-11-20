package org.example.DAO;

import org.example.MODEL.Question; 
import java.sql.*; 
import java.util.*;

public class QuestionDAO {
    public static final String SELECT_BASE =
            "SELECT q.question_id, q.instructor_id, q.topic_id, q.content, q.difficulty, q.question_type, " +
            "m.allow_multiple, s.model_answer FROM question q " +
            "LEFT JOIN mcq m ON q.question_id = m.question_id " +
            "LEFT JOIN short_answer s ON q.question_id = s.question_id";

    private final Connection conn; private final QuestionOptionDAO optionDAO;
    public QuestionDAO(Connection conn) { 
        this.conn = conn; 
        this.optionDAO = new QuestionOptionDAO(conn); }

    public void loadOptions(List<Question> questions) {
        for (Question q : questions) {
            if (Question.TYPE_MCQ.equals(q.getQuestionType())) {
                q.setOptions(optionDAO.getOptionsByQuestion(q.getQuestionId()));
            }
        }
    }

    public static Question mapRow(ResultSet rs) throws SQLException {
        Question q = new Question(
                rs.getInt("question_id"), rs.getInt("instructor_id"), rs.getInt("topic_id"),
                rs.getString("question_type"), rs.getString("content"), rs.getString("difficulty"));
        q.setAllowMultipleChoice(rs.getBoolean("allow_multiple"));
        q.setModelAnswer(rs.getString("model_answer"));
        return q;
    }
}