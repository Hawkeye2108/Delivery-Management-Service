//package com.delivery_management_service.services;
//
//import com.delivery_management_service.DTO.DriverNotificationDTO;
//import com.delivery_management_service.models.Driver;
//import com.delivery_management_service.models.Order;
//import com.delivery_management_service.models.Restaurant;
//import com.delivery_management_service.repository.DriverRepository;
//import com.delivery_management_service.repository.OrderRepository;
//import com.delivery_management_service.repository.RestaurantRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.locationtech.jts.geom.Point;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//
//
//import java.util.HashSet;
//import java.util.Set;
//import java.util.stream.Collectors;
//
///**
// * DeliveryManagementService
// *
// * Core service responsible for:
// * 1. Finding nearest available drivers using PostGIS spatial queries
// * 2. Notifying drivers in batches of 20
// * 3. Assigning drivers to orders
// * 4. Managing driver status (AVAILABLE/BUSY)
// * 5. Handling order lifecycle (ASSIGNED -> PICKED_UP -> DELIVERED)
// */
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class DeliveryManagementService {
//
//    private final DriverRepository driverRepository;
//    private final RestaurantRepository restaurantRepository;
//    private final OrderRepository orderRepository;
//    private final TwilioSmsService twilioSmsService;
//    private final DriverNotificationTracker notificationTracker;
//
//    @Value("${app.delivery.driver-batch-size:20}")
//    private int driverBatchSize;
//
//    @Value("${app.delivery.max-driver-distance-km:10}")
//    private double maxDriverDistanceKm;
//
//    @Value("${app.delivery.base-url}")
//    private String baseUrl;
//
//    @Value("${app.delivery.driver-response-timeout-seconds:60}")
//    private int driverResponseTimeoutSeconds;
//
//    /**
//     * MAIN METHOD: Assigns a driver to an order
//     *
//     * Process:
//     * 1. Fetch restaurant location (lat/lng)
//     * 2. Use PostGIS to find nearest 20 available drivers
//     * 3. Notify drivers (simulated)
//     * 4. If none accepts, fetch next 20 drivers
//     * 5. Once accepted, update order and driver status
//     *
//     * Runs asynchronously to avoid blocking restaurant acceptance response
//     *
//     * @param orderId Order ID to assign driver to
//     * @param restaurantId Restaurant ID (to get location)
//     */
//    @Async
//    @Transactional
//    public void assignDriverToOrder(Long orderId, Long restaurantId) {
//        log.info("========================================");
//        log.info("Starting driver assignment for order: {} from restaurant: {}", orderId, restaurantId);
//        log.info("========================================");
//
//        try {
//            // Step 1: Fetch restaurant with coordinates
//            Restaurant restaurant = restaurantRepository.findById(restaurantId)
//                    .orElseThrow(() -> new RuntimeException("Restaurant not found: " + restaurantId));
//
//            Point restaurantLocation = restaurant.getLocation();
//            if (restaurantLocation == null) {
//                log.error("Restaurant {} has no location coordinates", restaurantId);
//                throw new RuntimeException("Restaurant location not available");
//            }
//
//            double longitude = restaurantLocation.getX();
//            double latitude = restaurantLocation.getY();
//
//            log.info("Restaurant location: lat={}, lng={}", latitude, longitude);
//
//            // Step 2: Fetch order details
//            Order order = orderRepository.findById(orderId)
//                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
//
//            // Step 3: Find and notify drivers in batches
//            boolean driverAssigned = findAndNotifyDrivers(order, restaurant, longitude, latitude);
//
//            if (!driverAssigned) {
//                log.warn("No driver accepted order: {}. Marking as UNASSIGNED", orderId);
//                order.setStatus("UNASSIGNED");
//                orderRepository.save(order);
//            }
//
//        } catch (Exception e) {
//            log.error("Error assigning driver to order {}: {}", orderId, e.getMessage(), e);
//            // Update order status to indicate assignment failure
//            orderRepository.findById(orderId).ifPresent(order -> {
//                order.setStatus("ASSIGNMENT_FAILED");
//                orderRepository.save(order);
//            });
//        }
//    }
//
//    /**
//     * Finds nearest drivers and notifies them in batches until one accepts
//     *
//     * PRODUCTION IMPLEMENTATION:
//     * - Uses PostGIS ST_Distance to find drivers ordered by proximity
//     * - Notifies in batches of 20 (configurable)
//     * - Waits 60 seconds per batch for driver acceptance (configurable)
//     * - If no driver accepts, moves to next batch
//     * - Tracks notified drivers to avoid duplicates
//     *
//     * @param order Order to assign
//     * @param restaurant Restaurant details
//     * @param longitude Restaurant longitude
//     * @param latitude Restaurant latitude
//     * @return true if driver was assigned, false otherwise
//     */
//    private boolean findAndNotifyDrivers(Order order, Restaurant restaurant,
//                                         double longitude, double latitude) {
//        int maxAttempts = 10; // Maximum 10 batches (200 drivers)
//        int attempt = 0;
//
//        // Initialize tracking for this order
//        notificationTracker.initializeOrder(order.getId());
//
//        while (attempt < maxAttempts) {
//            attempt++;
//            log.info("========================================");
//            log.info("Driver search attempt {} for order {}", attempt, order.getId());
//            log.info("========================================");
//
//            // Check if order was already accepted (by previous batch)
//            if (notificationTracker.isOrderAccepted(order.getId())) {
//                Long acceptedDriverId = notificationTracker.getAcceptedDriver(order.getId());
//                log.info("Order {} already accepted by driver {}", order.getId(), acceptedDriverId);
//                return true;
//            }
//
//            // CRITICAL: PostGIS query to find nearest available drivers
//            List<Driver> nearestDrivers = driverRepository.findNearestAvailableDrivers(
//                    longitude,
//                    latitude,
//                    driverBatchSize
//            );
//
//            if (nearestDrivers.isEmpty()) {
//                log.warn("No more available drivers found for order: {}", order.getId());
//                notificationTracker.clearOrder(order.getId());
//                return false;
//            }
//
//            // Filter out already notified drivers
//            Set<Long> alreadyNotified = notificationTracker.getNotifiedDrivers(order.getId());
//            List<Driver> driversToNotify = nearestDrivers.stream()
//                    .filter(driver -> !alreadyNotified.contains(driver.getId()))
//                    .collect(Collectors.toList());
//
//            if (driversToNotify.isEmpty()) {
//                log.warn("All nearby drivers have already been notified. No new drivers to notify.");
//                notificationTracker.clearOrder(order.getId());
//                return false;
//            }
//
//            log.info("Found {} available drivers in batch {} ({} new drivers)",
//                    nearestDrivers.size(), attempt, driversToNotify.size());
//
//            // Log driver details
//            for (int i = 0; i < driversToNotify.size(); i++) {
//                Driver driver = driversToNotify.get(i);
//                double distance = calculateDistance(driver.getCurrentLocation(), restaurant.getLocation());
//                log.debug("  {}. Driver {} ({}) - {}km away",
//                        i + 1, driver.getId(), driver.getName(), String.format("%.2f", distance));
//            }
//
//            // Notify drivers with SMS and wait for acceptance
//            boolean driverAccepted = notifyDriversAndWaitForAcceptance(
//                    driversToNotify, order, restaurant
//            );
//
//            if (driverAccepted) {
//                Long acceptedDriverId = notificationTracker.getAcceptedDriver(order.getId());
//                log.info("✓ Driver {} accepted order {} in batch {}", acceptedDriverId, order.getId(), attempt);
//                notificationTracker.clearOrder(order.getId());
//                return true;
//            }
//
//            log.info("No driver accepted in batch {} within timeout period. Trying next batch...", attempt);
//
//            // Small delay before next batch to avoid overwhelming the system
//            try {
//                Thread.sleep(2000); // 2 second delay between batches
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                log.error("Thread interrupted during driver search", e);
//                notificationTracker.clearOrder(order.getId());
//                return false;
//            }
//        }
//
//        log.error("Exhausted all {} attempts. No driver found for order {}", maxAttempts, order.getId());
//        notificationTracker.clearOrder(order.getId());
//        return false;
//    }
//
//    /**
//     * Notifies a batch of drivers via SMS with acceptance URL
//     *
//     * Sends SMS to each driver containing:
//     * - Order details (restaurant, distance, amount)
//     * - Direct acceptance URL
//     *
//     * The URL format: {baseUrl}/api/drivers/{driverId}/accept-order/{orderId}
//     *
//     * When driver clicks the URL, it automatically accepts the order.
//     * First driver to click gets the order.
//     *
//     * @param drivers List of drivers to notify
//     * @param order Order details
//     * @param restaurant Restaurant details
//     * @return Optional containing accepting driver, or empty if none accepted
//     */
//    private Optional<Driver> notifyDriversAndWaitForAcceptance(
//            List<Driver> drivers, Order order, Restaurant restaurant) {
//
//        log.info("========================================");
//        log.info("Sending SMS notifications to {} drivers", drivers.size());
//        log.info("========================================");
//
//        List<DriverNotificationDTO> notifications = new ArrayList<>();
//        int successCount = 0;
//        int failCount = 0;
//
//        for (Driver driver : drivers) {
//            try {
//                // Calculate distance from driver to restaurant
//                double distance = calculateDistance(
//                        driver.getCurrentLocation(),
//                        restaurant.getLocation()
//                );
//
//                // Build acceptance URL
//                String acceptanceUrl = String.format(
//                        "%s/api/drivers/%d/accept-order/%d",
//                        baseUrl,
//                        driver.getId(),
//                        order.getId()
//                );
//
//                // Prepare notification DTO for logging/tracking
//                DriverNotificationDTO notification = DriverNotificationDTO.builder()
//                        .orderId(order.getId())
//                        .driverId(driver.getId())
//                        .restaurantName(restaurant.getName())
//                        .restaurantAddress(restaurant.getAddress())
//                        .deliveryAddress(order.getDeliveryAddress())
//                        .totalAmount(order.getTotalAmount())
//                        .distanceKm(distance)
//                        .estimatedPickupTime("15-20 minutes")
//                        .build();
//
//                notifications.add(notification);
//
//                log.info("Sending SMS to driver {}: {} ({}km away)",
//                        driver.getId(),
//                        driver.getName(),
//                        String.format("%.2f", distance));
//
//                // SEND SMS VIA TWILIO
//                String messageSid = twilioSmsService.sendOrderNotification(
//                        driver.getPhone(),
//                        driver.getId(),
//                        order.getId(),
//                        restaurant.getName(),
//                        distance,
//                        order.getTotalAmount().toString(),
//                        acceptanceUrl
//                );
//
//                if (messageSid != null) {
//                    successCount++;
//                    log.info("  ✓ SMS sent successfully to {} - SID: {}", driver.getPhone(), messageSid);
//                    log.info("  Acceptance URL: {}", acceptanceUrl);
//                } else {
//                    failCount++;
//                    log.error("  ✗ Failed to send SMS to {}", driver.getPhone());
//                }
//
//            } catch (Exception e) {
//                failCount++;
//                log.error("  ✗ Error notifying driver {}: {}", driver.getId(), e.getMessage(), e);
//            }
//        }
//
//        log.info("========================================");
//        log.info("SMS Notification Summary:");
//        log.info("  Total drivers: {}", drivers.size());
//        log.info("  Successful: {}", successCount);
//        log.info("  Failed: {}", failCount);
//        log.info("========================================");
//        log.info("Waiting for driver to accept via URL...");
//
//        // In production, you would:
//        // 1. Wait for driver to click URL (calls driverAcceptOrder)
//        // 2. Use a timeout mechanism (e.g., 60 seconds)
//        // 3. Move to next batch if no acceptance
//        // 4. Use Redis/Database to track which drivers were notified
//
//        // For now, simulate waiting and return empty
//        // The actual acceptance happens when driver clicks the URL
//        // which calls: POST /api/drivers/{driverId}/accept-order/{orderId}
//
//        return Optional.empty(); // Driver will accept via URL callback
//    }
//
//    /**
//     * Finalizes driver assignment to order
//     *
//     * Updates:
//     * 1. order.assigned_driver_id = driverId
//     * 2. order.status = 'ASSIGNED'
//     * 3. driver.status = 'BUSY'
//     *
//     * @param orderId Order ID
//     * @param driverId Driver ID
//     */
//    @Transactional
//    public void assignDriverToOrderFinal(Long orderId, Long driverId) {
//        log.info("Finalizing driver assignment: order={}, driver={}", orderId, driverId);
//
//        Order order = orderRepository.findById(orderId)
//                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
//
//        Driver driver = driverRepository.findById(driverId)
//                .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));
//
//        // Update order
//        order.setAssignedDriverId(driverId);
//        order.setStatus("ASSIGNED");
//        orderRepository.save(order);
//
//        // Update driver status to BUSY
//        driver.setStatus("BUSY");
//        driverRepository.save(driver);
//
//        log.info("✓ Driver {} successfully assigned to order {}", driverId, orderId);
//        log.info("  Order status: ASSIGNED");
//        log.info("  Driver status: BUSY");
//    }
//
//    /**
//     * Called when driver accepts an order via SMS URL
//     * POST /api/drivers/{driverId}/accept-order/{orderId}
//     *
//     * PRODUCTION VERSION with proper tracking and race condition handling:
//     * - Checks if order already assigned (first driver wins)
//     * - Updates notification tracker
//     * - Assigns driver and updates statuses
//     *
//     * Handles concurrent acceptance attempts - first driver wins
//     * Other drivers get "Order already assigned" error
//     *
//     * @param orderId Order ID
//     * @param driverId Driver ID
//     */
//    @Transactional
//    public void driverAcceptOrder(Long orderId, Long driverId) {
//        log.info("========================================");
//        log.info("Driver {} attempting to accept order {}", driverId, orderId);
//        log.info("========================================");
//
//        Order order = orderRepository.findById(orderId)
//                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
//
//        // Check if order already assigned (race condition protection)
//        if (order.getAssignedDriverId() != null) {
//            log.warn("Order {} already assigned to driver {}. Driver {} was too slow.",
//                    orderId, order.getAssignedDriverId(), driverId);
//            throw new RuntimeException("Order already assigned to another driver. Better luck next time!");
//        }
//
//        // Check if order is in correct status
//        if (!"ACCEPTED".equals(order.getStatus())) {
//            log.warn("Order {} is in status '{}', expected 'ACCEPTED'", orderId, order.getStatus());
//            throw new RuntimeException("Order is not available for assignment. Current status: " + order.getStatus());
//        }
//
//        Driver driver = driverRepository.findById(driverId)
//                .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));
//
//        if (!"AVAILABLE".equals(driver.getStatus())) {
//            log.warn("Driver {} is {}, not AVAILABLE", driverId, driver.getStatus());
//            throw new RuntimeException("Driver is not available. Current status: " + driver.getStatus());
//        }
//
//        // Mark in tracker before assigning (for timeout mechanism)
//        notificationTracker.markOrderAccepted(orderId, driverId);
//
//        // Assign driver to order
//        assignDriverToOrderFinal(orderId, driverId);
//
//        log.info("========================================");
//        log.info("✓ Driver {} successfully accepted order {}", driverId, orderId);
//        log.info("========================================");
//    }
//
//    /**
//     * Marks order as picked up from restaurant
//     * POST /api/drivers/{driverId}/pickup-order/{orderId}
//     *
//     * @param orderId Order ID
//     * @param driverId Driver ID
//     */
//    @Transactional
//    public void markOrderPickedUp(Long orderId, Long driverId) {
//        log.info("Marking order {} as picked up by driver {}", orderId, driverId);
//
//        Order order = orderRepository.findById(orderId)
//                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
//
//        if (!driverId.equals(order.getAssignedDriverId())) {
//            throw new RuntimeException("Driver not assigned to this order");
//        }
//
//        if (!"ASSIGNED".equals(order.getStatus())) {
//            log.warn("Order {} is in status {} but expected ASSIGNED", orderId, order.getStatus());
//        }
//
//        order.setStatus("PICKED_UP");
//        orderRepository.save(order);
//
//        log.info("✓ Order {} marked as picked up", orderId);
//    }
//
//    /**
//     * Marks order as delivered to customer
//     * POST /api/drivers/{driverId}/deliver-order/{orderId}
//     *
//     * Updates:
//     * 1. order.status = 'DELIVERED'
//     * 2. order.delivered_at = current timestamp
//     * 3. driver.status = 'AVAILABLE' (frees up driver)
//     *
//     * @param orderId Order ID
//     * @param driverId Driver ID
//     */
//    @Transactional
//    public void markOrderDelivered(Long orderId, Long driverId) {
//        log.info("Marking order {} as delivered by driver {}", orderId, driverId);
//
//        Order order = orderRepository.findById(orderId)
//                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
//
//        if (!driverId.equals(order.getAssignedDriverId())) {
//            throw new RuntimeException("Driver not assigned to this order");
//        }
//
//        if (!"PICKED_UP".equals(order.getStatus())) {
//            log.warn("Order {} is in status {} but expected PICKED_UP", orderId, order.getStatus());
//        }
//
//        // Update order
//        order.setStatus("DELIVERED");
//        order.setDeliveredAt(LocalDateTime.now());
//        orderRepository.save(order);
//
//        // Free up driver for new orders
//        Driver driver = driverRepository.findById(driverId)
//                .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));
//        driver.setStatus("AVAILABLE");
//        driverRepository.save(driver);
//
//        log.info("✓ Order {} delivered successfully", orderId);
//        log.info("  Driver {} is now AVAILABLE for new orders", driverId);
//    }
//
//    /**
//     * Calculate distance between two points in kilometers
//     * Uses Haversine formula for great-circle distance
//     *
//     * Note: PostGIS ST_Distance with geography type is more accurate,
//     * but this is useful for application-level calculations
//     *
//     * @param point1 First point
//     * @param point2 Second point
//     * @return Distance in kilometers
//     */
//    private double calculateDistance(Point point1, Point point2) {
//        if (point1 == null || point2 == null) {
//            return Double.MAX_VALUE;
//        }
//
//        // Haversine formula
//        double lat1 = point1.getY();
//        double lon1 = point1.getX();
//        double lat2 = point2.getY();
//        double lon2 = point2.getX();
//
//        double R = 6371; // Earth's radius in km
//        double dLat = Math.toRadians(lat2 - lat1);
//        double dLon = Math.toRadians(lon2 - lon1);
//
//        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
//                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
//                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
//
//        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
//
//        return R * c;
//    }
//}
//
//
//// ========================================================================
//// DETAILED FLOW DIAGRAM
//// ========================================================================
//
///*
//
//ORDER ACCEPTANCE & DRIVER ASSIGNMENT FLOW
//=========================================
//
//1. RESTAURANT ACCEPTS ORDER
//   ↓
//   POST /api/restaurant/orders/{orderId}/accept
//   ↓
//   RestaurantOrderService.acceptOrder()
//   ↓
//   - Update order.status = 'ACCEPTED'
//   - Set order.accepted_at = NOW()
//   ↓
//   DeliveryManagementService.assignDriverToOrder() [ASYNC]
//   ↓
//
//2. FETCH RESTAURANT LOCATION
//   ↓
//   restaurantRepository.findById(restaurantId)
//   ↓
//   Extract Point coordinates: (longitude, latitude)
//   ↓
//
//3. FIND NEAREST DRIVERS (PostGIS Query)
//   ↓
//   driverRepository.findNearestAvailableDrivers(lng, lat, 20)
//   ↓
//   SQL: SELECT * FROM drivers
//        WHERE is_active = true AND status = 'AVAILABLE'
//        ORDER BY ST_Distance(current_location, ST_MakePoint(lng, lat))
//        LIMIT 20
//   ↓
//   Returns: [Driver1, Driver2, ..., Driver20] (ordered by distance)
//   ↓
//
//4. NOTIFY DRIVERS (Batch 1)
//   ↓
//   For each driver:
//   - Calculate distance to restaurant
//   - Create DriverNotificationDTO
//   - Send push notification (FCM/APNs)
//   - Send SMS (Twilio)
//   ↓
//   Wait for driver acceptance (30-60 seconds)
//   ↓
//
//5. DRIVER RESPONSE
//   ↓
//   If accepted:
//     ↓
//     assignDriverToOrderFinal()
//     ↓
//     - order.assigned_driver_id = driverId
//     - order.status = 'ASSIGNED'
//     - driver.status = 'BUSY'
//     ↓
//     DONE ✓
//
//   If no acceptance:
//     ↓
//     Fetch next 20 drivers (Batch 2)
//     ↓
//     Repeat steps 3-5
//     ↓
//     Continue until driver assigned or max attempts (10 batches = 200 drivers)
//   ↓
//
//6. DRIVER PICKS UP ORDER
//   ↓
//   POST /api/drivers/{driverId}/pickup-order/{orderId}
//   ↓
//   - order.status = 'PICKED_UP'
//   ↓
//
//7. DRIVER DELIVERS ORDER
//   ↓
//   POST /api/drivers/{driverId}/deliver-order/{orderId}
//   ↓
//   - order.status = 'DELIVERED'
//   - order.delivered_at = NOW()
//   - driver.status = 'AVAILABLE' (ready for next order)
//   ↓
//   COMPLETE ✓
//
//
//DATABASE STATE CHANGES
//======================
//
//Initial State:
//- order.status = 'PENDING'
//- driver.status = 'AVAILABLE'
//
//After Restaurant Accepts:
//- order.status = 'ACCEPTED'
//- order.accepted_at = timestamp
//
//After Driver Assignment:
//- order.status = 'ASSIGNED'
//- order.assigned_driver_id = X
//- driver.status = 'BUSY'
//
//After Pickup:
//- order.status = 'PICKED_UP'
//
//After Delivery:
//- order.status = 'DELIVERED'
//- order.delivered_at = timestamp
//- driver.status = 'AVAILABLE'
//
//
//POSTGIS QUERY DETAILS
//=====================
//
//The core query that powers the driver search:
//
//SELECT d.*
//FROM drivers d
//WHERE d.is_active = true
//  AND d.status = 'AVAILABLE'
//  AND d.current_location IS NOT NULL
//ORDER BY ST_Distance(
//    d.current_location,
//    ST_SetSRID(ST_MakePoint(-73.935242, 40.730610), 4326)
//)
//LIMIT 20;
//
//Explanation:
//- ST_MakePoint(lng, lat): Creates geometry point from coordinates
//- ST_SetSRID(..., 4326): Sets spatial reference (WGS 84 - GPS)
//- ST_Distance(): Calculates planar distance (fast but approximate)
//- For accurate Earth distance, use ::geography cast:
//  ST_Distance(point1::geography, point2::geography) / 1000 AS km
//
//*/





package com.delivery_management_service.services;

import com.delivery_management_service.DTO.DriverNotificationDTO;
import com.delivery_management_service.models.Driver;
import com.delivery_management_service.models.Order;
import com.delivery_management_service.models.Restaurant;
import com.delivery_management_service.repository.DriverRepository;
import com.delivery_management_service.repository.OrderRepository;
import com.delivery_management_service.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DeliveryManagementService
 *
 * Core service responsible for:
 * 1. Finding nearest available drivers using PostGIS spatial queries
 * 2. Notifying drivers in batches of 20
 * 3. Assigning drivers to orders
 * 4. Managing driver status (AVAILABLE/BUSY)
 * 5. Handling order lifecycle (ASSIGNED -> PICKED_UP -> DELIVERED)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryManagementService {

    private final DriverRepository driverRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderRepository orderRepository;
    private final TwilioSmsService twilioSmsService;

    @Value("${app.delivery.driver-batch-size:20}")
    private int driverBatchSize;

    @Value("${app.delivery.max-driver-distance-km:10}")
    private double maxDriverDistanceKm;

    @Value("${app.delivery.base-url}")
    private String baseUrl;

    /**
     * MAIN METHOD: Assigns a driver to an order
     *
     * Process:
     * 1. Fetch restaurant location (lat/lng)
     * 2. Use PostGIS to find nearest 20 available drivers
     * 3. Notify drivers (simulated)
     * 4. If none accepts, fetch next 20 drivers
     * 5. Once accepted, update order and driver status
     *
     * Runs asynchronously to avoid blocking restaurant acceptance response
     *
     * @param orderId Order ID to assign driver to
     * @param restaurantId Restaurant ID (to get location)
     */
    @Async
    @Transactional
    public void assignDriverToOrder(Long orderId, Long restaurantId) {
        log.info("========================================");
        log.info("Starting driver assignment for order: {} from restaurant: {}", orderId, restaurantId);
        log.info("========================================");

        try {
            // Step 1: Fetch restaurant with coordinates
            Restaurant restaurant = restaurantRepository.findById(restaurantId)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found: " + restaurantId));

            Point restaurantLocation = restaurant.getLocation();
            if (restaurantLocation == null) {
                log.error("Restaurant {} has no location coordinates", restaurantId);
                throw new RuntimeException("Restaurant location not available");
            }

            double longitude = restaurantLocation.getX();
            double latitude = restaurantLocation.getY();

            log.info("Restaurant location: lat={}, lng={}", latitude, longitude);

            // Step 2: Fetch order details
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

            // Step 3: Find and notify drivers in batches
            boolean driverAssigned = findAndNotifyDrivers(order, restaurant, longitude, latitude);

            if (!driverAssigned) {
                log.warn("No driver accepted order: {}. Marking as UNASSIGNED", orderId);
                order.setStatus("UNASSIGNED");
                orderRepository.save(order);
//                orderRepository.flush(); // Force immediate write
            }

        } catch (Exception e) {
            log.error("Error assigning driver to order {}: {}", orderId, e.getMessage(), e);
            // Update order status to indicate assignment failure
            orderRepository.findById(orderId).ifPresent(order -> {
                order.setStatus("ASSIGNMENT_FAILED");
                orderRepository.save(order);
            });
        }
    }

    /**
     * Finds nearest drivers and notifies them in batches until one accepts
     *
     * Uses PostGIS ST_Distance to find drivers ordered by proximity
     * Notifies in batches of 20 (configurable)
     * If no driver accepts, moves to next batch
     *
     * @param order Order to assign
     * @param restaurant Restaurant details
     * @param longitude Restaurant longitude
     * @param latitude Restaurant latitude
     * @return true if driver was assigned, false otherwise
     */
    private boolean findAndNotifyDrivers(Order order, Restaurant restaurant,
                                         double longitude, double latitude) {
        int maxAttempts = 1; // Maximum 10 batches (200 drivers)
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            log.info("========================================");
            log.info("Driver search attempt {} for order {}", attempt, order.getId());
            log.info("========================================");

            // CRITICAL: PostGIS query to find nearest available drivers
            // This uses ST_Distance to order drivers by proximity
            List<Driver> nearestDrivers = driverRepository.findNearestAvailableDrivers(
                    longitude,
                    latitude,
                    driverBatchSize
            );

            if (nearestDrivers.isEmpty()) {
                log.warn("No more available drivers found for order: {}", order.getId());
                return false;
            }

            log.info("Found {} available drivers in batch {}", nearestDrivers.size(), attempt);

            // Log driver details
            for (int i = 0; i < nearestDrivers.size(); i++) {
                Driver driver = nearestDrivers.get(i);
                double distance = calculateDistance(driver.getCurrentLocation(), restaurant.getLocation());
                log.debug("  {}. Driver {} ({}) - {}km away",
                        i + 1, driver.getId(), driver.getName(), String.format("%.2f", distance));
            }

            // Notify drivers and wait for acceptance
            Optional<Driver> acceptedDriver = notifyDriversAndWaitForAcceptance(
                    nearestDrivers, order, restaurant
            );

            if (acceptedDriver.isPresent()) {
                Driver driver = acceptedDriver.get();
                assignDriverToOrderFinal(order.getId(), driver.getId());
                log.info("✓ Driver {} assigned to order {}", driver.getId(), order.getId());
                return true;
            }

            log.info("No driver accepted in batch {}. Trying next batch...", attempt);

            // In a real system, you might want to add a delay here
            // to avoid overwhelming the system
            try {
                Thread.sleep(2000); // 2 second delay between batches
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted during driver search", e);
                return false;
            }
        }

        log.error("Exhausted all {} attempts. No driver found for order {}", maxAttempts, order.getId());
        return false;
    }

    /**
     * Notifies a batch of drivers via SMS with acceptance URL
     *
     * Sends SMS to each driver containing:
     * - Order details (restaurant, distance, amount)
     * - Direct acceptance URL
     *
     * The URL format: {baseUrl}/api/drivers/{driverId}/accept-order/{orderId}
     *
     * When driver clicks the URL, it automatically accepts the order.
     * First driver to click gets the order.
     *
     * @param drivers List of drivers to notify
     * @param order Order details
     * @param restaurant Restaurant details
     * @return Optional containing accepting driver, or empty if none accepted
     */
//    private Optional<Driver> notifyDriversAndWaitForAcceptance(
//            List<Driver> drivers, Order order, Restaurant restaurant) {
//
//        log.info("========================================");
//        log.info("Sending SMS notifications to {} drivers", drivers.size());
//        log.info("========================================");
//
//        List<DriverNotificationDTO> notifications = new ArrayList<>();
//        int successCount = 0;
//        int failCount = 0;
//
//        for (Driver driver : drivers) {
//            try {
//                // Calculate distance from driver to restaurant
//                double distance = calculateDistance(
//                        driver.getCurrentLocation(),
//                        restaurant.getLocation()
//                );
//
//                // Build acceptance URL
//                String acceptanceUrl = String.format(
//                        "%s/api/drivers/%d/accept-order/%d",
//                        baseUrl,
//                        driver.getId(),
//                        order.getId()
//                );
//
//                // Prepare notification DTO for logging/tracking
//                DriverNotificationDTO notification = DriverNotificationDTO.builder()
//                        .orderId(order.getId())
//                        .driverId(driver.getId())
//                        .restaurantName(restaurant.getName())
//                        .restaurantAddress(restaurant.getAddress())
//                        .deliveryAddress(order.getDeliveryAddress())
//                        .totalAmount(order.getTotalAmount())
//                        .distanceKm(distance)
//                        .estimatedPickupTime("15-20 minutes")
//                        .build();
//
//                notifications.add(notification);
//
//                log.info("Sending SMS to driver {}: {} ({}km away)",
//                        driver.getId(),
//                        driver.getName(),
//                        String.format("%.2f", distance));
//
//                // SEND SMS VIA TWILIO
//                String messageSid = twilioSmsService.sendOrderNotification(
//                        driver.getPhone(),
//                        driver.getId(),
//                        order.getId(),
//                        restaurant.getName(),
//                        distance,
//                        order.getTotalAmount(),
//                        acceptanceUrl
//                );
//
//                if (messageSid != null) {
//                    successCount++;
//                    log.info("  ✓ SMS sent successfully to {} - SID: {}", driver.getPhone(), messageSid);
//                    log.info("  Acceptance URL: {}", acceptanceUrl);
//                } else {
//                    failCount++;
//                    log.error("  ✗ Failed to send SMS to {}", driver.getPhone());
//                }
//
//            } catch (Exception e) {
//                failCount++;
//                log.error("  ✗ Error notifying driver {}: {}", driver.getId(), e.getMessage(), e);
//            }
//        }
//
//        log.info("========================================");
//        log.info("SMS Notification Summary:");
//        log.info("  Total drivers: {}", drivers.size());
//        log.info("  Successful: {}", successCount);
//        log.info("  Failed: {}", failCount);
//        log.info("========================================");
//        log.info("Waiting for driver to accept via URL...");
//
//        // In production, you would:
//        // 1. Wait for driver to click URL (calls driverAcceptOrder)
//        // 2. Use a timeout mechanism (e.g., 60 seconds)
//        // 3. Move to next batch if no acceptance
//        // 4. Use Redis/Database to track which drivers were notified
//
//        // For now, simulate waiting and return empty
//        // The actual acceptance happens when driver clicks the URL
//        // which calls: POST /api/drivers/{driverId}/accept-order/{orderId}
//
//        return Optional.empty(); // Driver will accept via URL callback
//    }

    private Optional<Driver> notifyDriversAndWaitForAcceptance(
            List<Driver> drivers, Order order, Restaurant restaurant) {

        log.info("========================================");
        log.info("Sending SMS notifications to {} drivers", drivers.size());
        log.info("========================================");

        List<DriverNotificationDTO> notifications = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (Driver driver : drivers) {
            try {
                double distance = calculateDistance(
                        driver.getCurrentLocation(),
                        restaurant.getLocation()
                );

                String acceptanceUrl = String.format(
                        "%s/api/drivers/%d/accept-order/%d",
                        baseUrl,
                        driver.getId(),
                        order.getId()
                );

                DriverNotificationDTO notification = DriverNotificationDTO.builder()
                        .orderId(order.getId())
                        .driverId(driver.getId())
                        .restaurantName(restaurant.getName())
                        .restaurantAddress(restaurant.getAddress())
                        .deliveryAddress(order.getDeliveryAddress())
                        .totalAmount(order.getTotalAmount())
                        .distanceKm(distance)
                        .estimatedPickupTime("15-20 minutes")
                        .build();

                notifications.add(notification);

                log.info("Sending SMS to driver {}: {} ({}km away)",
                        driver.getId(),
                        driver.getName(),
                        String.format("%.2f", distance));

                String messageSid = twilioSmsService.sendOrderNotification(
                        driver.getPhone(),
                        driver.getId(),
                        order.getId(),
                        restaurant.getName(),
                        distance,
                        order.getTotalAmount(),
                        acceptanceUrl
                );

                if (messageSid != null) {
                    successCount++;
                    log.info("  ✓ SMS sent successfully to {} - SID: {}", driver.getPhone(), messageSid);
                } else {
                    failCount++;
                    log.error("  ✗ Failed to send SMS to {}", driver.getPhone());
                }

            } catch (Exception e) {
                failCount++;
                log.error("  ✗ Error notifying driver {}: {}", driver.getId(), e.getMessage(), e);
            }
        }

        log.info("========================================");
        log.info("SMS Notification Summary:");
        log.info("  Total drivers: {}", drivers.size());
        log.info("  Successful: {}", successCount);
        log.info("  Failed: {}", failCount);
        log.info("========================================");
        log.info("Waiting up to 2 minutes for driver acceptance...");

        // ---------------------------------------------
        // WAIT FOR DRIVER ACCEPTANCE (max 2 minutes)
        // ---------------------------------------------
        long waitUntil = System.currentTimeMillis() + (2 * 60 * 1000); // 2 minutes

        while (System.currentTimeMillis() < waitUntil) {
            // Reload order to check if assigned
            Order refreshedOrder = orderRepository.findById(order.getId()).orElse(null);

            if (refreshedOrder != null && refreshedOrder.getAssignedDriverId() != null) {
                Long assignedDriverId = refreshedOrder.getAssignedDriverId();
                log.info("✓ Driver {} accepted the order!", assignedDriverId);

                return Optional.of(
                        driverRepository.findById(assignedDriverId).orElse(null)
                );
            }

            try {
                Thread.sleep(2000); // Check every 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for acceptance", e);
                break;
            }
        }

        log.warn("No driver accepted the order within 2 minutes.");
        return Optional.empty();
    }

    /**
     * Finalizes driver assignment to order
     *
     * Updates:
     * 1. order.assigned_driver_id = driverId
     * 2. order.status = 'ASSIGNED'
     * 3. driver.status = 'BUSY'
     *
     * @param orderId Order ID
     * @param driverId Driver ID
     */
    @Transactional
    public void assignDriverToOrderFinal(Long orderId, Long driverId) {
        log.info("Finalizing driver assignment: order={}, driver={}", orderId, driverId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));

        // Update order
        order.setAssignedDriverId(driverId);
        order.setStatus("ASSIGNED");
        orderRepository.save(order);

        // Update driver status to BUSY
        driver.setStatus("BUSY");
        driverRepository.save(driver);

        log.info("✓ Driver {} successfully assigned to order {}", driverId, orderId);
        log.info("  Order status: ASSIGNED");
        log.info("  Driver status: BUSY");
    }

    /**
     * Called when driver accepts an order via SMS URL
     * POST /api/drivers/{driverId}/accept-order/{orderId}
     *
     * Handles concurrent acceptance attempts - first driver wins
     * Other drivers get "Order already assigned" error
     *
     * @param orderId Order ID
     * @param driverId Driver ID
     */
    @Transactional
    public void driverAcceptOrder(Long orderId, Long driverId) {
        log.info("========================================");
        log.info("Driver {} attempting to accept order {}", driverId, orderId);
        log.info("========================================");

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Check if order already assigned (race condition protection)
        if (order.getAssignedDriverId() != null) {
            log.warn("Order {} already assigned to driver {}. Driver {} was too slow.",
                    orderId, order.getAssignedDriverId(), driverId);
            throw new RuntimeException("Order already assigned to another driver. Better luck next time!");
        }

        // Check if order is in correct status
        if (!"ACCEPTED".equals(order.getStatus())) {
            log.warn("Order {} is in status '{}', expected 'PENDING'", orderId, order.getStatus());
            throw new RuntimeException("Order is not available for assignment. Current status: " + order.getStatus());
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));

        if (!"AVAILABLE".equals(driver.getStatus())) {
            log.warn("Driver {} is {}, not AVAILABLE", driverId, driver.getStatus());
            throw new RuntimeException("Driver is not available. Current status: " + driver.getStatus());
        }

        // Assign driver to order
        assignDriverToOrderFinal(orderId, driverId);

        log.info("========================================");
        log.info("✓ Driver {} successfully accepted order {}", driverId, orderId);
        log.info("========================================");
    }

    /**
     * Marks order as picked up from restaurant
     * POST /api/drivers/{driverId}/pickup-order/{orderId}
     *
     * @param orderId Order ID
     * @param driverId Driver ID
     */
    @Transactional
    public void markOrderPickedUp(Long orderId, Long driverId) {
        log.info("Marking order {} as picked up by driver {}", orderId, driverId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (!driverId.equals(order.getAssignedDriverId())) {
            throw new RuntimeException("Driver not assigned to this order");
        }

        if (!"ASSIGNED".equals(order.getStatus())) {
            log.warn("Order {} is in status {} but expected ASSIGNED", orderId, order.getStatus());
        }

        order.setStatus("PICKED_UP");
        orderRepository.save(order);

        log.info("✓ Order {} marked as picked up", orderId);
    }

    /**
     * Marks order as delivered to customer
     * POST /api/drivers/{driverId}/deliver-order/{orderId}
     *
     * Updates:
     * 1. order.status = 'DELIVERED'
     * 2. order.delivered_at = current timestamp
     * 3. driver.status = 'AVAILABLE' (frees up driver)
     *
     * @param orderId Order ID
     * @param driverId Driver ID
     */
    @Transactional
    public void markOrderDelivered(Long orderId, Long driverId) {
        log.info("Marking order {} as delivered by driver {}", orderId, driverId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (!driverId.equals(order.getAssignedDriverId())) {
            throw new RuntimeException("Driver not assigned to this order");
        }

        if (!"PICKED_UP".equals(order.getStatus())) {
            log.warn("Order {} is in status {} but expected PICKED_UP", orderId, order.getStatus());
        }

        // Update order
        order.setStatus("DELIVERED");
        order.setDeliveredAt(LocalDateTime.now());
        orderRepository.save(order);

        // Free up driver for new orders
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));
        driver.setStatus("AVAILABLE");
        driverRepository.save(driver);

        log.info("✓ Order {} delivered successfully", orderId);
        log.info("  Driver {} is now AVAILABLE for new orders", driverId);
    }

    /**
     * Calculate distance between two points in kilometers
     * Uses Haversine formula for great-circle distance
     *
     * Note: PostGIS ST_Distance with geography type is more accurate,
     * but this is useful for application-level calculations
     *
     * @param point1 First point
     * @param point2 Second point
     * @return Distance in kilometers
     */
    private double calculateDistance(Point point1, Point point2) {
        if (point1 == null || point2 == null) {
            return Double.MAX_VALUE;
        }

        // Haversine formula
        double lat1 = point1.getY();
        double lon1 = point1.getX();
        double lat2 = point2.getY();
        double lon2 = point2.getX();

        double R = 6371; // Earth's radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}


// ========================================================================
// DETAILED FLOW DIAGRAM
// ========================================================================

/*

ORDER ACCEPTANCE & DRIVER ASSIGNMENT FLOW
=========================================

1. RESTAURANT ACCEPTS ORDER
   ↓
   POST /api/restaurant/orders/{orderId}/accept
   ↓
   RestaurantOrderService.acceptOrder()
   ↓
   - Update order.status = 'ACCEPTED'
   - Set order.accepted_at = NOW()
   ↓
   DeliveryManagementService.assignDriverToOrder() [ASYNC]
   ↓

2. FETCH RESTAURANT LOCATION
   ↓
   restaurantRepository.findById(restaurantId)
   ↓
   Extract Point coordinates: (longitude, latitude)
   ↓

3. FIND NEAREST DRIVERS (PostGIS Query)
   ↓
   driverRepository.findNearestAvailableDrivers(lng, lat, 20)
   ↓
   SQL: SELECT * FROM drivers
        WHERE is_active = true AND status = 'AVAILABLE'
        ORDER BY ST_Distance(current_location, ST_MakePoint(lng, lat))
        LIMIT 20
   ↓
   Returns: [Driver1, Driver2, ..., Driver20] (ordered by distance)
   ↓

4. NOTIFY DRIVERS (Batch 1)
   ↓
   For each driver:
   - Calculate distance to restaurant
   - Create DriverNotificationDTO
   - Send push notification (FCM/APNs)
   - Send SMS (Twilio)
   ↓
   Wait for driver acceptance (30-60 seconds)
   ↓

5. DRIVER RESPONSE
   ↓
   If accepted:
     ↓
     assignDriverToOrderFinal()
     ↓
     - order.assigned_driver_id = driverId
     - order.status = 'ASSIGNED'
     - driver.status = 'BUSY'
     ↓
     DONE ✓

   If no acceptance:
     ↓
     Fetch next 20 drivers (Batch 2)
     ↓
     Repeat steps 3-5
     ↓
     Continue until driver assigned or max attempts (10 batches = 200 drivers)
   ↓

6. DRIVER PICKS UP ORDER
   ↓
   POST /api/drivers/{driverId}/pickup-order/{orderId}
   ↓
   - order.status = 'PICKED_UP'
   ↓

7. DRIVER DELIVERS ORDER
   ↓
   POST /api/drivers/{driverId}/deliver-order/{orderId}
   ↓
   - order.status = 'DELIVERED'
   - order.delivered_at = NOW()
   - driver.status = 'AVAILABLE' (ready for next order)
   ↓
   COMPLETE ✓


DATABASE STATE CHANGES
======================

Initial State:
- order.status = 'PENDING'
- driver.status = 'AVAILABLE'

After Restaurant Accepts:
- order.status = 'ACCEPTED'
- order.accepted_at = timestamp

After Driver Assignment:
- order.status = 'ASSIGNED'
- order.assigned_driver_id = X
- driver.status = 'BUSY'

After Pickup:
- order.status = 'PICKED_UP'

After Delivery:
- order.status = 'DELIVERED'
- order.delivered_at = timestamp
- driver.status = 'AVAILABLE'


POSTGIS QUERY DETAILS
=====================

The core query that powers the driver search:

SELECT d.*
FROM drivers d
WHERE d.is_active = true
  AND d.status = 'AVAILABLE'
  AND d.current_location IS NOT NULL
ORDER BY ST_Distance(
    d.current_location,
    ST_SetSRID(ST_MakePoint(-73.935242, 40.730610), 4326)
)
LIMIT 20;

Explanation:
- ST_MakePoint(lng, lat): Creates geometry point from coordinates
- ST_SetSRID(..., 4326): Sets spatial reference (WGS 84 - GPS)
- ST_Distance(): Calculates planar distance (fast but approximate)
- For accurate Earth distance, use ::geography cast:
  ST_Distance(point1::geography, point2::geography) / 1000 AS km

*/