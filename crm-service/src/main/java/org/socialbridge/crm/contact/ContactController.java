package org.socialbridge.crm.contact;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/contacts")
public class ContactController {
 private final ContactService service;
 public ContactController(ContactService service){this.service=service;}
 @PostMapping public ResponseEntity<ContactResponse> create(@Valid @RequestBody CreateContactRequest request){
  ContactResponse result=service.create(request);return ResponseEntity.created(URI.create("/api/v1/contacts/"+result.id())).body(result);
 }
 @GetMapping public Page<ContactResponse> list(@RequestParam(required=false) ContactType type,
 @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  if(page<0||size<1||size>100)throw new BadRequestException("page must be >= 0 and size must be 1..100");
  return service.list(type,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt")));
 }
 @GetMapping("/{id}") public ContactResponse get(@PathVariable UUID id){return service.get(id);}
 @PutMapping("/{id}") public ContactResponse update(@PathVariable UUID id,@Valid @RequestBody CreateContactRequest request){return service.update(id,request);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id){service.archive(id);}
}
