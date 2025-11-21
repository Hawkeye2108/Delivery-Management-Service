package com.delivery_management_service.controllers;


import com.delivery_management_service.DTO.ApiResponse;
import com.delivery_management_service.services.DeliveryManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Driver operations
 * Base URL: /api/drivers
 *
 * Handles driver-side order operations:
 * - Accept order (THIS IS THE SMS URL ENDPOINT!)
 * - Mark order as picked up
 * - Mark order as delivered
 */
@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Slf4j
public class DriverController {

    private final DeliveryManagementService deliveryManagementService;

    /**
     * ⭐ THIS IS THE ENDPOINT THAT DRIVERS CLICK FROM SMS! ⭐
     *
     * Driver accepts an order by clicking URL in SMS
     *
     * URL FORMAT: http://localhost:8080/api/drivers/{driverId}/accept-order/{orderId}
     * EXAMPLE: http://localhost:8080/api/drivers/5/accept-order/1
     *
     * HTTP Method: POST (but can also handle GET for easy SMS clicking)
     *
     * This endpoint:
     * 1. Receives driver ID and order ID from URL
     * 2. Calls deliveryManagementService.driverAcceptOrder()
     * 3. Returns success/error response
     *
     * Updates:
     * - Order assigned_driver_id
     * - Order status to ASSIGNED
     * - Driver status to BUSY
     * - Notification tracker (marks order as accepted)
     *
     * Race Condition Handling:
     * - First driver to call this endpoint wins
     * - Other drivers get "Order already assigned" error
     *
     * @param driverId Driver ID from URL path
     * @param orderId Order ID from URL path
     * @return Success message or error
     */
    @PostMapping("/{driverId}/accept-order/{orderId}")
    public ResponseEntity<ApiResponse<String>> acceptOrder(
            @PathVariable Long driverId,
            @PathVariable Long orderId) {

        log.info("=== SMS ACCEPTANCE ENDPOINT CALLED ===");
        log.info("POST /api/drivers/{}/accept-order/{}", driverId, orderId);
        log.info("Driver {} attempting to accept order {} via SMS link", driverId, orderId);

        try {
            // Call the service method that handles acceptance logic
            deliveryManagementService.driverAcceptOrder(orderId, driverId);

            log.info("✓ Success! Driver {} accepted order {}", driverId, orderId);

            return ResponseEntity.ok(
                    ApiResponse.success(
                            "🎉 Congratulations! You've been assigned this order. " +
                                    "Please proceed to the restaurant for pickup.",
                            null
                    )
            );

        } catch (RuntimeException e) {
            log.warn("✗ Failed! Driver {} could not accept order {}: {}",
                    driverId, orderId, e.getMessage());

            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * OPTIONAL: Allow GET method for easier SMS clicking
     * Some phones/browsers work better with GET links
     *
     * This allows the same URL to work with both GET and POST
     */
    @GetMapping("/{driverId}/accept-order/{orderId}")
    public ResponseEntity<String> acceptOrderViaGet(
            @PathVariable Long driverId,
            @PathVariable Long orderId) {

        log.info("=== SMS ACCEPTANCE VIA GET (Redirect to POST) ===");
        log.info("GET /api/drivers/{}/accept-order/{}", driverId, orderId);

        try {
            deliveryManagementService.driverAcceptOrder(orderId, driverId);

            // Return HTML response for better user experience
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html")
                    .body(
                            "<!DOCTYPE html>" +
                                    "<html>" +
                                    "<head>" +
                                    "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                                    "  <title>Order Accepted</title>" +
                                    "  <style>" +
                                    "    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background: #f0f0f0; }" +
                                    "    .success { background: white; border-radius: 10px; padding: 30px; max-width: 400px; margin: 0 auto; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
                                    "    .icon { font-size: 60px; margin-bottom: 20px; }" +
                                    "    h1 { color: #28a745; margin: 0; font-size: 24px; }" +
                                    "    p { color: #666; line-height: 1.6; }" +
                                    "  </style>" +
                                    "</head>" +
                                    "<body>" +
                                    "  <div class='success'>" +
                                    "    <div class='icon'>🎉</div>" +
                                    "    <h1>Order Accepted!</h1>" +
                                    "    <p><strong>Order #" + orderId + "</strong></p>" +
                                    "    <p>You've been assigned this delivery.</p>" +
                                    "    <p>Please proceed to the restaurant for pickup.</p>" +
                                    "  </div>" +
                                    "</body>" +
                                    "</html>"
                    );

        } catch (RuntimeException e) {
            log.warn("Driver {} could not accept order {}: {}", driverId, orderId, e.getMessage());

            // Return error HTML
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html")
                    .body(
                            "<!DOCTYPE html>" +
                                    "<html>" +
                                    "<head>" +
                                    "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                                    "  <title>Cannot Accept Order</title>" +
                                    "  <style>" +
                                    "    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background: #f0f0f0; }" +
                                    "    .error { background: white; border-radius: 10px; padding: 30px; max-width: 400px; margin: 0 auto; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
                                    "    .icon { font-size: 60px; margin-bottom: 20px; }" +
                                    "    h1 { color: #dc3545; margin: 0; font-size: 24px; }" +
                                    "    p { color: #666; line-height: 1.6; }" +
                                    "  </style>" +
                                    "</head>" +
                                    "<body>" +
                                    "  <div class='error'>" +
                                    "    <div class='icon'>❌</div>" +
                                    "    <h1>Cannot Accept Order</h1>" +
                                    "    <p>" + e.getMessage() + "</p>" +
                                    "  </div>" +
                                    "</body>" +
                                    "</html>"
                    );
        }
    }

    /**
     * Driver marks order as picked up from restaurant
     *
     * POST /api/drivers/{driverId}/pickup-order/{orderId}
     *
     * Updates order status to PICKED_UP
     *
     * @param driverId Driver ID
     * @param orderId Order ID
     * @return Success message
     */
    @PostMapping("/{driverId}/pickup-order/{orderId}")
    public ResponseEntity<ApiResponse<String>> markOrderPickedUp(
            @PathVariable Long driverId,
            @PathVariable Long orderId) {
        log.info("POST /api/drivers/{}/pickup-order/{} - Marking order as picked up", driverId, orderId);

        deliveryManagementService.markOrderPickedUp(orderId, driverId);

        log.info("Order {} marked as picked up by driver {}", orderId, driverId);
        return ResponseEntity.ok(ApiResponse.success("Order marked as picked up", null));
    }

    /**
     * Driver marks order as delivered to customer
     *
     * POST /api/drivers/{driverId}/deliver-order/{orderId}
     *
     * Updates:
     * - Order status to DELIVERED
     * - Order delivered_at timestamp
     * - Driver status back to AVAILABLE
     *
     * @param driverId Driver ID
     * @param orderId Order ID
     * @return Success message
     */
    @PostMapping("/{driverId}/deliver-order/{orderId}")
    public ResponseEntity<ApiResponse<String>> markOrderDelivered(
            @PathVariable Long driverId,
            @PathVariable Long orderId) {
        log.info("POST /api/drivers/{}/deliver-order/{} - Marking order as delivered", driverId, orderId);

        deliveryManagementService.markOrderDelivered(orderId, driverId);

        log.info("Order {} delivered successfully by driver {}. Driver now available", orderId, driverId);
        return ResponseEntity.ok(ApiResponse.success("Order delivered successfully", null));
    }
}

