package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaDefinition;
import io.eventuate.tram.sagas.simpledsl.SimpleSaga;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.BeginReviseTicketCommand;
import net.ftgo.common.orderflow.commands.ConfirmReviseTicketCommand;
import net.ftgo.common.orderflow.commands.CreateTicketCommand;
import net.ftgo.common.orderflow.commands.ReviseAuthorizationCommand;
import net.ftgo.common.orderflow.commands.UndoReviseTicketCommand;
import net.ftgo.common.orderflow.replies.AuthorizationRevised;
import net.ftgo.order.domain.OrderLineItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.eventuate.tram.commands.consumer.CommandWithDestinationBuilder.send;

/**
 * ReviseOrderSaga orchestrates the distributed transaction for order revision.
 * 
 * This saga coordinates order revision across multiple services:
 * - Order Service: Manages order state transitions
 * - Kitchen Service: Updates kitchen ticket with revised line items
 * - Accounting Service: Adjusts payment authorization to new total
 * 
 * Saga Steps:
 * 1. beginRevise (local) - Transitions order to REVISION_PENDING state
 * 2. beginReviseTicket - Updates ticket with revised line items in Kitchen Service
 * 3. reviseCreditCardAuthorization - Adjusts payment authorization (PIVOT POINT - first non-compensatable step)
 * 4. confirmReviseTicket - Confirms ticket revision (retriable)
 * 5. confirmRevise (local) - Updates order details and transitions to APPROVED state (retriable)
 * 
 * Compensation Logic:
 * - If saga fails before pivot (steps 1-2): Execute compensations in reverse order
 *   - undoReviseTicket (if ticket revision was initiated)
 *   - undoRevise (restore order to APPROVED state)
 * - If saga fails after pivot (steps 3-5): Retry until success (no compensation)
 * 
 * Pivot Point:
 * - Step 3 (reviseCreditCardAuthorization) is the pivot point
 * - Once payment authorization is adjusted, we cannot compensate (money has been re-reserved)
 * - All steps after pivot must be retriable and eventually succeed
 * 
 * Semantic Lock:
 * - Order remains in REVISION_PENDING state during saga execution
 * - Prevents concurrent cancel or additional revise operations
 * - Released when saga completes (APPROVED if success, or APPROVED if compensation occurs)
 * 
 * Requirements Coverage:
 * - Requirement 3.1: Transition order to REVISION_PENDING state
 * - Requirement 3.2: Update ticket with revised line items in Kitchen Service
 * - Requirement 3.3: Revise payment authorization in Accounting Service
 * - Requirement 3.4: Confirm ticket and order revision
 * - Requirement 3.5: Execute compensations if saga fails before pivot
 * - Requirement 3.6: Publish OrderRevised event on success
 * - Requirement 3.7: Enforce semantic lock during revision
 * - Requirement 3.8: Ensure revised total equals sum of line item prices plus delivery fee
 */
