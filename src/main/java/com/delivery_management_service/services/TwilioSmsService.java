package com.delivery_management_service.services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Service for sending SMS notifications via Twilio
 *
 * Features:
 * - Send SMS to drivers with order acceptance links
 * - Configurable (can be disabled for development)
 * - Error handling and logging
 * - Support for E.164 phone number format
 */
@Service
@Slf4j
public class TwilioSmsService {

    @Value("${app.twilio.account-sid}")
    private String accountSid;

    @Value("${app.twilio.auth-token}")
    private String authToken;

    @Value("${app.twilio.from-phone}")
    private String fromPhoneNumber;

    @Value("${app.twilio.enabled:true}")
    private boolean smsEnabled;

    /**
     * Initialize Twilio client on application startup
     */
    @PostConstruct
    public void init() {
        if (smsEnabled) {
            try {
                Twilio.init(accountSid, authToken);
                log.info("========================================");
                log.info("Twilio SMS service initialized successfully");
                log.info("Account SID: {}", accountSid.substring(0, 10) + "...");
                log.info("Sending SMS from: {}", fromPhoneNumber);
                log.info("========================================");
            } catch (Exception e) {
                log.error("Failed to initialize Twilio: {}", e.getMessage(), e);
                throw new RuntimeException("Twilio initialization failed", e);
            }
        } else {
            log.warn("========================================");
            log.warn("Twilio SMS service is DISABLED");
            log.warn("SMS messages will be logged but not sent");
            log.warn("========================================");
        }
    }

