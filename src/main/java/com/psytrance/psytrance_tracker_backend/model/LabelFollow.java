package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A user follows a label of the map: the gigs of its artists count as followed gigs. */
@Entity
@Table(name = "label_follows")
public class LabelFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "label_name", nullable = false, length = 200)
    private String labelName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LabelFollow() {
    }

    public LabelFollow(User user, String labelName, Instant createdAt) {
        this.user = user;
        this.labelName = labelName;
        this.createdAt = createdAt;
    }

    public User getUser() {
        return user;
    }

    public String getLabelName() {
        return labelName;
    }
}
