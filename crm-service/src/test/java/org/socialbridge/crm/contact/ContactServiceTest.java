package org.socialbridge.crm.contact;

import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialbridge.crm.shared.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies which database failures are allowed to become the public duplicate-email conflict. */
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock ContactRepository repository;
    ContactService service;

    @BeforeEach
    void setUp() {
        service = new ContactService(repository);
    }

    @Test
    void createTranslatesTheEmailIndexRaceIntoAConflict() {
        DataIntegrityViolationException failure = integrityFailure("uq_contact_email_lower");
        when(repository.saveAndFlush(any(Contact.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Contact with this email already exists");
    }

    @Test
    void createPreservesAnUnrelatedConstraintFailure() {
        DataIntegrityViolationException failure = integrityFailure("some_other_constraint");
        when(repository.saveAndFlush(any(Contact.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.create(request())).isSameAs(failure);
    }

    @Test
    void updateTranslatesTheEmailIndexRaceIntoAConflict() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndArchivedFalse(id))
                .thenReturn(Optional.of(new Contact("Ada Example", "old@example.org", ContactType.DONOR)));
        DataIntegrityViolationException failure = integrityFailure("uq_contact_email_lower");
        when(repository.saveAndFlush(any(Contact.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.update(id, request()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Contact with this email already exists");
    }

    @Test
    void updatePreservesAnUnclassifiedIntegrityFailure() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndArchivedFalse(id))
                .thenReturn(Optional.of(new Contact("Ada Example", "old@example.org", ContactType.DONOR)));
        DataIntegrityViolationException failure = new DataIntegrityViolationException("Unclassified database failure");
        when(repository.saveAndFlush(any(Contact.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.update(id, request())).isSameAs(failure);
    }

    private CreateContactRequest request() {
        return new CreateContactRequest("Ada Example", "ada@example.org", ContactType.DONOR);
    }

    private DataIntegrityViolationException integrityFailure(String constraintName) {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn(constraintName);
        return new DataIntegrityViolationException("Synthetic database constraint failure", cause);
    }
}
