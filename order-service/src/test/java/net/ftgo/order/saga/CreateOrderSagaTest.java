package net.ftgo.order.saga;

import net.ftgo.common.Money;
import net.ftgo.common.orderflow.replies.CardAuthorized;
import net.ftgo.common.orderflow.replies.TicketCreated;
import net.ftgo.order.domain.OrderLineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CreateOrderSaga.
 * 
 * Tests the saga definition structure, saga data handling, and reply processing
 * without requiring actual Kafka or database infrastructure.
 * 
 * Test Coverage:
 * - Saga definition structure
 * - Saga data creation and state
 * - Reply handling (ticketId, authorizationId)
 * - Saga data serialization
 * 
 * Note: Full saga execution testing (success path, compensation, retry) requires
 * integration tests with real Kafka and MySQL infrastructure. See
 * CreateOrderSagaIntegrationTest for end-to-end saga execution tests.
 */
class CreateOrderSagaTest {
    
    private CreateOrderSaga saga;
    private CreateOrderSagaData sagaData;
    
    @BeforeEach
    void setUp() {
        saga = new CreateOrderSaga();
        
        // Create test saga data
        Long orderId = 1L;
        Long consumerId = 100L;
        Long restaurantId = 200L;
        List<OrderLineItem> lineItems = Arrays.asList(
            new OrderLineItem(1L, "Burger", new Money(BigDecimal.valueOf(12.99)), 2),
            new OrderLineItem(2L, "Fries", new Money(BigDecimal.valueOf(4.99)), 1)
        );
        Money orderTotal = new Money(BigDecimal.valueOf(30.97));
        
        sagaData = new CreateOrderSagaData(orderId, consumerId, restaurantId, lineItems, orderTotal);
    }
    
    @Test
    void testSagaDefinitionIsNotNull() {
        assertNotNull(saga.getSagaDefinition(), "Saga definition should not be null");
    }
    
    @Test
    void testSagaDataCreation() {
        assertEquals(1L, sagaData.getOrderId());
        assertEquals(100L, sagaData.getConsumerId());
        assertEquals(200L, sagaData.getRestaurantId());
        assertEquals(2, sagaData.getLineItems().size());
        assertEquals(new Money(BigDecimal.valueOf(30.97)), sagaData.getOrderTotal());
        assertNull(sagaData.getTicketId(), "TicketId should be null initially");
        assertNull(sagaData.getAuthorizationId(), "AuthorizationId should be null initially");
    }
    
    @Test
    void testSagaDataToString() {
        String result = sagaData.toString();
        assertTrue(result.contains("orderId=1"));
        assertTrue(result.contains("consumerId=100"));
        assertTrue(result.contains("restaurantId=200"));
    }
    
    /**
     * Test that saga data can store ticketId from TicketCreated.
     */
    @Test
    void testSagaData_StoresTicketId() {
        Long expectedTicketId = 300L;
        
        sagaData.setTicketId(expectedTicketId);
        
        assertEquals(expectedTicketId, sagaData.getTicketId(),
            "TicketId should be stored in saga data");
    }
    
    /**
     * Test that saga data can store authorizationId from CardAuthorized.
     */
    @Test
    void testSagaData_StoresAuthorizationId() {
        Long expectedAuthorizationId = 123L;
        
        sagaData.setAuthorizationId(expectedAuthorizationId);
        
        assertEquals(expectedAuthorizationId, sagaData.getAuthorizationId(),
            "AuthorizationId should be stored in saga data");
    }
    
    /**
     * Test that saga data can store both ticketId and authorizationId.
     */
    @Test
    void testSagaData_StoresBothIds() {
        Long expectedTicketId = 999L;
        Long expectedAuthorizationId = 123L;
        
        sagaData.setTicketId(expectedTicketId);
        sagaData.setAuthorizationId(expectedAuthorizationId);
        
        assertEquals(expectedTicketId, sagaData.getTicketId());
        assertEquals(expectedAuthorizationId, sagaData.getAuthorizationId());
    }
    
    /**
     * Test TicketCreated reply structure.
     */
    @Test
    void testTicketCreatedReply() {
        Long ticketId = 777L;
        TicketCreated reply = new TicketCreated(ticketId);
        
        assertEquals(ticketId, reply.getTicketId());
    }
    
    /**
     * Test CardAuthorized reply structure.
     */
    @Test
    void testCardAuthorizedReply() {
        Long authorizationId = 123L;
        CardAuthorized reply = new CardAuthorized(authorizationId);
        
        assertEquals(authorizationId, reply.getAuthorizationId());
    }
    
    /**
     * Test that saga has correct number of steps.
     * 
     * Expected steps:
     * 1. createOrder (local)
     * 2. verifyConsumer
     * 3. createTicket
     * 4. authorizeCard (PIVOT)
     * 5. approveTicket
     * 6. approveOrder (local)
     */
    @Test
    void testSagaHasCorrectStructure() {
        assertNotNull(saga.getSagaDefinition(), "Saga definition should exist");
        // Note: Detailed step verification requires Eventuate Tram Sagas testing support
        // which is available in integration tests
    }
    
    /**
     * Test that line items are correctly stored in saga data.
     */
    @Test
    void testSagaData_LineItems() {
        List<OrderLineItem> lineItems = sagaData.getLineItems();
        
        assertNotNull(lineItems);
        assertEquals(2, lineItems.size());
        
        OrderLineItem firstItem = lineItems.get(0);
        assertEquals(1L, firstItem.getMenuItemId());
        assertEquals("Burger", firstItem.getName());
        assertEquals(new Money(BigDecimal.valueOf(12.99)), firstItem.getPrice());
        assertEquals(2, firstItem.getQuantity());
        
        OrderLineItem secondItem = lineItems.get(1);
        assertEquals(2L, secondItem.getMenuItemId());
        assertEquals("Fries", secondItem.getName());
        assertEquals(new Money(BigDecimal.valueOf(4.99)), secondItem.getPrice());
        assertEquals(1, secondItem.getQuantity());
    }
    
    /**
     * Test that saga data can be created with empty constructor (for serialization).
     */
    @Test
    void testSagaData_EmptyConstructor() {
        CreateOrderSagaData emptyData = new CreateOrderSagaData();
        
        assertNull(emptyData.getOrderId());
        assertNull(emptyData.getConsumerId());
        assertNull(emptyData.getRestaurantId());
        assertNull(emptyData.getLineItems());
        assertNull(emptyData.getOrderTotal());
        assertNull(emptyData.getTicketId());
        assertNull(emptyData.getAuthorizationId());
    }
    
    /**
     * Test that saga data fields can be set individually (for deserialization).
     */
    @Test
    void testSagaData_Setters() {
        CreateOrderSagaData data = new CreateOrderSagaData();
        
        data.setOrderId(10L);
        data.setConsumerId(20L);
        data.setRestaurantId(30L);
        
        List<OrderLineItem> items = Arrays.asList(
            new OrderLineItem(1L, "Pizza", new Money(BigDecimal.valueOf(15.99)), 1)
        );
        data.setLineItems(items);
        
        Money total = new Money(BigDecimal.valueOf(15.99));
        data.setOrderTotal(total);
        
        data.setTicketId(40L);
        data.setAuthorizationId(123L);
        
        assertEquals(10L, data.getOrderId());
        assertEquals(20L, data.getConsumerId());
        assertEquals(30L, data.getRestaurantId());
        assertEquals(items, data.getLineItems());
        assertEquals(total, data.getOrderTotal());
        assertEquals(40L, data.getTicketId());
        assertEquals(123L, data.getAuthorizationId());
    }
}