    /**
     * Send generic SMS message
     *
     * @param toPhoneNumber Recipient phone number (must be in E.164 format: +1234567890)
     * @param messageText SMS message content
     * @return Message SID if successful, null otherwise
     */
    public String sendSms(String toPhoneNumber, String messageText) {
        if (!smsEnabled) {
            log.info("SMS DISABLED - Would have sent to {}", toPhoneNumber);
            log.info("Message: {}", messageText);
            return "SMS_DISABLED";
        }

        try {
            log.info("Sending SMS to {}", toPhoneNumber);
            log.debug("Message content: {}", messageText);

            Message message = Message.creator(
                    new PhoneNumber(toPhoneNumber),  // To
                    new PhoneNumber(fromPhoneNumber), // From
                    messageText                       // Message body
            ).create();

            log.info("✓ SMS sent successfully to {}. SID: {}", toPhoneNumber, message.getSid());
            log.debug("SMS Status: {}", message.getStatus());

            return message.getSid();

        } catch (Exception e) {
            log.error("✗ Failed to send SMS to {}: {}", toPhoneNumber, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Send order notification SMS to driver with acceptance URL
     *
     * This is the main method used by DeliveryManagementService
     *
     * SMS Format:
     * 🍔 New Order Available!
     *
     * Restaurant: Pizza Palace
     * Distance: 2.5 km
     * Amount: $45.98
     *
     * Accept now: http://localhost:8080/api/drivers/5/accept-order/1
     *
     * First come, first served!
     *
     * @param driverPhone Driver's phone number (E.164 format)
     * @param driverId Driver ID
     * @param orderId Order ID
     * @param restaurantName Restaurant name
     * @param distanceKm Distance in kilometers
     * @param orderAmount Order total amount
     * @param acceptanceUrl URL to accept the order
     * @return Message SID if successful, null otherwise
     */
    public String sendOrderNotification(
            String driverPhone,
            Long driverId,
            Long orderId,
            String restaurantName,
            Double distanceKm,
            BigDecimal orderAmount,
            String acceptanceUrl) {

        // Format the SMS message
        String message = buildOrderNotificationMessage(
                restaurantName,
                distanceKm,
                orderAmount,
                acceptanceUrl
        );

        log.info("Sending order notification to driver {} ({})", driverId, driverPhone);
        log.debug("Order ID: {}, Restaurant: {}, Amount: ${}", orderId, restaurantName, orderAmount);

        return sendSms(driverPhone, message);
    }

    /**
     * Build formatted SMS message for order notification
     *
     * @param restaurantName Restaurant name
     * @param distanceKm Distance in km
     * @param orderAmount Order amount
     * @param acceptanceUrl Acceptance URL
     * @return Formatted SMS message
     */
    private String buildOrderNotificationMessage(
            String restaurantName,
            Double distanceKm,
            BigDecimal orderAmount,
            String acceptanceUrl) {

//        return String.format(
//                "🍔 New Order Available!\n\n" +
//                        "Restaurant: %s\n" +
//                        "Distance: %.1f km\n" +
//                        "Amount: $%s\n\n" +
//                        "Accept now: %s\n\n" +
//                        "First come, first served!",
//                restaurantName,
//                distanceKm,
//                orderAmount.toString(),
//                acceptanceUrl
//        );
        return String.format(
                "New Order Available!\n\nAccept now: %s",
                acceptanceUrl
        );
    }

    /**
     * Send order picked up notification to customer
     *
     * @param customerPhone Customer phone number
     * @param driverName Driver name
     * @param estimatedTime Estimated delivery time
     * @return Message SID if successful, null otherwise
     */
    public String sendOrderPickedUpNotification(
            String customerPhone,
            String driverName,
            String estimatedTime) {

        String message = String.format(
                "🚗 Your order has been picked up!\n\n" +
                        "Driver: %s\n" +
                        "Estimated arrival: %s\n\n" +
                        "Track your delivery in the app.",
                driverName,
                estimatedTime
        );

        return sendSms(customerPhone, message);
    }

    /**
     * Send order delivered notification to customer
     *
     * @param customerPhone Customer phone number
     * @param orderAmount Order total
     * @return Message SID if successful, null otherwise
     */
    public String sendOrderDeliveredNotification(
            String customerPhone,
            BigDecimal orderAmount) {

        String message = String.format(
                "✅ Your order has been delivered!\n\n" +
                        "Total: $%s\n\n" +
                        "Enjoy your meal! 🍽️\n" +
                        "Please rate your experience.",
                orderAmount.toString()
        );

        return sendSms(customerPhone, message);
    }

    /**
     * Send order assigned notification to restaurant
     *
     * @param restaurantPhone Restaurant phone number
     * @param orderId Order ID
     * @param driverName Driver name
     * @param driverPhone Driver phone number
     * @return Message SID if successful, null otherwise
     */
    public String sendOrderAssignedToRestaurant(
            String restaurantPhone,
            Long orderId,
            String driverName,
            String driverPhone) {

        String message = String.format(
                "✓ Driver assigned to Order #%d\n\n" +
                        "Driver: %s\n" +
                        "Phone: %s\n\n" +
                        "Preparing order for pickup.",
                orderId,
                driverName,
                driverPhone
        );

        return sendSms(restaurantPhone, message);
    }

    /**
     * Validate phone number format (E.164)
     *
     * @param phoneNumber Phone number to validate
     * @return true if valid E.164 format
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        // E.164 format: +[country code][number]
        // Example: +1234567890 (length: 11-15 chars)
        return phoneNumber.matches("^\\+[1-9]\\d{1,14}$");
    }

    /**
     * Format phone number to E.164 if needed
     *
     * @param phoneNumber Phone number
     * @param defaultCountryCode Country code (e.g., "1" for USA)
     * @return E.164 formatted phone number
     */
    public String formatToE164(String phoneNumber, String defaultCountryCode) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return null;
        }

        // Remove all non-digit characters
        String digitsOnly = phoneNumber.replaceAll("[^0-9]", "");

        // If doesn't start with country code, add it
        if (!phoneNumber.startsWith("+")) {
            return "+" + defaultCountryCode + digitsOnly;
        }

        return "+" + digitsOnly;
    }
}

// ========================================================================
// USAGE EXAMPLES
// ========================================================================

/*

1. BASIC USAGE (Injected into DeliveryManagementService)
========================================================

@Service
public class DeliveryManagementService {

    private final TwilioSmsService twilioSmsService;

    public void notifyDrivers(List<Driver> drivers, Order order) {
        for (Driver driver : drivers) {
            String acceptanceUrl = buildAcceptanceUrl(driver.getId(), order.getId());

            twilioSmsService.sendOrderNotification(
                driver.getPhone(),      // "+1234567890"
                driver.getId(),         // 5
                order.getId(),          // 1
                "Pizza Palace",         // Restaurant name
                2.5,                    // Distance in km
                new BigDecimal("45.98"), // Order amount
                acceptanceUrl           // http://localhost:8080/api/drivers/5/accept-order/1
            );
        }
    }
}


2. SEND CUSTOM SMS
==================

@Autowired
private TwilioSmsService smsService;

public void sendCustomMessage() {
    smsService.sendSms(
        "+1234567890",
        "Hello from Delivery System!"
    );
}


3. NOTIFY CUSTOMER OF PICKUP
=============================

smsService.sendOrderPickedUpNotification(
    "+1234567890",      // Customer phone
    "John Doe",         // Driver name
    "15-20 minutes"     // Estimated time
);


4. NOTIFY CUSTOMER OF DELIVERY
===============================

smsService.sendOrderDeliveredNotification(
    "+1234567890",              // Customer phone
    new BigDecimal("45.98")     // Order amount
);


5. NOTIFY RESTAURANT OF DRIVER ASSIGNMENT
==========================================

smsService.sendOrderAssignedToRestaurant(
    "+1234567890",      // Restaurant phone
    1L,                 // Order ID
    "John Doe",         // Driver name
    "+0987654321"       // Driver phone
);


6. VALIDATE PHONE NUMBER
=========================

if (smsService.isValidPhoneNumber("+1234567890")) {
    // Send SMS
} else {
    // Invalid format
}


7. FORMAT PHONE NUMBER TO E.164
================================

String formatted = smsService.formatToE164("(123) 456-7890", "1");
// Returns: +11234567890


8. DISABLE SMS FOR TESTING
===========================

In application.yml:

app:
  twilio:
    enabled: false

// SMS will be logged but not sent


9. REAL-WORLD EXAMPLE FROM DeliveryManagementService
=====================================================

private Optional<Driver> notifyDriversAndWaitForAcceptance(
        List<Driver> drivers, Order order, Restaurant restaurant) {

    for (Driver driver : drivers) {
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

        // SEND SMS WITH ACCEPTANCE LINK
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
            log.info("✓ SMS sent to driver {} - SID: {}", driver.getId(), messageSid);
        } else {
            log.error("✗ Failed to send SMS to driver {}", driver.getId());
        }
    }

    return Optional.empty(); // Wait for driver to click URL
}


10. ERROR HANDLING
==================

String messageSid = smsService.sendSms("+1234567890", "Test message");

if (messageSid != null) {
    // Success - save message SID for tracking
    log.info("SMS sent with SID: {}", messageSid);
} else {
    // Failed - log error and retry
    log.error("SMS failed to send");
}

*/