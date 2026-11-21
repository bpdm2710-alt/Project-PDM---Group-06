package org.example.DAO;

import org.example.MODEL.Question; 
import java.sql.*; 
import java.util.*;

public class QuestionDAO {
    public static final String SELECT_BASE =
            // 21-table schema: QUESTION/MCQ/SHORT_ANSWER, columns aliased to the names mapRow reads.
            "SELECT q.Quest_ID AS question_id, q.User_ID AS instructor_id, q.Topic_ID AS topic_id, " +
            "q.Content AS content, q.Difficulty AS difficulty, " +
            "CASE WHEN m.Quest_ID IS NOT NULL THEN '" + Question.TYPE_MCQ + "' ELSE '" + Question.TYPE_SHORT_ANSWER + "' END AS question_type, " +
            "0 AS allow_multiple, s.Answer_key AS model_answer FROM QUESTION q " +
            "LEFT JOIN MCQ m ON m.Quest_ID = q.Quest_ID " +
            "LEFT JOIN SHORT_ANSWER s ON s.Quest_ID = q.Quest_ID";

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