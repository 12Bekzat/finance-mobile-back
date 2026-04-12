package finance.mobile.finance;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb-finance;DB_CLOSE_DELAY=-1",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FinanceDataIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void createTransactionsAndGoalsForCurrentUser() throws Exception {
        MvcResult registerResult = mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fullName": "Finance User",
                          "email": "finance@example.com",
                          "password": "123456"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andReturn();

        String token = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.token");

        mockMvc
            .perform(
                post("/api/transactions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Salary",
                          "amount": 3500,
                          "type": "income",
                          "category": "Salary",
                          "paymentMethod": "Bank Transfer",
                          "transactionDate": "2026-03-28"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Salary"))
            .andExpect(jsonPath("$.type").value("income"))
            .andExpect(jsonPath("$.amount").value(3500.00));

        mockMvc
            .perform(
                post("/api/transactions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Groceries",
                          "amount": 85.40,
                          "type": "expense",
                          "category": "Food",
                          "paymentMethod": "Card",
                          "transactionDate": "2026-03-29"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("expense"));

        mockMvc
            .perform(get("/api/transactions").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Groceries"))
            .andExpect(jsonPath("$[1].title").value("Salary"));

        MvcResult goalResult = mockMvc
            .perform(
                post("/api/goals")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Emergency Fund",
                          "targetAmount": 1000,
                          "daysLeft": 60
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("active"))
            .andReturn();

        Number goalId = JsonPath.read(goalResult.getResponse().getContentAsString(), "$.id");

        mockMvc
            .perform(
                post("/api/goals/" + goalId.longValue() + "/contributions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "amount": 1000
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.savedAmount").value(1000.00));

        mockMvc
            .perform(get("/api/goals").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").isEmpty())
            .andExpect(jsonPath("$.completed[0].title").value("Emergency Fund"));

        mockMvc
            .perform(
                post("/api/cards")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "holderName": "Finance User",
                          "cardNumber": "4111111111111111",
                          "expiry": "12/30",
                          "cvc": "123"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.brand").value("Visa"))
            .andExpect(jsonPath("$.last4").value("1111"))
            .andExpect(jsonPath("$.isDefault").value(true));

        mockMvc
            .perform(get("/api/notifications").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(3))
            .andExpect(jsonPath("$.notifications[0].type").value("card_linked"))
            .andExpect(jsonPath("$.notifications[1].type").value("goal_completed"))
            .andExpect(jsonPath("$.notifications[2].type").value("goal_created"));

        mockMvc
            .perform(MockMvcRequestBuilders.delete("/api/goals/" + goalId.longValue()).header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
    }
}
