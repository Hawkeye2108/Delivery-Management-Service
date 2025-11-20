package com.delivery_management_service.repository;

import com.delivery_management_service.models.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant,Long> {

    /**
     * Find all active restaurants
     */
    List<Restaurant> findByIsActiveTrue();

    /**
     * Find active restaurant by ID
     */
    Optional<Restaurant> findByIdAndIsActiveTrue(Long id);

    /**
     * Find restaurant with coordinates extracted
     * Returns: [id, name, address, phone, email, description, is_active, longitude, latitude]
     */
    @Query(value = "SELECT id, name, address, phone, email, description, is_active, " +
            "ST_X(location) as longitude, ST_Y(location) as latitude " +
            "FROM restaurants WHERE id = :id",
            nativeQuery = true)
    Object[] findRestaurantWithCoordinates(@Param("id") Long id);
}
