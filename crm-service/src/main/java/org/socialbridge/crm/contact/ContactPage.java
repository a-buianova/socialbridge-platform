package org.socialbridge.crm.contact;

import java.util.List;
import org.springframework.data.domain.Page;

public record ContactPage(
        List<ContactResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ContactPage {
        content = List.copyOf(content);
    }

    public static ContactPage from(Page<ContactResponse> contacts) {
        return new ContactPage(
                contacts.getContent(),
                contacts.getNumber(),
                contacts.getSize(),
                contacts.getTotalElements(),
                contacts.getTotalPages());
    }
}
