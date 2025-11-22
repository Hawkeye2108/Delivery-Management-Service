package com.delivery_management_service.services;

import com.delivery_management_service.models.Order;
import com.delivery_management_service.repository.OrderRepository;
import com.delivery_management_service.services.DeliveryManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantOrderService {

    private final OrderRepository orderRepository;
    private final DeliveryManagementService deliveryManagementService;

    @Transactional
    public void acceptOrder(Long orderId) {
        log.info("Restaurant accepting order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("Order cannot be accepted. Current status: " + order.getStatus());
        }

        // Update order status
        order.setStatus("ACCEPTED");
        order.setAcceptedAt(LocalDateTime.now());
        orderRepository.save(order);
//        orderRepository.flush(); // Force immediate DB write

        log.info("Order {} accepted. Initiating driver assignment...", orderId);

        // IMPORTANT: This happens AFTER the transaction commits
        // The @Async method will see the committed "ACCEPTED" status
        // Trigger delivery management
        deliveryManagementService.assignDriverToOrder(orderId, order.getRestaurantId());
        // Register callback to run AFTER transaction commits
//        TransactionSynchronizationManager.registerSynchronization(
//                new TransactionSynchronization() {
//                    @Override
//                    public void afterCommit() {
//                        // This runs AFTER transaction commits
//                        deliveryManagementService.assignDriverToOrder(
//                                orderId,
//                                order.getRestaurantId()
//                        );
//                    }
//                }
//        );
    }

    @Transactional
    public void rejectOrder(Long orderId, String reason) {
        log.info("Restaurant rejecting order: {} with reason: {}", orderId, reason);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("Order cannot be rejected. Current status: " + order.getStatus());
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);

        log.info("Order {} rejected", orderId);
    }
}