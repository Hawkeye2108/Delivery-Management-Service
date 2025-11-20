package com.delivery_management_service.repository;

import com.delivery_management_service.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Find all orders for a specific restaurant
     */
    List<Order> findByRestaurantId(Long restaurantId);

    /**
     * Find orders by status
     * Status values: PENDING, ACCEPTED, ASSIGNED, PICKED_UP, DELIVERED, CANCELLED, UNASSIGNED
     */
    List<Order> findByStatus(String status);

    /**
     * Find all orders assigned to a specific driver
     */
    List<Order> findByAssignedDriverId(Long driverId);

    /**
     * Find order with order items eagerly loaded using JOIN FETCH
     * Prevents N+1 query problem when accessing order items
     *
     * @param orderId Order ID
     * @return Optional containing Order with items loaded
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    /**
     * Find order with restaurant and driver details eagerly loaded
     * Useful for displaying complete order information in one query
     *
     * @param orderId Order ID
     * @return Optional containing Order with restaurant and driver loaded
     */
    @Query("SELECT o FROM Order o " +
            "LEFT JOIN FETCH o.restaurant " +
            "LEFT JOIN FETCH o.assignedDriver " +
            "WHERE o.id = :orderId")
    Optional<Order> findByIdWithDetails(@Param("orderId") Long orderId);
}
