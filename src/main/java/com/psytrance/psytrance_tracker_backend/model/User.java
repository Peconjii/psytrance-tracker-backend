package com.psytrance.psytrance_tracker_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue
    private Long id;
    @Column(unique = true)
    private String username;
    private String email;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    // Reminder emails before events the user saved or is going to; can be turned off on the profile
    private boolean eventReminders = true;
    // Confirmed by opening the emailed link (see EmailVerification); only verified accounts may add events
    private boolean emailVerified;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEventReminders() {
        return eventReminders;
    }

    public void setEventReminders(boolean eventReminders) {
        this.eventReminders = eventReminders;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}
