package org.socialbridge.crm.contact;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="contacts", uniqueConstraints=@UniqueConstraint(name="uq_contact_email",columnNames="email"))
public class Contact {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=120) private String name;
 @Column(nullable=false,length=254) private String email;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private ContactType type;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(nullable=false) private boolean archived = false;
 protected Contact() {}
 public Contact(String name,String email,ContactType type) { this.name=name;this.email=email;this.type=type; }
 @PrePersist void prePersist() { createdAt=Instant.now(); }
 public UUID getId(){return id;} public String getName(){return name;} public String getEmail(){return email;}
 public ContactType getType(){return type;} public Instant getCreatedAt(){return createdAt;} public boolean isArchived(){return archived;}
 public void update(String name,String email,ContactType type){this.name=name;this.email=email;this.type=type;}
 public void archive(){archived=true;}
}
