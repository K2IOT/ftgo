package net.ftgo.order.saga;

import net.ftgo.common.Money;
import net.ftgo.order.domain.OrderLineItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReviseOrderSaga.
 * 
 * Tests the saga definition structure and data handling.
 */
class ReviseOrderSagaTest {
    
    @Test
    void testSagaDefinitionIsNotNull() {
        ReviseOrderSaga saga = new ReviseOrderSaga();
        assertNotNull(saga.getSagaDefinition(), "Saga definition should not be null");
    }
    
    @Test
    void testSagaDataCreation() {
        Long orderId = 1L;
        List<OrderLineItem> revisedLineItems = Arrays.asList(
            new OrderLineItem(1L, "Burger", new Money(BigDecimal.valueOf(12.99)), 2),
            new OrderLineItem(2L, "Fries", new Money(BigDecimal.valueOf(4.99)), 1)
        );
        Money revisedTotal = new Money(BigDecimal.valueOf(30.97));
        Long ticketId = 100L;
        String authorizationId = "auth-123";
        
        ReviseOrderSagaData data = new ReviseOrderSagaData(
            orderId, 
            revisedLineItems, 
            revisedTotal, 
            ticketId, 
            authorizationId
        );
        
        assertEquals(orderId, data.getOrderId());
        assertEquals(revisedLineItems, data.getRevisedLineItems());
        assertEquals(revisedTotal, data.getRevisedTotal());
        assertEquals(ticketId, data.getTicketId());
        assertEquals(authorizationId, data.getAuthorizationId());
    }
    
    @Test
    void testSagaDataToString() {
        ReviseOrderSagaData data = new ReviseOrderSagaData(
            1L,
            Arrays.asList(new OrderLineItem(1L, "Burger", new Money(BigDecimal.valueOf(12.99)), 2)),
            new Money(BigDecimal.valueOf(25.98)),
            100L,
            "auth-123"
        );
        
        String result = data.toString();
        assertTrue(result.contains("orderId=1"));
        assertTrue(result.contains("ticketId=100"));
        assertTrue(result.contains("authorizationId=auth-123"));
    }
}
