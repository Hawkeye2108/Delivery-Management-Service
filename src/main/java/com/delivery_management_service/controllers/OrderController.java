package com.delivery_management_service.controllers;

import com.delivery_management_service.DTO.ApiResponse;
import com.delivery_management_service.DTO.OrderRequestDTO;
import com.delivery_management_service.DTO.OrderResponseDTO;
import com.delivery_management_service.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    /**
     * Create a new order
     *
     * POST /api/orders
     *
     * Request Body: OrderRequestDTO with customer details and items
     * Response: Created order with calculated total amount
     *
     * Example Request:
     * {
     *   "restaurantId": 1,
     *   "customerName": "John Doe",
     *   "customerPhone": "+1234567890",
     *   "deliveryAddress": "123 Main St",
     *   "items": [
     *     {
     *       "foodItemId": 1,
     *       "quantity": 2,
     *       "specialInstructions": "Extra cheese"
     *     }
     *   ]
     * }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponseDTO>> createOrder(
            @Valid @RequestBody OrderRequestDTO request) {
        log.info("POST /api/orders - Creating new order for restaurant {}", request.getRestaurantId());
        log.debug("Order details: customer={}, items={}", request.getCustomerName(), request.getItems().size());

        OrderResponseDTO order = orderService.createOrder(request);

        log.info("Order created successfully with ID: {}, Total: {}", order.getId(), order.getTotalAmount());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", order));
    }

    /**
     * Get order by ID
     *
     * GET /api/orders/{id}
     *
     * @param id Order ID
     * @return Complete order details with items
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> getOrderById(@PathVariable Long id) {
        log.info("GET /api/orders/{} - Fetching order details", id);
        OrderResponseDTO order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    /**
     * Get all orders for a restaurant
     *
     * GET /api/orders/restaurant/{restaurantId}
     *
     * @param restaurantId Restaurant ID
     * @return List of orders for the restaurant
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<List<OrderResponseDTO>>> getOrdersByRestaurant(
            @PathVariable Long restaurantId) {
        log.info("GET /api/orders/restaurant/{} - Fetching restaurant orders", restaurantId);
        List<OrderResponseDTO> orders = orderService.getOrdersByRestaurant(restaurantId);
        log.info("Found {} orders for restaurant {}", orders.size(), restaurantId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
}
