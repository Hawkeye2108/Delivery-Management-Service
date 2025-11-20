package com.delivery_management_service.controllers;

//import com.delivery_management_service.com.delivery_management_service.DTO.RestaurantDTO;
//import com.delivery_management_service.com.delivery_management_service.exception.ResourceNotFoundException;
//import com.delivery_management_service.com.delivery_management_service.models.Restaurant;
//import com.delivery_management_service.com.delivery_management_service.repository.RestaurantRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@RestController
//public class RestaurantController {
//    @Autowired
//    private RestaurantRepository restaurantRepository;
//    @GetMapping("/restaurants")
//    public List<RestaurantDTO> getAllRestaurants(){
//        List<Restaurant> restaurants = restaurantRepository.findAll();
//        // Convert each Restaurants entity to RestaurantDTO
//        return restaurants.stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//    }
//
//    @GetMapping("/restaurant/{id}")
//    public ResponseEntity<RestaurantDTO> getRestaurantById(@PathVariable Long id){
//        Restaurant restaurant = restaurantRepository.findById(id)
//                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found with id: " + id));
//
//        RestaurantDTO dto = convertToDTO(restaurant);
//        return ResponseEntity.ok(dto);
//    }
//
//    // Helper method to map Restaurants entity to RestaurantDTO
//    private RestaurantDTO convertToDTO(Restaurant restaurant) {
//        return new RestaurantDTO(
//                restaurant.getId(),
//                restaurant.getName(),
//                restaurant.getAddress(),
//                restaurant.getPhone(),
//                restaurant.getEmail(),
//                restaurant.getDescription(),
//                restaurant.getActive()
//        );
//    }
//}


import com.delivery_management_service.DTO.ApiResponse;
import com.delivery_management_service.DTO.FoodItemDTO;
import com.delivery_management_service.DTO.RestaurantDTO;
import com.delivery_management_service.services.RestaurantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for Restaurant operations
 * Base URL: /api/restaurants
 */
@RestController
@RequestMapping("/api/restaurants")
//@RequiredArgsConstructor
@Slf4j
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService){
        this.restaurantService = restaurantService;
    }
    /**
     * Get all active restaurants
     *
     * GET /api/restaurants
     *
     * Response: List of restaurants (without location coordinates)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RestaurantDTO>>> getAllRestaurants() {
        log.info("GET /api/restaurants - Fetching all active restaurants");
        List<RestaurantDTO> restaurants = restaurantService.getAllActiveRestaurants();
        log.info("Found {} active restaurants", restaurants.size());
        return ResponseEntity.ok(ApiResponse.success(restaurants));
    }

    /**
     * Get restaurant by ID
     *
     * GET /api/restaurants/{id}
     *
     * @param id Restaurant ID
     * @return Restaurant details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RestaurantDTO>> getRestaurantById(@PathVariable Long id) {
        log.info("GET /api/restaurants/{} - Fetching restaurant details", id);
        RestaurantDTO restaurant = restaurantService.getRestaurantById(id);
        return ResponseEntity.ok(ApiResponse.success(restaurant));
    }

    /**
     * Get restaurant menu (all available food items)
     *
     * GET /api/restaurants/{id}/menu
     *
     * @param id Restaurant ID
     * @return List of available food items
     */
    @GetMapping("/{id}/menu")
    public ResponseEntity<ApiResponse<List<FoodItemDTO>>> getRestaurantMenu(@PathVariable Long id) {
        log.info("GET /api/restaurants/{}/menu - Fetching menu", id);
        List<FoodItemDTO> menu = restaurantService.getRestaurantMenu(id);
        log.info("Found {} menu items for restaurant {}", menu.size(), id);
        return ResponseEntity.ok(ApiResponse.success("Menu retrieved successfully", menu));
    }
}