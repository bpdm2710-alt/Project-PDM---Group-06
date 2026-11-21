package org.example.DAO;

import org.example.MODEL.Topic;
import java.sql.*; import java.util.*;

public class TopicDAO {
    private final Connection conn;
    public TopicDAO(Connection conn) { 
        this.conn = conn; }

    public List<Topic> getAllTopics() {
        List<Topic> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT Topic_ID, Content FROM TOPIC ORDER BY Topic_ID");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new Topic(rs.getInt("Topic_ID"), rs.getString("Content")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}