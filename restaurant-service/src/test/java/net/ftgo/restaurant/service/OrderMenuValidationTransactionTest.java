package net.ftgo.restaurant.service;

import net.ftgo.common.orderflow.commands.ValidateOrderMenuCommand;
import net.ftgo.common.orderflow.replies.OrderMenuValidationRejected;
import net.ftgo.restaurant.repository.MenuItemRepository;
import net.ftgo.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderMenuValidationTransactionTest {

    @Test
    void typedBusinessRejectionDoesNotPoisonSurroundingMessageTransaction() {
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
        when(restaurantRepository.findById(202L)).thenReturn(Optional.empty());

        PlatformTransactionManager transactionManager = transactionManager();
        OrderMenuValidationService validationService = transactionalProxy(
            new OrderMenuValidationService(restaurantRepository, menuItemRepository),
            transactionManager
        );
        TransactionTemplate messageTransaction = new TransactionTemplate(transactionManager);

        assertThatCode(() -> messageTransaction.executeWithoutResult(status -> {
            try {
                validationService.validate(new ValidateOrderMenuCommand(101L, 202L, 0L, List.of()));
            } catch (OrderMenuValidationException rejection) {
                assertThat(rejection.getReasonCode())
                    .isEqualTo(OrderMenuValidationRejected.RESTAURANT_NOT_FOUND);
            }
        })).doesNotThrowAnyException();
    }

    private PlatformTransactionManager transactionManager() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:restaurant-menu-validation;DB_CLOSE_DELAY=-1");
        return new DataSourceTransactionManager(dataSource);
    }

    private OrderMenuValidationService transactionalProxy(OrderMenuValidationService target,
                                                           PlatformTransactionManager transactionManager) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(new TransactionInterceptor(
            transactionManager,
            new AnnotationTransactionAttributeSource()
        ));
        return (OrderMenuValidationService) proxyFactory.getProxy();
    }
}
