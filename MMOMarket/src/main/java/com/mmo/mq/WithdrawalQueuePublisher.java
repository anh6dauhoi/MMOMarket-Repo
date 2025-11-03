package com.mmo.mq;

import com.mmo.entity.Withdrawal;
import com.mmo.mq.dto.WithdrawalCreateMessage;
import com.mmo.mq.dto.WithdrawalMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class WithdrawalQueuePublisher {
    private static final Logger log = LoggerFactory.getLogger(WithdrawalQueuePublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public WithdrawalQueuePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // Legacy: publish a payout/processing message (no longer used for admin approval)
    public void publish(Withdrawal w) {
        if (w == null || w.getId() == null) return;
        Long sellerId = (w.getSeller() != null ? w.getSeller().getId() : null);
        Long amount = w.getAmount();
        WithdrawalMessage msg = new WithdrawalMessage(w.getId(), sellerId, amount);
        log.info("Publishing withdrawal message id={} sellerId={} amount={}", w.getId(), sellerId, amount);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, msg);
    }

    // New: publish a creation message when seller submits a request
    public void publishCreate(WithdrawalCreateMessage msg) {
        if (msg == null || msg.sellerId() == null || msg.amount() == null || msg.amount() <= 0) return;
        log.info("Publishing withdrawal-create message sellerId={} bankInfoId={} amount={} dedupeKey={}",
                msg.sellerId(), msg.bankInfoId(), msg.amount(), msg.dedupeKey());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, msg);
    }
}
