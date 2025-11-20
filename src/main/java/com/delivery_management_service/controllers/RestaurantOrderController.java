package com.delivery_management_service.controllers;

import com.delivery_management_service.DTO.ApiResponse;
import com.delivery_management_service.services.RestaurantOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restaurant/orders")
@RequiredArgsConstructor
@Slf4j
public class RestaurantOrderController {

    private final RestaurantOrderService restaurantOrderService;

    /**
     * Restaurant accepts an order
     *
     * POST /api/restaurant/orders/{orderId}/accept
     *
     * This triggers the automatic driver assignment process:
     * 1. Order status changes to ACCEPTED
     * 2. System fetches restaurant location
     * 3. Finds nearest 20 available drivers using PostGIS
     * 4. Notifies drivers in batches
     * 5. Assigns first accepting driver
     *
     * @param orderId Order ID to accept
     * @return Success message
     */
    @PostMapping("/{orderId}/accept")
    public ResponseEntity<ApiResponse<String>> acceptOrder(@PathVariable Long orderId) {
        log.info("POST /api/restaurant/orders/{}/accept - Restaurant accepting order", orderId);

        restaurantOrderService.acceptOrder(orderId);

        log.info("Order {} accepted. Driver assignment initiated", orderId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Order accepted successfully. Driver assignment in progress.",
                        null
                )
        );
    }

    /**
     * Restaurant rejects an order
     *
     * POST /api/restaurant/orders/{orderId}/reject?reason=...
     *
     * @param orderId Order ID to reject
     * @param reason Optional rejection reason
     * @return Success message
     */
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<ApiResponse<String>> rejectOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false) String reason) {
        log.info("POST /api/restaurant/orders/{}/reject - Restaurant rejecting order. Reason: {}",
                orderId, reason);

        restaurantOrderService.rejectOrder(orderId, reason);

        log.info("Order {} rejected", orderId);
        return ResponseEntity.ok(ApiResponse.success("Order rejected successfully", null));
    }
}
