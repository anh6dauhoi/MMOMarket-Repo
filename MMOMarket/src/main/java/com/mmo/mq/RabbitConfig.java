package com.mmo.mq;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableRabbit
@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "withdrawal.exchange";
    public static final String ROUTING_KEY = "withdrawal.requested";
    public static final String QUEUE = "withdrawal.requests";

    // New: dedicated exchange/queue for seller registration to avoid clashing with withdrawal flows
    public static final String SELLER_REGISTRATION_EXCHANGE = "seller.registration.exchange";
    public static final String SELLER_REGISTRATION_ROUTING_KEY = "seller.registration.requested";
    public static final String SELLER_REGISTRATION_QUEUE = "seller.registration.requests";

    // New: dedicated exchange/queue for buy-points flow
    public static final String BUY_POINTS_EXCHANGE = "buy.points.exchange";
    public static final String BUY_POINTS_ROUTING_KEY = "buy.points.requested";
    public static final String BUY_POINTS_QUEUE = "buy.points.requests";

    // New: dedicated exchange/queue for buy-account (orders) flow
    public static final String BUY_ACCOUNT_EXCHANGE = "buy.account.exchange";
    public static final String BUY_ACCOUNT_ROUTING_KEY = "buy.account.requested";
    public static final String BUY_ACCOUNT_QUEUE = "buy.account.requests";

    @Bean
    public DirectExchange withdrawalExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue withdrawalQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding withdrawalBinding(Queue withdrawalQueue, DirectExchange withdrawalExchange) {
        return BindingBuilder.bind(withdrawalQueue).to(withdrawalExchange).with(ROUTING_KEY);
    }

    // New beans for seller registration messaging
    @Bean
    public DirectExchange sellerRegistrationExchange() {
        return new DirectExchange(SELLER_REGISTRATION_EXCHANGE, true, false);
    }

    @Bean
    public Queue sellerRegistrationQueue() {
        return QueueBuilder.durable(SELLER_REGISTRATION_QUEUE).build();
    }

    @Bean
    public Binding sellerRegistrationBinding(Queue sellerRegistrationQueue, DirectExchange sellerRegistrationExchange) {
        return BindingBuilder.bind(sellerRegistrationQueue).to(sellerRegistrationExchange).with(SELLER_REGISTRATION_ROUTING_KEY);
    }

    // New beans for buy points messaging
    @Bean
    public DirectExchange buyPointsExchange() {
        return new DirectExchange(BUY_POINTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue buyPointsQueue() {
        return QueueBuilder.durable(BUY_POINTS_QUEUE).build();
    }

    @Bean
    public Binding buyPointsBinding(Queue buyPointsQueue, DirectExchange buyPointsExchange) {
        return BindingBuilder.bind(buyPointsQueue).to(buyPointsExchange).with(BUY_POINTS_ROUTING_KEY);
    }

    // New beans for buy account messaging
    @Bean
    public DirectExchange buyAccountExchange() {
        return new DirectExchange(BUY_ACCOUNT_EXCHANGE, true, false);
    }

    @Bean
    public Queue buyAccountQueue() {
        return QueueBuilder.durable(BUY_ACCOUNT_QUEUE).build();
    }

    @Bean
    public Binding buyAccountBinding(Queue buyAccountQueue, DirectExchange buyAccountExchange) {
        return BindingBuilder.bind(buyAccountQueue).to(buyAccountExchange).with(BUY_ACCOUNT_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                               Jackson2JsonMessageConverter converter,
                                                                               RabbitAdmin rabbitAdmin) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(true);
        factory.setMissingQueuesFatal(false);
        return factory;
    }

    // Dedicated factory for buy-account listener with retry/backoff and tuned throughput
    @Bean
    public SimpleRabbitListenerContainerFactory buyAccountListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                                   Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(true); // preserve message on failures after local retries
        factory.setMissingQueuesFatal(false);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        Advice retry = RetryInterceptorBuilder.stateless()
                .maxAttempts(5)
                .backOffOptions(1000, 2.0, 10000)
                .build();
        factory.setAdviceChain(retry);
        return factory;
    }
}
