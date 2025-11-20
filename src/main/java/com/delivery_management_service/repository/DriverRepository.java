package com.delivery_management_service.repository;

import com.delivery_management_service.models.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriverRepository extends JpaRepository<Driver,Long> {

    /**
     * CRITICAL POSTGIS QUERY: Find nearest available drivers
     *
     * Uses PostGIS ST_Distance function to calculate distance from restaurant
     * Orders drivers by proximity and returns the nearest N drivers
     *
     * Query Explanation:
     * - ST_SetSRID(ST_MakePoint(:lng, :lat), 4326): Creates a Point geometry from coordinates
     * - ST_Distance(): Calculates distance between driver location and restaurant
     * - SRID 4326: Standard GPS coordinate system (WGS 84)
     *
     * @param longitude Restaurant longitude (X coordinate)
     * @param latitude Restaurant latitude (Y coordinate)
     * @param limit Number of drivers to return (e.g., 20 for batch notification)
     * @return List of nearest available drivers ordered by distance
     */
    @Query(value = "SELECT d.* FROM drivers d " +
            "WHERE d.is_active = true " +
            "AND d.status = 'AVAILABLE' " +
            "AND d.current_location IS NOT NULL " +
            "ORDER BY ST_Distance(d.current_location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)) " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Driver> findNearestAvailableDrivers(
            @Param("lng") Double longitude,
            @Param("lat") Double latitude,
            @Param("limit") int limit
    );

    /**
     * ADVANCED POSTGIS QUERY: Find nearest drivers with actual distance in kilometers
     *
     * Uses geography type for accurate distance calculation on Earth's surface
     * Returns both driver data and calculated distance
     *
     * Query Explanation:
     * - ::geography cast: Converts geometry to geography for accurate Earth distance
     * - ST_Distance on geography: Returns distance in meters
     * - / 1000: Converts meters to kilometers
     *
     * @param longitude Restaurant longitude
     * @param latitude Restaurant latitude
     * @param limit Number of drivers to return
     * @return List of Object arrays: [Driver fields..., distance_km]
     */
    @Query(value = "SELECT d.*, " +
            "ST_Distance(d.current_location::geography, " +
            "            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) / 1000 as distance_km " +
            "FROM drivers d " +
            "WHERE d.is_active = true " +
            "AND d.status = 'AVAILABLE' " +
            "AND d.current_location IS NOT NULL " +
            "ORDER BY distance_km " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findNearestDriversWithDistance(
            @Param("lng") Double longitude,
            @Param("lat") Double latitude,
            @Param("limit") int limit
    );

    /**
     * Update driver status
     * Possible values: AVAILABLE, BUSY, OFFLINE
     *
     * @param driverId Driver ID
     * @param status New status value
     */
    @Modifying
    @Query("UPDATE Driver d SET d.status = :status WHERE d.id = :driverId")
    void updateDriverStatus(@Param("driverId") Long driverId, @Param("status") String status);

    /**
     * Find all active drivers by status
     */
    List<Driver> findByStatusAndIsActiveTrue(String status);
}
