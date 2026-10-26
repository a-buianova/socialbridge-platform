package org.socialbridge.crm.contact;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class ContactApiTest {
 @Autowired MockMvc mvc;
 @Test void createThenGetAndPreventDuplicates() throws Exception {
  String body="{\"name\":\"Ada Example\",\"email\":\"ADA@example.org\",\"type\":\"DONOR\"}";
  mvc.perform(post("/api/v1/contacts").contentType(MediaType.APPLICATION_JSON).content(body))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("ada@example.org"));
  mvc.perform(post("/api/v1/contacts").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
  mvc.perform(get("/api/v1/contacts?type=DONOR")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
 }
 @Test void rejectsInvalidInputAndPagination() throws Exception {
  mvc.perform(post("/api/v1/contacts").contentType(MediaType.APPLICATION_JSON)
   .content("{\"name\":\"\",\"email\":\"invalid\",\"type\":\"DONOR\"}"))
   .andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/contacts?size=1000")).andExpect(status().isBadRequest());
 }
}