public class ReviseOrderSaga implements SimpleSaga<ReviseOrderSagaData> {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviseOrderSaga.class);
    
    private final ReviseOrderSagaLocalSteps localSteps;
    private final SagaDefinition<ReviseOrderSagaData> sagaDefinition;
    
    /**
     * Creates the ReviseOrderSaga with its step definitions.
     */
    public ReviseOrderSaga() {
        this(null);
    }

    public ReviseOrderSaga(ReviseOrderSagaLocalSteps localSteps) {
        this.localSteps = localSteps;
        this.sagaDefinition = step()
            .invokeLocal(this::beginRevise)
            .withCompensation(this::undoRevise)
        .step()
            .invokeParticipant(this::beginReviseTicket)
            .withCompensation(this::undoReviseTicket)
        .step()
            .invokeParticipant(this::reviseCreditCardAuthorization)
            .onReply(AuthorizationRevised.class, this::handleReviseAuthorizationReply)
        .step()
            .invokeParticipant(this::confirmReviseTicket)
        .step()
            .invokeParticipant(this::confirmReviseStep)
        .build();
    }
    
    @Override
    public SagaDefinition<ReviseOrderSagaData> getSagaDefinition() {
        return sagaDefinition;
    }
    
    // Step 1: Begin revise (local)
    
    /**
     * Begins order revision by transitioning to REVISION_PENDING state.
     * This is a local step that sends a command to Order Service.
     * 
     * Implements semantic lock to prevent concurrent modifications.
     * 
     * @param data the saga data
     */
    private void beginRevise(ReviseOrderSagaData data) {
        logger.info("ReviseOrderSaga: Step 1 - beginRevise for orderId={}", data.getOrderId());
        requireLocalSteps().beginReviseOrder(data.getOrderId());
    }
    
    /**
     * Compensation for beginRevise: Restores order to APPROVED state.
     * Sends command to Order Service to undo the revision.
     * 
     * @param data the saga data
     * @return command to send to Order Service
     */
    private void undoRevise(ReviseOrderSagaData data) {
        logger.warn("ReviseOrderSaga: Compensation - undoRevise for orderId={}", data.getOrderId());
        requireLocalSteps().undoReviseOrder(data.getOrderId());
    }

    private ReviseOrderSagaLocalSteps requireLocalSteps() {
        if (localSteps == null) {
            throw new IllegalStateException("ReviseOrderSagaLocalSteps is required to execute ReviseOrderSaga");
        }
        return localSteps;
    }
    
    // Step 2: Begin revise ticket
    
    /**
     * Sends command to Kitchen Service to begin ticket revision.
     * Updates the ticket with revised line items.
     * 
     * @param data the saga data
     * @return command to send to Kitchen Service
     */
    private CommandWithDestination beginReviseTicket(ReviseOrderSagaData data) {
        logger.info("ReviseOrderSaga: Step 2 - beginReviseTicket for ticketId={}", data.getTicketId());
        
        return send(new BeginReviseTicketCommand(data.getTicketId(), toTicketLineItemDtos(data.getRevisedLineItems())))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    /**
     * Compensation for beginReviseTicket: Undoes ticket revision.
     * Sends command to Kitchen Service to restore the ticket.
     * 
     * @param data the saga data
     * @return command to send to Kitchen Service
     */
    private CommandWithDestination undoReviseTicket(ReviseOrderSagaData data) {
        logger.warn("ReviseOrderSaga: Compensation - undoReviseTicket for ticketId={}", data.getTicketId());
        
        return send(new UndoReviseTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    // Step 3: Revise credit card authorization (PIVOT POINT)
    
    /**
     * Sends command to Accounting Service to revise payment authorization.
     * Adjusts the authorization to the new order total.
     * 
     * THIS IS THE PIVOT POINT:
     * - First non-compensatable step
     * - Once authorization is revised, saga must complete forward
     * - All subsequent steps are retriable
     * 
     * @param data the saga data
     * @return command to send to Accounting Service
     */
    private CommandWithDestination reviseCreditCardAuthorization(ReviseOrderSagaData data) {
        logger.info("ReviseOrderSaga: Step 3 (PIVOT) - reviseCreditCardAuthorization for authorizationId={}, newAmount={}",
            data.getAuthorizationId(), data.getRevisedTotal());
        
        return send(new ReviseAuthorizationCommand(
                data.getConsumerId(),
                data.getAuthorizationId(),
                data.getRevisedTotal().getAmount(),
                data.getPaymentRevisionRequestId()
            ))
            .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private void handleReviseAuthorizationReply(ReviseOrderSagaData data, AuthorizationRevised reply) {
        logger.info("ReviseOrderSaga: Received AuthorizationRevised with authorizationId={}",
            reply.getAuthorizationId());
        data.setRevisedAuthorizationId(reply.getAuthorizationId());
    }
    
    // Step 4: Confirm revise ticket (retriable)
    
    /**
     * Sends command to Kitchen Service to confirm ticket revision.
     * 
     * This step is RETRIABLE (occurs after pivot point).
     * If it fails, the saga will retry until success.
     * 
     * @param data the saga data
     * @return command to send to Kitchen Service
     */
    private CommandWithDestination confirmReviseTicket(ReviseOrderSagaData data) {
        logger.info("ReviseOrderSaga: Step 4 (retriable) - confirmReviseTicket for ticketId={}",
            data.getTicketId());
        
        return send(new ConfirmReviseTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }
    
    // Step 5: Confirm revise (local, retriable)
    
    /**
     * Confirms order revision (transitions to APPROVED state and updates order details).
     * Sends command to Order Service to confirm the revision.
     * 
     * This step is RETRIABLE (occurs after pivot point).
     * If it fails, the saga will retry until success.
     * 
     * @param data the saga data
     * @return command to send to Order Service
     */
    private CommandWithDestination confirmReviseStep(ReviseOrderSagaData data) {
        logger.info("ReviseOrderSaga: Step 5 (retriable) - confirmRevise for orderId={}",
            data.getOrderId());
        
        return send(new ReviseOrderSagaLocalSteps.ConfirmReviseCommand(
                data.getOrderId(), 
                data.getRevisedLineItems(),
                data.getCurrentAuthorizationId()
            ))
            .to(ChannelNames.REVISE_ORDER_SAGA_COMMAND_CHANNEL)
            .build();
    }

    private List<CreateTicketCommand.TicketLineItemDTO> toTicketLineItemDtos(List<OrderLineItem> revisedLineItems) {
        return revisedLineItems.stream()
            .map(item -> new CreateTicketCommand.TicketLineItemDTO(
                item.getMenuItemId(),
                item.getName(),
                item.getQuantity()
            ))
            .toList();
    }
}
