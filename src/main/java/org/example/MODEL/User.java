package org.example.MODEL;

public class User {
    private int userId;
    private String firstName;
    private String lastName;
    private String email;
    private String role; 

    public User(int userId, String firstName, String lastName, String email, String role) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
    }

    public int getUserId() { 
        return userId; }
    
    public String getFirstName() { 
        return firstName; }
    
    public String getLastName() { 
        return lastName; }
    
    public String getEmail() { 
        return email; }
    
    public String getRole() { 
        return role; }
    
    public String getFullName() { 
        return firstName + " " + lastName; }
}