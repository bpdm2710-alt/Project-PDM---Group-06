package org.example.MODEL;

public class Topic {

    private int topicID;
    private String topicName;

    public Topic(int topicId, String topicName){
        this.topicID = topicId;
        this.topicName = topicName;
    }

    public int getTopicId() { 
        return topicID; }

    public void setTopicId(int topicId) { 
        this.topicID = topicId; }

    public String getTopicName() { 
        return topicName; }
    
      public void setTopicName(String topicName) { 
        this.topicName = topicName; }    

}