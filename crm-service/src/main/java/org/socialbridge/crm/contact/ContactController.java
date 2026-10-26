package org.socialbridge.crm.contact;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.socialbridge.crm.shared.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contacts")
public class ContactController {

    private final ContactService service;

    public ContactController(ContactService service) {
        this.service = service;
    }

    @PostMapping
    @ApiResponse(
            responseCode = "201",
            description = "Contact created",
            headers = @Header(
                    name = "Location",
                    description = "Relative URI of the created contact",
                    schema = @Schema(type = "string", format = "uri-reference")),
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ContactResponse.class)))
    public ResponseEntity<ContactResponse> create(@Valid @RequestBody CreateContactRequest request) {
        ContactResponse result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/contacts/" + result.id())).body(result);
    }

    @GetMapping
    @ApiResponse(
            responseCode = "200",
            description = "Active contacts in stable pagination order",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ContactPage.class)))
    public ContactPage list(
            @RequestParam(name = "type", required = false) ContactType type,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("page must be >= 0 and size must be 1..100");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new BadRequestException("page multiplied by size must not exceed 2147483647");
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        return ContactPage.from(service.list(type, PageRequest.of(page, size, sort)));
    }

    @GetMapping("/{id}")
    @ApiResponse(
            responseCode = "200",
            description = "Active contact",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ContactResponse.class)))
    public ContactResponse get(@PathVariable(name = "id") UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @ApiResponse(
            responseCode = "200",
            description = "Updated contact",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ContactResponse.class)))
    public ContactResponse update(
            @PathVariable(name = "id") UUID id,
            @Valid @RequestBody CreateContactRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Contact archived or already archived", content = @Content)
    public void delete(@PathVariable(name = "id") UUID id) {
        service.archive(id);
    }
}
