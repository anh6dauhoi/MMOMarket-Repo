package com.mmo.mq;

import com.mmo.mq.dto.SellerRegistrationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class SellerRegistrationPublisher {
    private static final Logger log = LoggerFactory.getLogger(SellerRegistrationPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public SellerRegistrationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(SellerRegistrationMessage msg) {
        if (msg == null || msg.userId() == null) return;
        log.info("Publishing seller-registration message userId={} shopName={} dedupeKey={}", msg.userId(), msg.shopName(), msg.dedupeKey());
        rabbitTemplate.convertAndSend(RabbitConfig.SELLER_REGISTRATION_EXCHANGE, RabbitConfig.SELLER_REGISTRATION_ROUTING_KEY, msg);
    }
}

