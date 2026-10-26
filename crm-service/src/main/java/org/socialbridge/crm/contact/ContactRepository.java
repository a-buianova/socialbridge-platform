package org.socialbridge.crm.contact;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ContactRepository extends JpaRepository<Contact,UUID> {
 boolean existsByEmailIgnoreCase(String email);
 boolean existsByEmailIgnoreCaseAndIdNot(String email,UUID id);
 Page<Contact> findByArchivedFalse(Pageable pageable);
 Page<Contact> findByArchivedFalseAndType(ContactType type,Pageable pageable);
 Optional<Contact> findByIdAndArchivedFalse(UUID id);
}
