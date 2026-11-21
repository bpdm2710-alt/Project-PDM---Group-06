package org.example.DAO;

import org.example.MODEL.Topic;
import java.sql.*; import java.util.*;

public class TopicDAO {
    private final Connection conn;
    public TopicDAO(Connection conn) { this.conn = conn; }

    public List<Topic> getAllTopics() {
        List<Topic> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT Topic_ID, Content AS topicName FROM topic");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new Topic(rs.getInt("Topic_ID"), rs.getString("topicName")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}