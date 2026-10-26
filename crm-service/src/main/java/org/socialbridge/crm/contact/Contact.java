package org.socialbridge.crm.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContactType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean archived;

    @Version
    @Column(nullable = false)
    private long version;

    protected Contact() {
    }

    public Contact(String name, String email, ContactType type) {
        this.name = name;
        this.email = email;
        this.type = type;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public ContactType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isArchived() {
        return archived;
    }

    public long getVersion() {
        return version;
    }

    public void update(String name, String email, ContactType type) {
        this.name = name;
        this.email = email;
        this.type = type;
    }

    public void archive() {
        archived = true;
    }
}
