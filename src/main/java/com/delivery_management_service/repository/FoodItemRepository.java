package com.delivery_management_service.repository;

import com.delivery_management_service.models.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FoodItemRepository extends JpaRepository<FoodItem,Long> {
    /**
     * Find all available food items for a restaurant
     */

    @Query(value = "Select f from FoodItem f where f.restaurantId = :restaurantId and f.isAvailable = true")
    List<FoodItem> findByRestaurantIdAndIsAvailableTrue(@Param("restaurantId") Long restaurantId);

    /**
     * Find all food items for a restaurant (including unavailable)
     */
    List<FoodItem> findByRestaurantId(Long restaurantId);
}
