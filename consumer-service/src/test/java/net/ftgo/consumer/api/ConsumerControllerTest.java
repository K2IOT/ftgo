package net.ftgo.consumer.api;

import net.ftgo.common.Money;
import net.ftgo.consumer.config.ConsumerServiceConfiguration;
import net.ftgo.consumer.config.EventuateTramTestConfiguration;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.repository.ConsumerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ConsumerController REST API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(EventuateTramTestConfiguration.class)
class ConsumerControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ConsumerRepository consumerRepository;
    
    @BeforeEach
    void setUp() {
        consumerRepository.deleteAll();
    }
    
    @Test
    void testCreateConsumer() throws Exception {
        String requestBody = """
            {
                "name": "John Doe",
                "email": "john.doe@example.com",
                "creditLimit": 100.00
            }
            """;
        
        mockMvc.perform(post("/consumers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.creditLimit", is(100.00)))
                .andExpect(jsonPath("$.availableCredit", is(100.00)));
    }
    
    @Test
    void testCreateConsumerWithDuplicateEmail() throws Exception {
        // Create first consumer
        Consumer consumer = new Consumer("Jane Doe", "jane@example.com", new Money("100.00"));
        consumerRepository.save(consumer);
        
        // Try to create second consumer with same email
        String requestBody = """
            {
                "name": "Jane Smith",
                "email": "jane@example.com",
                "creditLimit": 200.00
            }
            """;
        
        mockMvc.perform(post("/consumers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("already exists")));
    }
    
    @Test
    void testGetConsumer() throws Exception {
        // Create consumer
        Consumer consumer = new Consumer("Alice Smith", "alice@example.com", new Money("150.00"));
        consumer = consumerRepository.save(consumer);
        
        mockMvc.perform(get("/consumers/" + consumer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(consumer.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Alice Smith")))
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.creditLimit", is(150.00)));
    }
    
    @Test
    void testGetConsumerNotFound() throws Exception {
        mockMvc.perform(get("/consumers/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("not found")));
    }
    
    @Test
    void testUpdateConsumer() throws Exception {
        // Create consumer
        Consumer consumer = new Consumer("Bob Johnson", "bob@example.com", new Money("200.00"));
        consumer = consumerRepository.save(consumer);
        
        String requestBody = """
            {
                "name": "Robert Johnson",
                "email": "robert@example.com"
            }
            """;
        
        mockMvc.perform(put("/consumers/" + consumer.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(consumer.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Robert Johnson")))
                .andExpect(jsonPath("$.email", is("robert@example.com")))
                .andExpect(jsonPath("$.creditLimit", is(200.00)));
    }
    
    @Test
    void testUpdateConsumerNotFound() throws Exception {
        String requestBody = """
            {
                "name": "Test User",
                "email": "test@example.com"
            }
            """;
        
        mockMvc.perform(put("/consumers/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("not found")));
    }
    
    @Test
    void testCreateConsumerWithInvalidEmail() throws Exception {
        String requestBody = """
            {
                "name": "Invalid User",
                "email": "invalid-email",
                "creditLimit": 100.00
            }
            """;
        
        mockMvc.perform(post("/consumers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void testCreateConsumerWithNegativeCreditLimit() throws Exception {
        String requestBody = """
            {
                "name": "Test User",
                "email": "test@example.com",
                "creditLimit": -50.00
            }
            """;
        
        mockMvc.perform(post("/consumers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
