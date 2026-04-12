package finance.mobile.finance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void registerLoginAndReadCurrentUser() throws Exception {
        String requestBody = """
            {
              "fullName": "Test User",
              "email": "test@example.com",
              "password": "123456"
            }
            """;

        MvcResult registerResult = mockMvc
            .perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andReturn();

        String registerToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.token");

        mockMvc
            .perform(get("/api/auth/me").header("Authorization", "Bearer " + registerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.fullName").value("Test User"))
            .andExpect(jsonPath("$.user.email").value("test@example.com"));

        String updateBody = """
            {
              "fullName": "Updated User",
              "email": "test@example.com",
              "avatarUrl": "https://example.com/avatar.png",
              "currentPassword": "123456",
              "newPassword": "654321"
            }
            """;

        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "test@example.com",
                          "password": "123456"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.hasPassword").value(true));

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/profile")
                    .header("Authorization", "Bearer " + registerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.fullName").value("Updated User"))
            .andExpect(jsonPath("$.user.avatarUrl").value("https://example.com/avatar.png"));

        String loginBody = """
            {
              "email": "test@example.com",
              "password": "654321"
            }
            """;

        mockMvc
            .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.user.fullName").value("Updated User"));
    }
}
