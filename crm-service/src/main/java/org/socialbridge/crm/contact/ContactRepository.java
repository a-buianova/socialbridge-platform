package org.socialbridge.crm.contact;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    Page<Contact> findByArchivedFalse(Pageable pageable);

    Page<Contact> findByArchivedFalseAndType(ContactType type, Pageable pageable);

    Optional<Contact> findByIdAndArchivedFalse(UUID id);
}
