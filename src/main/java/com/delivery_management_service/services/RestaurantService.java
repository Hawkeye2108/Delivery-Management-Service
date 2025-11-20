package com.delivery_management_service.services;

import com.delivery_management_service.DTO.FoodItemDTO;
import com.delivery_management_service.DTO.RestaurantDTO;
import com.delivery_management_service.models.FoodItem;
import com.delivery_management_service.models.Restaurant;
import com.delivery_management_service.repository.FoodItemRepository;
import com.delivery_management_service.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final FoodItemRepository foodItemRepository;

    @Transactional(readOnly = true)
    public List<RestaurantDTO> getAllActiveRestaurants() {
        log.info("Fetching all active restaurants");
        return restaurantRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RestaurantDTO getRestaurantById(Long id) {
        log.info("Fetching restaurant with id: {}", id);
        Restaurant restaurant = restaurantRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + id));
        return mapToDTO(restaurant);
    }

    @Transactional(readOnly = true)
    public List<FoodItemDTO> getRestaurantMenu(Long restaurantId) {
        log.info("Fetching menu for restaurant id: {}", restaurantId);

        // Verify restaurant exists
        restaurantRepository.findByIdAndIsActiveTrue(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + restaurantId));
        log.info("fetching = {} ",restaurantId);
        return foodItemRepository.findByRestaurantIdAndIsAvailableTrue(restaurantId)
                .stream()
                .map(this::mapFoodItemToDTO)
                .collect(Collectors.toList());
    }

    private RestaurantDTO mapToDTO(Restaurant restaurant) {
        return RestaurantDTO.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .address(restaurant.getAddress())
                .phone(restaurant.getPhone())
                .email(restaurant.getEmail())
                .description(restaurant.getDescription())
                .isActive(restaurant.getIsActive())
                .build();
    }

    private FoodItemDTO mapFoodItemToDTO(FoodItem item) {
        return FoodItemDTO.builder()
                .id(item.getId())
                .restaurantId(item.getRestaurantId())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .category(item.getCategory())
                .imageUrl(item.getImageUrl())
                .isVegetarian(item.getIsVegetarian())
                .isAvailable(item.getIsAvailable())
                .preparationTimeMinutes(item.getPreparationTimeMinutes())
                .build();
    }
}

