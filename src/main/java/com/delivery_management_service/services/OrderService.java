package com.delivery_management_service.services;

import com.delivery_management_service.DTO.OrderItemRequestDTO;
import com.delivery_management_service.DTO.OrderItemResponseDTO;
import com.delivery_management_service.DTO.OrderRequestDTO;
import com.delivery_management_service.DTO.OrderResponseDTO;
import com.delivery_management_service.models.FoodItem;
import com.delivery_management_service.models.Order;
import com.delivery_management_service.models.OrderItem;
import com.delivery_management_service.repository.FoodItemRepository;
import com.delivery_management_service.repository.OrderRepository;
import com.delivery_management_service.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final FoodItemRepository foodItemRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public OrderResponseDTO createOrder(OrderRequestDTO request) {
        log.info("Creating new order for restaurant: {}", request.getRestaurantId());

        // Validate restaurant
        restaurantRepository.findByIdAndIsActiveTrue(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found or inactive"));

        // Fetch all food items
        List<Long> foodItemIds = request.getItems().stream()
                .map(OrderItemRequestDTO::getFoodItemId)
                .collect(Collectors.toList());

        Map<Long, FoodItem> foodItemMap = foodItemRepository.findAllById(foodItemIds)
                .stream()
                .collect(Collectors.toMap(FoodItem::getId, item -> item));

        // Create order
        Order order = new Order();
        order.setRestaurantId(request.getRestaurantId());
        order.setCustomerName(request.getCustomerName());
        order.setCustomerPhone(request.getCustomerPhone());
        order.setDeliveryAddress(request.getDeliveryAddress());
        order.setStatus("PENDING");

        // Create order items and calculate total
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequestDTO itemRequest : request.getItems()) {
            FoodItem foodItem = foodItemMap.get(itemRequest.getFoodItemId());
            if (foodItem == null) {
                throw new RuntimeException("Food item not found: " + itemRequest.getFoodItemId());
            }

            if (!foodItem.getIsAvailable()) {
                throw new RuntimeException("Food item not available: " + foodItem.getName());
            }

            if (!foodItem.getRestaurantId().equals(request.getRestaurantId())) {
                throw new RuntimeException("Food item does not belong to this restaurant");
            }

            BigDecimal itemTotal = foodItem.getPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            OrderItem orderItem = new OrderItem();
            orderItem.setFoodItemId(foodItem.getId());
            orderItem.setItemName(foodItem.getName());
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setUnitPrice(foodItem.getPrice());
            orderItem.setTotalPrice(itemTotal);
            orderItem.setSpecialInstructions(itemRequest.getSpecialInstructions());

            orderItems.add(orderItem);
            totalAmount = totalAmount.add(itemTotal);
        }

        order.setTotalAmount(totalAmount);

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Save order items
        for (OrderItem item : orderItems) {
            item.setOrderId(savedOrder.getId());
        }
        savedOrder.setOrderItems(orderItems);

        log.info("Order created successfully with id: {}", savedOrder.getId());
        return mapToOrderResponseDTO(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderById(Long orderId) {
        log.info("Fetching order: {}", orderId);
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        return mapToOrderResponseDTO(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getOrdersByRestaurant(Long restaurantId) {
        log.info("Fetching orders for restaurant: {}", restaurantId);
        return orderRepository.findByRestaurantId(restaurantId)
                .stream()
                .map(this::mapToOrderResponseDTO)
                .collect(Collectors.toList());
    }

    private OrderResponseDTO mapToOrderResponseDTO(Order order) {
        List<OrderItemResponseDTO> items = order.getOrderItems().stream()
                .map(item -> OrderItemResponseDTO.builder()
                        .id(item.getId())
                        .foodItemId(item.getFoodItemId())
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .specialInstructions(item.getSpecialInstructions())
                        .build())
                .collect(Collectors.toList());

        return OrderResponseDTO.builder()
                .id(order.getId())
                .restaurantId(order.getRestaurantId())
                .restaurantName(order.getRestaurant() != null ? order.getRestaurant().getName() : null)
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .deliveryAddress(order.getDeliveryAddress())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .acceptedAt(order.getAcceptedAt())
                .deliveredAt(order.getDeliveredAt())
                .assignedDriverId(order.getAssignedDriverId())
                .assignedDriverName(order.getAssignedDriver() != null ? order.getAssignedDriver().getName() : null)
                .items(items)
                .build();
    }
}

