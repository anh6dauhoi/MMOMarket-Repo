package com.mmo.mq;

import com.mmo.mq.dto.BuyPointsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class BuyPointsPublisher {
    private static final Logger log = LoggerFactory.getLogger(BuyPointsPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public BuyPointsPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(BuyPointsMessage msg) {
        if (msg == null || msg.userId() == null || msg.pointsToBuy() == null || msg.pointsToBuy() <= 0) return;
        log.info("Publishing buy-points message userId={} pointsToBuy={} cost={} dedupeKey={}",
                msg.userId(), msg.pointsToBuy(), msg.costCoins(), msg.dedupeKey());
        rabbitTemplate.convertAndSend(RabbitConfig.BUY_POINTS_EXCHANGE, RabbitConfig.BUY_POINTS_ROUTING_KEY, msg);
    }
}

