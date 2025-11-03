package com.mmo.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
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
}
