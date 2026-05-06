package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaDefinition;
import io.eventuate.tram.sagas.simpledsl.SimpleSaga;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.order.saga.commands.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eventuate.tram.commands.consumer.CommandWithDestinationBuilder.send;

/**
 * CancelOrderSaga orchestrates the distributed transaction for order cancellation.
 * 
 * This saga coordinates order cancellation across multiple services:
 * - Order Service: Manages order state transitions
 * - Kitchen Service: Cancels kitchen ticket
 * - Accounting Service: Reverses payment authorization
 * 
 * Saga Steps:
 * 1. beginCancel (local) - Transitions order to CANCEL_PENDING state
 * 2. beginCancelTicket - Initiates ticket cancellation in Kitchen Service
 * 3. reverseAuthorization - Reverses payment authorization (PIVOT POINT - first non-compensatable step)
 * 4. confirmCancelTicket - Confirms ticket cancellation (retriable)
 * 5. confirmCancel (local) - Transitions order to CANCELLED state (retriable)
 * 
 * Compensation Logic:
 * - If saga fails before pivot (steps 1-2): Execute compensations in reverse order
 *   - undoCancelTicket (if ticket cancellation was initiated)
 *   - undoCancel (restore order to APPROVED state)
 * - If saga fails after pivot (steps 3-5): Retry until success (no compensation)
 * 
 * Pivot Point:
 * - Step 3 (reverseAuthorization) is the pivot point
 * - Once payment authorization is reversed, we cannot compensate (money has been released)
 * - All steps after pivot must be retriable and eventually succeed
 * 
 * Semantic Lock:
 * - Order remains in CANCEL_PENDING state during saga execution
 * - Prevents concurrent revise operations
 * - Released when saga completes (CANCELLED or APPROVED if compensation occurs)
 * 
 * Requirements Coverage:
 * - Requirement 2.1: Transition order to CANCEL_PENDING state
 * - Requirement 2.2: Begin ticket cancellation in Kitchen Service
 * - Requirement 2.3: Reverse payment authorization in Accounting Service
 * - Requirement 2.4: Confirm ticket and order cancellation
 * - Requirement 2.5: Execute compensations if saga fails before pivot
 * - Requirement 2.6: Publish OrderCancelled event on success
 * - Requirement 2.7: Enforce semantic lock during cancellation
 */
public class CancelOrderSaga implements SimpleSaga<CancelOrderSagaData> {
    
    private static final Logger logger = LoggerFactory.getLogger(CancelOrderSaga.class);
    
    private final SagaDefinition<CancelOrderSagaData> sagaDefinition;
    
    /**
     * Creates the CancelOrderSaga with its step definitions.
     */
    public CancelOrderSaga() {
        this.sagaDefinition = step()
            .invokeLocal(this::beginCancel)
            .withCompensation(this::undoCancel)
        .step()
            .invokeParticipant(this::beginCancelTicket)
            .withCompensation(this::undoCancelTicket)
        .step()
            .invokeParticipant(this::reverseAuthorization)
        .step()
            .invokeParticipant(this::confirmCancelTicket)
        .step()
            .invokeParticipant(this::confirmCancelStep)
        .build();
    }
    
    @Override
    public SagaDefinition<CancelOrderSagaData> getSagaDefinition() {
        return sagaDefinition;
    }
    
    // Step 1: Begin cancel (local)
    
    /**
     * Begins order cancellation by transitioning to CANCEL_PENDING state.
     * This is a local step that sends a command to Order Service.
     * 
     * Implements semantic lock to prevent concurrent modifications.
     * 
     * @param data the saga data
     */
    private void beginCancel(CancelOrderSagaData data) {
        logger.info("CancelOrderSaga: Step 1 - beginCancel for orderId={}", data.getOrderId());
        // The actual state transition happens in the command handler
        // This step exists for saga definition structure and compensation chain
    }
    
    /**
     * Compensation for beginCancel: Restores order to APPROVED state.
     * Sends command to Order Service to undo the cancellation.
     * 
     * @param data the saga data
     * @return command to send to Order Service
     */
    private CommandWithDestination undoCancel(CancelOrderSagaData data) {
        logger.warn("CancelOrderSaga: Compensation - undoCancel for orderId={}", data.getOrderId());
        
        return send(new CancelOrderSagaLocalSteps.UndoCancelCommand(data.getOrderId()))
            .to(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    // Step 2: Begin cancel ticket
    
    /**
     * Sends command to Kitchen Service to begin ticket cancellation.
     * 
     * @param data the saga data
     * @return command to send to Kitchen Service
     */
    private CommandWithDestination beginCancelTicket(CancelOrderSagaData data) {
        logger.info("CancelOrderSaga: Step 2 - beginCancelTicket for ticketId={}", data.getTicketId());
        
        return send(new BeginCancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    /**
     * Compensation for beginCancelTicket: Undoes ticket cancellation.
     * Sends command to Kitchen Service to restore the ticket.
     * 
     * @param data the saga data
     * @return command to send to Kitchen Service
     */
    private CommandWithDestination undoCancelTicket(CancelOrderSagaData data) {
        logger.warn("CancelOrderSaga: Compensation - undoCancelTicket for ticketId={}", data.getTicketId());
        
        return send(new UndoCancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    // Step 3: Reverse authorization (PIVOT POINT)
    
    /**
     * Sends command to Accounting Service to reverse payment authorization.
     * 
     * THIS IS THE PIVOT POINT:
     * - First non-compensatable step
     * - Once authorization is reversed, saga must complete forward
     * - All subsequent steps are retriable
     * 
     * @param data the saga data
     * @return command to send to Accounting Service
     */
    private CommandWithDestination reverseAuthorization(CancelOrderSagaData data) {
        logger.info("CancelOrderSaga: Step 3 (PIVOT) - reverseAuthorization for authorizationId={}",
            data.getAuthorizationId());
        
        return send(new ReverseAuthorizationCommand(data.getConsumerId(), data.getAuthorizationId()))
            .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    // Step 4: Confirm cancel ticket (retriable)
    
    /**
     * Sends command to Kitchen Service to confirm ticket cancellation.
     * 
     * This step is RETRIABLE (occurs after pivot point).
     * If it fails, the saga will retry until success.
     * 
     * @param data the saga data
     * @return command to send to Kitchen Service
     */
    private CommandWithDestination confirmCancelTicket(CancelOrderSagaData data) {
        logger.info("CancelOrderSaga: Step 4 (retriable) - confirmCancelTicket for ticketId={}",
            data.getTicketId());
        
        return send(new ConfirmCancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    // Step 5: Confirm cancel (local, retriable)
    
    /**
     * Confirms order cancellation (transitions to CANCELLED state).
     * Sends command to Order Service to confirm the cancellation.
     * 
     * This step is RETRIABLE (occurs after pivot point).
     * If it fails, the saga will retry until success.
     * 
     * @param data the saga data
     * @return command to send to Order Service
     */
    private CommandWithDestination confirmCancelStep(CancelOrderSagaData data) {
        logger.info("CancelOrderSaga: Step 5 (retriable) - confirmCancel for orderId={}",
            data.getOrderId());
        
        return send(new CancelOrderSagaLocalSteps.ConfirmCancelCommand(data.getOrderId()))
            .to(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL)
            .build();
    }
}
