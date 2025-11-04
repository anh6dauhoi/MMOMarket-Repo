package com.mmo.mq;

import com.mmo.mq.dto.BuyAccountMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class BuyAccountPublisher {
    private static final Logger log = LoggerFactory.getLogger(BuyAccountPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public BuyAccountPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(Long orderId) {
        if (orderId == null) return;
        BuyAccountMessage msg = new BuyAccountMessage(orderId);
        log.info("Publishing buy-account message orderId={}", orderId);
        rabbitTemplate.convertAndSend(RabbitConfig.BUY_ACCOUNT_EXCHANGE, RabbitConfig.BUY_ACCOUNT_ROUTING_KEY, msg);
    }
}

