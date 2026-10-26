package org.socialbridge.crm.shared;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.time.Instant;
import java.util.Map;
@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(NotFoundException.class) ResponseEntity<Map<String,Object>> notFound(NotFoundException e){return error(HttpStatus.NOT_FOUND,e.getMessage());}
 @ExceptionHandler(ConflictException.class) ResponseEntity<Map<String,Object>> conflict(ConflictException e){return error(HttpStatus.CONFLICT,e.getMessage());}
 @ExceptionHandler(BadRequestException.class) ResponseEntity<Map<String,Object>> invalid(BadRequestException e){return error(HttpStatus.BAD_REQUEST,e.getMessage());}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException e){return error(HttpStatus.BAD_REQUEST,"Request validation failed");}
 @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<Map<String,Object>> unreadable(HttpMessageNotReadableException e){return error(HttpStatus.BAD_REQUEST,"Malformed request body");}
 @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
 ResponseEntity<Map<String,Object>> mismatch(Exception e){return error(HttpStatus.BAD_REQUEST,"Invalid request parameter");}
 private ResponseEntity<Map<String,Object>> error(HttpStatus status,String message){return ResponseEntity.status(status).body(Map.of("timestamp",Instant.now().toString(),"status",status.value(),"message",message));}
}
