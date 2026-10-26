package org.socialbridge.crm.contact;
import java.time.Instant;
import java.util.UUID;
public record ContactResponse(UUID id,String name,String email,ContactType type,Instant createdAt,boolean archived) {
 public static ContactResponse from(Contact c){return new ContactResponse(c.getId(),c.getName(),c.getEmail(),c.getType(),c.getCreatedAt(),c.isArchived());}
}
