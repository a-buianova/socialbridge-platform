package org.socialbridge.crm.contact;
import org.socialbridge.crm.shared.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class ContactService {
 private final ContactRepository repository;
 public ContactService(ContactRepository repository){this.repository=repository;}
 @Transactional
 public ContactResponse create(CreateContactRequest request){
  String email=normalizeEmail(request.email());
  if(repository.existsByEmailIgnoreCase(email))throw new ConflictException("Contact with this email already exists");
  try{return ContactResponse.from(repository.saveAndFlush(new Contact(request.name().trim(),email,request.type())));}
  catch(DataIntegrityViolationException e){throw new ConflictException("Contact with this email already exists");}
 }
 @Transactional(readOnly=true)
 public ContactResponse get(UUID id){return ContactResponse.from(findActive(id));}
 @Transactional(readOnly=true)
 public Page<ContactResponse> list(ContactType type,Pageable pageable){
  Page<Contact> contacts=(type==null)?repository.findByArchivedFalse(pageable):repository.findByArchivedFalseAndType(type,pageable);
  return contacts.map(ContactResponse::from);
 }
 @Transactional
 public ContactResponse update(UUID id,CreateContactRequest request){
  Contact c=findActive(id);String email=normalizeEmail(request.email());
  if(repository.existsByEmailIgnoreCaseAndIdNot(email,id))throw new ConflictException("Contact with this email already exists");
  c.update(request.name().trim(),email,request.type());
  try{return ContactResponse.from(repository.saveAndFlush(c));}
  catch(DataIntegrityViolationException e){throw new ConflictException("Contact with this email already exists");}
 }
 @Transactional
 public void archive(UUID id){Contact c=findActive(id);c.archive();}
 private Contact findActive(UUID id){return repository.findByIdAndArchivedFalse(id).orElseThrow(()->new NotFoundException("Contact not found"));}
 private String normalizeEmail(String value){return value.trim().toLowerCase(Locale.ROOT);}
}
