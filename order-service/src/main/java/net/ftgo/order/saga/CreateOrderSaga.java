package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaDefinition;
import io.eventuate.tram.sagas.simpledsl.SimpleSaga;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.order.saga.commands.*;
import net.ftgo.order.saga.replies.AuthorizeCardReply;
import net.ftgo.order.saga.replies.CreateTicketReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eventuate.tram.commands.consumer.CommandWithDestinationBuilder.send;

/**
 * CreateOrderSaga orchestrates the distributed transaction for order placement.
 * 
 * This saga coordinates order approval across multiple services:
 * - Consumer Service: Verifies consumer credit limit
 * - Kitchen Service: Creates and approves kitchen ticket
 * - Accounting Service: Authorizes credit card payment
 * 
 * Saga Steps:
 * 1. createOrder (local) - Creates order in APPROVAL_PENDING state
 * 2. verifyConsumer - Validates consumer exists and has sufficient credit
 * 3. createTicket - Creates kitchen ticket in CREATE_PENDING state
 * 4. authorizeCard - Authorizes payment (PIVOT POINT - first non-compensatable step)
 * 5. approveTicket - Approves kitchen ticket (retriable)
 * 6. approveOrder (local) - Transitions order to APPROVED state (retriable)
 * 
 * Compensation Logic:
 * - If saga fails before pivot (steps 1-3): Execute compensations in reverse order
 *   - cancelTicket (if ticket was created)
 *   - rejectOrder (always executed)
 * - If saga fails after pivot (steps 4-6): Retry until success (no compensation)
 * 
 * Pivot Point:
 * - Step 4 (authorizeCard) is the pivot point
 * - Once payment is authorized, we cannot compensate (money has been reserved)
 * - All steps after pivot must be retriable and eventually succeed
 * 
 * Semantic Lock:
 * - Order remains in APPROVAL_PENDING state during saga execution
 * - Prevents concurrent cancel/revise operations
 * - Released when saga completes (APPROVED or REJECTED)
 */
public class CreateOrderSaga implements SimpleSaga<CreateOrderSagaData> {
    
    private static final Logger logger = LoggerFactory.getLogger(CreateOrderSaga.class);
    
    private final SagaDefinition<CreateOrderSagaData> sagaDefinition;
    
    /**
     * Creates the CreateOrderSaga with its step definitions.
     */
    public CreateOrderSaga() {
        this.sagaDefinition = step()
            .invokeLocal(this::createOrder)
            .withCompensation(this::rejectOrder)
        .step()
            .invokeParticipant(this::verifyConsumer)
        .step()
            .invokeParticipant(this::createTicket)
            .onReply(CreateTicketReply.class, this::handleCreateTicketReply)
            .withCompensation(this::cancelTicket)
        .step()
            .invokeParticipant(this::authorizeCard)
            .onReply(AuthorizeCardReply.class, this::handleAuthorizeCardReply)
        .step()
            .invokeParticipant(this::approveTicket)
        .step()
            .invokeParticipant(this::approveOrderStep)
        .build();
    }
    
    @Override
    public SagaDefinition<CreateOrderSagaData> getSagaDefinition() {
        return sagaDefinition;
    }
    
    // Step 1: Create order (local)
    
    /**
     * Creates the order in APPROVAL_PENDING state.
     * This is a local step that doesn't send commands to other services.
     * 
     * Note: The actual order creation happens before the saga starts.
     * This step is a placeholder for saga definition consistency.
     * 
     * @param data the saga data
     */
    private void createOrder(CreateOrderSagaData data) {
        logger.info("CreateOrderSaga: Step 1 - createOrder for orderId={}", data.getOrderId());
        // Order is already created in APPROVAL_PENDING state before saga starts
        // This step exists for saga definition structure and compensation chain
    }
    
