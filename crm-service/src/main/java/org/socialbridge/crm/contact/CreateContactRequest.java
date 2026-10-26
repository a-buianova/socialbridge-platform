package org.socialbridge.crm.contact;
import jakarta.validation.constraints.*;
public record CreateContactRequest(@NotBlank @Size(max=120) String name,
 @NotBlank @Email @Size(max=254) String email, @NotNull ContactType type) {}
