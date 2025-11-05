package com.tal.pro.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    public User(String username, String email, String password, String firstName, String lastName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        updateFullName();
    }
    
    // For backward compatibility
    public User(String username, String email, String password) {
        this(username, email, password, null, null);
    }
    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String password;

    @Indexed(unique = true)
    private String email;

    @Field("first_name")
    private String firstName;

    @Field("last_name")
    private String lastName;

    @Field("full_name")
    private String fullName;
    
    @Field("phone_number")
    private String phoneNumber;
    
    /**
     * Updates the full name based on first and last name
     */
    private void updateFullName() {
        if (this.firstName != null && this.lastName != null) {
            this.fullName = (this.firstName + " " + this.lastName).trim();
        } else if (this.firstName != null) {
            this.fullName = this.firstName.trim();
        } else if (this.lastName != null) {
            this.fullName = this.lastName.trim();
        } else {
            this.fullName = "";
        }
    }

    @DBRef
    private Set<Role> roles = new HashSet<>();

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        if (this.fullName == null || this.fullName.trim().isEmpty()) {
            updateFullName();
        }
        return fullName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
        updateFullName();
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
        updateFullName();
    }


    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