    /**
     * Compensation for createOrder: Rejects the order.
     * Sends command to Order Service to reject the order.
     * 
     * @param data the saga data
     * @return command to send to Order Service
     */
    private CommandWithDestination rejectOrder(CreateOrderSagaData data) {
        logger.warn("CreateOrderSaga: Compensation - rejectOrder for orderId={}", data.getOrderId());
        
        return send(new CreateOrderSagaLocalSteps.RejectOrderCommand(data.getOrderId()))
            .to(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    // Step 2: Verify consumer
    
    /**
     * Sends command to Consumer Service to verify consumer credit limit.
     * 
     * @param data the saga data
     * @return command to send to Consumer Service
     */
    private CommandWithDestination verifyConsumer(CreateOrderSagaData data) {
        logger.info("CreateOrderSaga: Step 2 - verifyConsumer for consumerId={}, orderTotal={}",
            data.getConsumerId(), data.getOrderTotal());
        
        return send(new VerifyConsumerCommand(data.getConsumerId(), data.getOrderTotal()))
            .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    // Step 3: Create ticket
    
    /**
     * Sends command to Kitchen Service to create a ticket.
     * 
     * @param data the saga data
     * @return command to send to Kitchen Service
     */
    private CommandWithDestination createTicket(CreateOrderSagaData data) {
        logger.info("CreateOrderSaga: Step 3 - createTicket for orderId={}, restaurantId={}",
            data.getOrderId(), data.getRestaurantId());
        
        return send(new CreateTicketCommand(
                data.getOrderId(),
                data.getRestaurantId(),
                data.getLineItems()
            ))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    /**
     * Handles the reply from Kitchen Service after ticket creation.
     * Stores the ticket ID in saga data for use in subsequent steps.
     * 
     * @param data the saga data
     * @param reply the create ticket reply
     */
    private void handleCreateTicketReply(CreateOrderSagaData data, CreateTicketReply reply) {
        logger.info("CreateOrderSaga: Received CreateTicketReply with ticketId={}", reply.getTicketId());
        data.setTicketId(reply.getTicketId());
    }
    
    /**
     * Compensation for createTicket: Cancels the ticket.
     * Sends command to Kitchen Service to cancel the ticket.
     * 
     * @param data the saga data
     * @return command to send to Kitchen Service
     */
    private CommandWithDestination cancelTicket(CreateOrderSagaData data) {
        logger.warn("CreateOrderSaga: Compensation - cancelTicket for ticketId={}", data.getTicketId());
        
        return send(new CancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    // Step 4: Authorize card (PIVOT POINT)
    
    /**
     * Sends command to Accounting Service to authorize credit card payment.
     * 
     * THIS IS THE PIVOT POINT:
     * - First non-compensatable step
     * - Once authorization succeeds, saga must complete forward
     * - All subsequent steps are retriable
     * 
     * @param data the saga data
     * @return command to send to Accounting Service
     */
    private CommandWithDestination authorizeCard(CreateOrderSagaData data) {
        logger.info("CreateOrderSaga: Step 4 (PIVOT) - authorizeCard for consumerId={}, amount={}",
            data.getConsumerId(), data.getOrderTotal());
        
        // Use orderId as requestId for idempotency
        String requestId = "order-" + data.getOrderId();
        
        return send(new AuthorizeCardCommand(
                data.getConsumerId(),
                data.getOrderTotal(),
                requestId
            ))
            .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    /**
     * Handles the reply from Accounting Service after authorization.
     * Stores the authorization ID in saga data.
     * 
     * @param data the saga data
     * @param reply the authorize card reply
     */
    private void handleAuthorizeCardReply(CreateOrderSagaData data, AuthorizeCardReply reply) {
        logger.info("CreateOrderSaga: Received AuthorizeCardReply with authorizationId={}", 
            reply.getAuthorizationId());
        data.setAuthorizationId(reply.getAuthorizationId());
    }
    
    // Step 5: Approve ticket (retriable)
    
    /**
     * Sends command to Kitchen Service to approve the ticket.
     * 
     * This step is RETRIABLE (occurs after pivot point).
     * If it fails, the saga will retry until success.
     * 
     * @param data the saga data
     * @return command to send to Kitchen Service
     */
    private CommandWithDestination approveTicket(CreateOrderSagaData data) {
        logger.info("CreateOrderSaga: Step 5 (retriable) - approveTicket for ticketId={}", 
            data.getTicketId());
        
        return send(new ApproveTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    // Step 6: Approve order (local, retriable)
    
    /**
     * Approves the order (transitions to APPROVED state).
     * Sends command to Order Service to approve the order.
     * 
     * This step is RETRIABLE (occurs after pivot point).
     * If it fails, the saga will retry until success.
     * 
     * @param data the saga data
     * @return command to send to Order Service
     */
    private CommandWithDestination approveOrderStep(CreateOrderSagaData data) {
        logger.info("CreateOrderSaga: Step 6 (retriable) - approveOrder for orderId={}", 
            data.getOrderId());
        
        return send(new CreateOrderSagaLocalSteps.ApproveOrderCommand(data.getOrderId()))
            .to(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL)
            .build();
    }
}
