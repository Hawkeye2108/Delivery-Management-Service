package com.delivery_management_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverNotificationDTO {
    private Long orderId;
    private Long driverId;
    private String restaurantName;
    private String restaurantAddress;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private Double distanceKm;
    private String estimatedPickupTime;
}