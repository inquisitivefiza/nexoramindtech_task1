package com.example.taskmanager;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskManagerApiIntegrationTest {

    private static final String PASSWORD = "Passw0rdSecure";

    @Autowired
    private MockMvc mvc;

    // ------------------------------------------------------------------ auth

    @Test
    @DisplayName("register: 201 and never exposes the password or its hash")
    void registerSuccess() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Jane Doe", email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist());
    }

    @Test
    @DisplayName("register: duplicate email (case-insensitive) -> 409")
    void registerDuplicateEmail() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Other", email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("register: invalid input -> 400 with per-field details")
    void registerValidation() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("", "not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    @DisplayName("login: valid credentials -> 200 + JWT")
    void loginSuccess() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(86400));
    }

    @Test
    @DisplayName("login: wrong password and unknown email give the same 401")
    void loginFailure() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "WrongPass123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(uniqueEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("malformed JSON -> 400")
    void malformedJson() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------- authentication

    @Test
    @DisplayName("tasks: no token -> 401 JSON")
    void tasksRequireToken() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message", containsString("Authentication required")));
    }

    @Test
    @DisplayName("tasks: garbage token -> 401 JSON")
    void tasksRejectInvalidToken() throws Exception {
        mvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid authentication token"));
    }

    // ------------------------------------------------------------- task CRUD

    @Test
    @DisplayName("tasks: full create / read / update / delete lifecycle")
    void taskLifecycle() throws Exception {
        String token = newUserToken();

        // create (defaults applied)
        String body = mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write report\",\"description\":\"Q4 numbers\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.title").value("Write report"))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.priority").value("medium"))
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        // read
        mvc.perform(get("/api/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // update
        mvc.perform(put("/api/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write final report\",\"status\":\"in_progress\",\"priority\":\"high\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Write final report"))
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.priority").value("high"))
                // PUT replaces the description; it was omitted, so it is cleared
                .andExpect(jsonPath("$.description").value(nullValue()));

        // delete, then it's gone
        mvc.perform(delete("/api/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Task deleted successfully"));
        mvc.perform(get("/api/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("tasks: another user's task is invisible (404) for read, update and delete")
    void tasksAreIsolatedPerUser() throws Exception {
        String owner = newUserToken();
        String intruder = newUserToken();
        long id = createTask(owner, "{\"title\":\"Private task\"}");

        mvc.perform(get("/api/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(0));

        // untouched for the owner
        mvc.perform(get("/api/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Private task"));
    }

    @Test
    @DisplayName("tasks: pagination, filtering and sorting")
    void listPaginationAndFilters() throws Exception {
        String token = newUserToken();
        createTask(token, "{\"title\":\"A\",\"status\":\"pending\",\"priority\":\"low\"}");
        createTask(token, "{\"title\":\"B\",\"status\":\"completed\",\"priority\":\"high\"}");
        createTask(token, "{\"title\":\"C\",\"status\":\"pending\",\"priority\":\"high\"}");

        mvc.perform(get("/api/tasks?page=0&size=2").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.total_elements").value(3))
                .andExpect(jsonPath("$.total_pages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mvc.perform(get("/api/tasks?page=1&size=2").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.last").value(true));

        mvc.perform(get("/api/tasks?status=pending&priority=high").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("C"));

        mvc.perform(get("/api/tasks?sort=title&order=asc").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[0].title").value("A"))
                .andExpect(jsonPath("$.content[2].title").value("C"));
    }

    // ------------------------------------------------------------ validation

    @Test
    @DisplayName("tasks: blank title -> 400 with details")
    void createTaskValidation() throws Exception {
        String token = newUserToken();
        mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").value("Title is required"));
    }

    @Test
    @DisplayName("tasks: invalid enum value in body -> 400 listing allowed values")
    void invalidEnumInBody() throws Exception {
        String token = newUserToken();
        mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"status\":\"done\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Allowed values")))
                .andExpect(jsonPath("$.message", containsString("in_progress")));
    }

    @Test
    @DisplayName("tasks: bad query params / path id -> 400, unknown id -> 404")
    void badParameters() throws Exception {
        String token = newUserToken();
        mvc.perform(get("/api/tasks?status=nope").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks?size=1000").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks?page=-1").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks?sort=password").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks/abc").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks/999999").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task with id 999999 not found"));
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String registerJson(String name, String email, String password) {
        return "{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, password);
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Test User", email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String newUserToken() throws Exception {
        String email = uniqueEmail();
        register(email);
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.access_token");
    }

    private long createTask(String token, String json) throws Exception {
        String body = mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
