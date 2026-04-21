# Product Overview

FTGO (Food To Go) is a production-grade microservices platform for online food ordering and delivery, based on *Microservices Patterns* by Chris Richardson.

## Core Functionality

- **Consumer Management**: User accounts with credit limit verification
- **Restaurant Management**: Restaurant profiles, menus, and availability
- **Order Processing**: Complete order lifecycle with saga-based orchestration
- **Kitchen Operations**: Ticket management and order preparation tracking
- **Payment Processing**: Credit card authorization and accounting
- **Delivery Management**: Courier assignment and delivery tracking
- **Order History**: CQRS-based queryable view of all order data

## Key Business Characteristics

- **Transaction Model**: Saga-based (orchestration + choreography) for distributed transactions
- **Consistency**: Eventual consistency (ACD) across services, ACID within single service
- **Architecture**: Domain-Driven Design with bounded contexts mapped to microservices
- **Deployability**: Each service independently deployable via CI/CD pipeline

## Primary User Roles

- **ROLE_CONSUMER**: Place, view, cancel, and revise orders
- **ROLE_RESTAURANT**: Manage menus, view and accept kitchen tickets
- **ROLE_COURIER**: View assigned deliveries, update delivery status
- **ROLE_ADMIN**: Full system access
