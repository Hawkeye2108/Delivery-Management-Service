package com.delivery_management_service.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Twilio
 *
 * Binds application.yml properties to Java object
 * Provides validation and type safety
 */
@Configuration
@ConfigurationProperties(prefix = "app.twilio")
@Data
@Validated
public class TwilioConfig {

    /**
     * Twilio Account SID
     * Format: ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
     */
    @NotBlank(message = "Twilio Account SID is required")
    private String accountSid;

    /**
     * Twilio Auth Token
     * Keep this secret!
     */
    @NotBlank(message = "Twilio Auth Token is required")
    private String authToken;

    /**
     * Twilio phone number to send SMS from
     * Format: +1234567890 (E.164)
     */
    @NotBlank(message = "Twilio phone number is required")
    private String fromPhone;

    /**
     * Enable/disable SMS sending
     * Set to false for development/testing
     */
    private boolean enabled = true;
}
