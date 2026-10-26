package org.socialbridge.crm.contact;

import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.socialbridge.crm.shared.ConflictException;
import org.socialbridge.crm.shared.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    private static final String EMAIL_UNIQUE_INDEX = "uq_contact_email_lower";
    private static final String DUPLICATE_EMAIL_MESSAGE = "Contact with this email already exists";

    private final ContactRepository repository;

    public ContactService(ContactRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ContactResponse create(CreateContactRequest request) {
        if (request.email() != null && repository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException(DUPLICATE_EMAIL_MESSAGE);
        }
        Contact contact = new Contact(request.name(), request.email(), request.type());
        return save(contact);
    }

    @Transactional(readOnly = true)
    public ContactResponse get(UUID id) {
        return ContactResponse.from(findActive(id));
    }

    @Transactional(readOnly = true)
    public Page<ContactResponse> list(ContactType type, Pageable pageable) {
        Page<Contact> contacts = type == null
                ? repository.findByArchivedFalse(pageable)
                : repository.findByArchivedFalseAndType(type, pageable);
        return contacts.map(ContactResponse::from);
    }

    @Transactional
    public ContactResponse update(UUID id, CreateContactRequest request) {
        Contact contact = findActive(id);
        if (request.email() != null && repository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new ConflictException(DUPLICATE_EMAIL_MESSAGE);
        }
        contact.update(request.name(), request.email(), request.type());
        return save(contact);
    }

    @Transactional
    public void archive(UUID id) {
        Contact contact = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Contact not found"));
        if (!contact.isArchived()) {
            contact.archive();
            repository.saveAndFlush(contact);
        }
    }

    private Contact findActive(UUID id) {
        return repository.findByIdAndArchivedFalse(id)
                .orElseThrow(() -> new NotFoundException("Contact not found"));
    }

    private ContactResponse save(Contact contact) {
        try {
            return ContactResponse.from(repository.saveAndFlush(contact));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmail(exception)) {
                throw new ConflictException(DUPLICATE_EMAIL_MESSAGE);
            }
            throw exception;
        }
    }

    private boolean isDuplicateEmail(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && EMAIL_UNIQUE_INDEX.equals(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
