package org.socialbridge.crm.contact;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ContactResponse(
        UUID id,
        String name,
        @Schema(
                description = "Normalized email, or null for an offline contact. "
                        + "A non-null email remains reserved after archival.",
                types = {"string", "null"})
        String email,
        ContactType type,
        Instant createdAt,
        boolean archived) {

    public static ContactResponse from(Contact contact) {
        return new ContactResponse(
                contact.getId(),
                contact.getName(),
                contact.getEmail(),
                contact.getType(),
                contact.getCreatedAt(),
                contact.isArchived());
    }
}
