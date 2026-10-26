package org.socialbridge.crm.contact;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the real Flyway schema and API; Docker is deliberately a required prerequisite. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ContactApiTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("socialbridge_test")
            .withUsername("socialbridge_test")
            .withPassword("synthetic_test_only");

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CONTACTS = "/api/v1/contacts";

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ContactRepository repository;
    @Autowired ContactService service;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void clearContacts() {
        jdbc.update("DELETE FROM contacts");
    }

    @Test
    void runsBothMigrationsAndCreatesPostgresqlConstraints() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class)).containsExactly("1", "2");
        assertThat(jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name='contacts' AND column_name='email'",
                String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename='contacts' AND indexname='uq_contact_email_lower'",
                String.class)).contains("UNIQUE", "lower");
        assertThat(jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name='contacts' AND column_name='version'",
                String.class)).isEqualTo("NO");
    }

    @Test
    void upgradesExistingPrototypeDataWithoutLosingTheEmailReservation() {
        UUID existing = UUID.randomUUID();
        try {
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas("prototype_upgrade")
                    .defaultSchema("prototype_upgrade")
                    .target("1")
                    .load()
                    .migrate();
            jdbc.update("INSERT INTO prototype_upgrade.contacts(id,name,email,type,created_at) VALUES (?,?,?,?,?)",
                    existing, "Existing Example", "existing@example.org", "DONOR", Timestamp.from(Instant.now()));
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas("prototype_upgrade")
                    .defaultSchema("prototype_upgrade")
                    .load()
                    .migrate();
            assertThat(jdbc.queryForObject("SELECT version FROM prototype_upgrade.contacts WHERE id=?", Long.class, existing))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT email FROM prototype_upgrade.contacts WHERE id=?", String.class, existing))
                    .isEqualTo("existing@example.org");
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO prototype_upgrade.contacts(id,name,email,type,created_at) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), "Duplicate Example", "EXISTING@example.org", "DONOR", Timestamp.from(Instant.now())))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("INSERT INTO prototype_upgrade.contacts(id,name,type,created_at) VALUES (?,?,?,?)",
                    UUID.randomUUID(), "Offline Example", "DONOR", Timestamp.from(Instant.now()));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS prototype_upgrade CASCADE");
        }
    }

    @Test
    void createsReadsReplacesAndArchivesAContact() throws Exception {
        MvcResult created = mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON)
                        .content(body("  Ada Example  ", "  ADA@EXAMPLE.ORG  ", "DONOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ada Example"))
                .andExpect(jsonPath("$.email").value("ada@example.org"))
                .andExpect(jsonPath("$.archived").value(false))
                .andReturn();
        UUID id = id(created);
        assertThat(created.getResponse().getHeader("Location")).isEqualTo(CONTACTS + "/" + id);
        mvc.perform(get(CONTACTS + "/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        mvc.perform(put(CONTACTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ada Updated", " updated@example.org ", "VOLUNTEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Updated"))
                .andExpect(jsonPath("$.email").value("updated@example.org"))
                .andExpect(jsonPath("$.type").value("VOLUNTEER"));
        mvc.perform(delete(CONTACTS + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete(CONTACTS + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(CONTACTS + "/" + id)).andExpect(status().isNotFound());
        mvc.perform(put(CONTACTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ada Updated", "updated@example.org", "DONOR")))
                .andExpect(status().isNotFound());
        mvc.perform(get(CONTACTS)).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT archived FROM contacts WHERE id=?", Boolean.class, id)).isTrue();
    }

    @Test
    void supportsMissingNullAndBlankEmailsWithoutInventingDuplicates() throws Exception {
        mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Offline Example\",\"type\":\"DONOR\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(nullValue()));
        mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anonymous Example\",\"email\":null,\"type\":\"DONOR\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(nullValue()));
        UUID blankEmail = create("Blank Example", "  ", "SUPPORTER");
        mvc.perform(get(CONTACTS + "/" + blankEmail))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(nullValue()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contacts WHERE email IS NULL", Integer.class)).isEqualTo(3);
    }

    @Test
    void duplicateEmailIsCaseInsensitiveAndRemainsReservedAfterArchival() throws Exception {
        UUID original = create("Ada Example", "ada@example.org", "DONOR");
        mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Other Example", " ADA@EXAMPLE.ORG ", "PARTNER")))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(delete(CONTACTS + "/" + original)).andExpect(status().isNoContent());
        mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Other Example", "ada@example.org", "PARTNER")))
                .andExpect(status().isConflict());
    }

    @Test
    void replacingOwnEmailIsAllowedButAnotherContactsEmailIsNot() throws Exception {
        UUID first = create("First Example", "first@example.org", "DONOR");
        UUID second = create("Second Example", "second@example.org", "DONOR");
        mvc.perform(put(CONTACTS + "/" + first).contentType(MediaType.APPLICATION_JSON)
                        .content(body("First Updated", "FIRST@EXAMPLE.ORG", "SUPPORTER")))
                .andExpect(status().isOk());
        mvc.perform(put(CONTACTS + "/" + second).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Second Updated", " first@example.org ", "SUPPORTER")))
                .andExpect(status().isConflict());
        mvc.perform(get(CONTACTS + "/" + second)).andExpect(jsonPath("$.email").value("second@example.org"));
        mvc.perform(put(CONTACTS + "/" + first).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Offline Updated\",\"type\":\"DONOR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(nullValue()));
    }

    @Test
    void stablePaginationUsesIdToBreakIdenticalTimestampTiesAndFiltersType() throws Exception {
        UUID first = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID second = UUID.fromString("00000000-0000-4000-8000-000000000002");
        insertDirectly(first, "first@example.org");
        insertDirectly(second, "second@example.org");
        create("Other Example", "other@example.org", "VOLUNTEER");
        jdbc.update("UPDATE contacts SET created_at=?", Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        List<String> expected = List.of(second.toString(), first.toString());
        for (int page = 0; page < 2; page++) {
            mvc.perform(get(CONTACTS).param("type", "DONOR").param("page", Integer.toString(page)).param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(expected.get(page)))
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.pageable").doesNotExist());
        }
        mvc.perform(get(CONTACTS).param("type", "DONOR").param("page", "2").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "size=abc", "type=INVALID", "page=2147483648",
            "page=2147483647&size=20", "page=107374183&size=20"})
    void rejectsInvalidQueryParameters(String query) throws Exception {
        mvc.perform(get(CONTACTS + "?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value(CONTACTS));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"\",\"email\":\"invalid\",\"type\":\"DONOR\"}",
            "{\"name\":\"   \",\"type\":\"DONOR\"}",
            "{\"name\":\"Ada Example\",\"email\":\"not-an-email\",\"type\":\"DONOR\"}",
            "{\"name\":\"Ada Example\",\"type\":null}",
            "{\"name\":\"Ada Example\",\"type\":\"INVALID\"}",
            "{\"name\":\"Ada Example\",\"type\":123}",
            "{\"name\":\"Ada Example\",\"type\":\"DONOR\"",
            "null"
    })
    void rejectsInvalidRequestBodies(String request) throws Exception {
        mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsOverlongFieldsAndMissingBody() throws Exception {
        mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON).content(body("x".repeat(121), "ada@example.org", "DONOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ada Example", "x".repeat(243) + "@example.org", "DONOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    void missingRecordsMalformedIdsUnsupportedMethodsAndUnknownPathsHaveSafeErrors() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get(CONTACTS + "/" + missing)).andExpect(status().isNotFound());
        mvc.perform(delete(CONTACTS + "/" + missing)).andExpect(status().isNotFound());
        mvc.perform(put(CONTACTS + "/" + missing).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ada Example", "ada@example.org", "DONOR")))
                .andExpect(status().isNotFound());
        mvc.perform(get(CONTACTS + "/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(patch(CONTACTS + "/" + missing).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/v1/nonexistent")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void validationUsesProblemDetailsAndDoesNotReflectRejectedPersonalData() throws Exception {
        MvcResult result = mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "synthetic-invalid-email", "DONOR")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value(CONTACTS))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[1].field").value("name"))
                .andReturn();
        Map<String, Object> problem = JsonPath.read(result.getResponse().getContentAsString(), "$");
        // RFC 9457 defines an omitted type as about:blank; Spring 7 omits this default.
        assertThat(problem.getOrDefault("type", "about:blank")).isEqualTo("about:blank");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("synthetic-invalid-email", "rejectedValue");
        String traceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(UUID.fromString(traceId).toString()).isEqualTo(traceId);
        assertThat(result.getResponse().getHeader("X-Request-ID")).isEqualTo(traceId);
    }

    @Test
    void reusesOnlyValidUuidRequestIdsAndGeneratesSafeIdsOtherwise() throws Exception {
        String requestId = "A0B1C2D3-1111-4222-8333-000000000001";
        mvc.perform(get(CONTACTS).header("X-Request-ID", requestId))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", requestId.toLowerCase()));
        MvcResult invalid = mvc.perform(get(CONTACTS + "/not-a-uuid").header("X-Request-ID", "unsafe\r\nheader"))
                .andExpect(status().isBadRequest()).andReturn();
        String safe = invalid.getResponse().getHeader("X-Request-ID");
        assertThat(UUID.fromString(safe).toString()).isEqualTo(safe);
        assertThat(JsonPath.<String>read(invalid.getResponse().getContentAsString(), "$.traceId")).isEqualTo(safe);
    }

    @Test
    void databaseRejectsCaseVariantsEvenWhenApplicationChecksAreBypassed() throws Exception {
        create("Ada Example", "ada@example.org", "DONOR");
        assertThatThrownBy(() -> insertDirectly(UUID.randomUUID(), "ADA@EXAMPLE.ORG"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contacts", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentHttpCreatesReturnOneCreatedAndOneConflict() throws Exception {
        List<Integer> statuses = inParallel(
                () -> mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON)
                                .content(body("First Example", "race@example.org", "DONOR")))
                        .andReturn().getResponse().getStatus(),
                () -> mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON)
                                .content(body("Second Example", "RACE@example.org", "PARTNER")))
                        .andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contacts WHERE lower(email)='race@example.org'", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentHttpUpdatesToOneEmailReturnOneSuccessAndOneConflict() throws Exception {
        UUID first = create("First Example", "first@example.org", "DONOR");
        UUID second = create("Second Example", "second@example.org", "DONOR");
        List<Integer> statuses = inParallel(
                () -> mvc.perform(put(CONTACTS + "/" + first).contentType(MediaType.APPLICATION_JSON)
                                .content(body("First Updated", "shared@example.org", "DONOR")))
                        .andReturn().getResponse().getStatus(),
                () -> mvc.perform(put(CONTACTS + "/" + second).contentType(MediaType.APPLICATION_JSON)
                                .content(body("Second Updated", "SHARED@example.org", "DONOR")))
                        .andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contacts WHERE lower(email)='shared@example.org'", Integer.class)).isEqualTo(1);
    }

    @Test
    void databaseSerializesConcurrentInsertsAfterBothPrechecksMiss() throws Exception {
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        List<Boolean> committed = inParallel(
                () -> insertAfterPrecheck(bothChecked, "simultaneous@example.org"),
                () -> insertAfterPrecheck(bothChecked, "SIMULTANEOUS@example.org"));
        assertThat(committed).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contacts", Integer.class)).isEqualTo(1);
    }

    @Test
    void staleUpdateCannotResurrectAConcurrentlyArchivedContact() throws Exception {
        UUID id = create("Ada Example", "ada@example.org", "DONOR");
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch archived = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Void> staleWrite = executor.submit(() -> {
                EntityManager manager = entityManagerFactory.createEntityManager();
                try {
                    manager.getTransaction().begin();
                    Contact stale = manager.find(Contact.class, id);
                    loaded.countDown();
                    if (!archived.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Archive transaction did not finish");
                    }
                    stale.update("Stale Example", "stale@example.org", ContactType.SUPPORTER);
                    manager.flush();
                    manager.getTransaction().commit();
                    return null;
                } finally {
                    if (manager.getTransaction().isActive()) {
                        manager.getTransaction().rollback();
                    }
                    manager.close();
                }
            });
            try {
                assertThat(loaded.await(15, TimeUnit.SECONDS)).isTrue();
                service.archive(id);
            } finally {
                archived.countDown();
            }
            assertThatThrownBy(() -> staleWrite.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(OptimisticLockException.class);
        }
        assertThat(jdbc.queryForObject("SELECT archived FROM contacts WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT name FROM contacts WHERE id=?", String.class, id)).isEqualTo("Ada Example");
    }

    @Test
    void generatedOpenApiDescribesCurrentContactRoutes() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/contacts'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contacts'].post.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/contacts'].post.responses['201'].headers.Location.schema.type").value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/contacts'].post.responses['201'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ContactResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/contacts'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ContactPage"))
                .andExpect(jsonPath("$.paths['/api/v1/contacts/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contacts/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contacts/{id}'].delete.responses['204'].content").doesNotExist())
                .andReturn();
        String specification = result.getResponse().getContentAsString();
        List<String> required = JsonPath.read(specification, "$.components.schemas.CreateContactRequest.required");
        assertThat(required).containsExactlyInAnyOrder("name", "type");
        List<String> requestEmailTypes = JsonPath.read(specification, "$.components.schemas.CreateContactRequest.properties.email.type");
        List<String> responseEmailTypes = JsonPath.read(specification, "$.components.schemas.ContactResponse.properties.email.type");
        assertThat(requestEmailTypes).containsExactlyInAnyOrder("string", "null");
        assertThat(responseEmailTypes).containsExactlyInAnyOrder("string", "null");
        Map<String, Object> pageProperties = JsonPath.read(specification, "$.components.schemas.ContactPage.properties");
        assertThat(pageProperties.keySet()).containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
    }

    private boolean insertAfterPrecheck(CyclicBarrier bothChecked, String email) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                assertThat(repository.existsByEmailIgnoreCase(email)).isFalse();
                await(bothChecked);
                repository.saveAndFlush(new Contact("Concurrent Example", email, ContactType.DONOR));
            });
            return true;
        } catch (DataIntegrityViolationException expected) {
            return false;
        }
    }

    private void insertDirectly(UUID id, String email) {
        jdbc.update("INSERT INTO contacts(id,name,email,type,created_at,archived,version) VALUES (?,?,?,?,?,false,0)",
                id, "Database Example", email, "DONOR", Timestamp.from(Instant.now()));
    }

    private UUID create(String name, String email, String type) throws Exception {
        return id(mvc.perform(post(CONTACTS).contentType(MediaType.APPLICATION_JSON).content(body(name, email, type)))
                .andExpect(status().isCreated()).andReturn());
    }

    private UUID id(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private String body(String name, String email, String type) {
        return JSON.writeValueAsString(Map.of("name", name, "email", email, "type", type));
    }

    private <T> List<T> inParallel(Callable<T> first, Callable<T> second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<T>> futures = List.of(executor.submit(() -> { await(start); return first.call(); }),
                    executor.submit(() -> { await(start); return second.call(); }));
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent test rendezvous failed", exception);
        }
    }
}
