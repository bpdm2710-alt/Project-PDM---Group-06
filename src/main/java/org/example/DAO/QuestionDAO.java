package org.example.DAO;

import org.example.MODEL.Question; 
import java.sql.*; 
import java.util.*;

public class QuestionDAO {
    public static final String SELECT_BASE =
            "SELECT q.Quest_ID AS questionId, q.User_ID AS instructorId, q.Topic_ID, q.Content, q.Difficulty, " +
            "CASE WHEN m.Quest_ID IS NOT NULL THEN 'MCQ' WHEN s.Quest_ID IS NOT NULL THEN 'Short answer' ELSE 'UNKNOWN' END AS question_type, " +
            "s.Answer_key AS model_answer FROM question q " +
            "LEFT JOIN mcq m ON q.Quest_ID = m.Quest_ID " +
            "LEFT JOIN short_answer s ON q.Quest_ID = s.Quest_ID";

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
                rs.getInt("questionId"), rs.getInt("instructorId"), rs.getInt("Topic_ID"),
                rs.getString("question_type"), rs.getString("Content"), rs.getInt("Difficulty"));
        
        q.setAllowMultipleChoice(false); 
        q.setModelAnswer(rs.getString("model_answer"));
        return q;
    }
}