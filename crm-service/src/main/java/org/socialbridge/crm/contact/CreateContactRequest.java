package org.socialbridge.crm.contact;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record CreateContactRequest(
        @NotBlank @Size(max = 120) String name,
        @Schema(
                description = "Optional email; omitted, null and blank values become null. "
                        + "Other values are trimmed and lowercased before validation.",
                types = {"string", "null"})
        @Email @Size(max = 254) String email,
        @NotNull ContactType type) {

    public CreateContactRequest {
        name = name == null ? null : name.strip();
        email = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
