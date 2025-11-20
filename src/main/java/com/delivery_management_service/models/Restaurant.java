package com.delivery_management_service.models;

import jakarta.persistence.*;

import org.locationtech.jts.geom.Point;
@Entity
@Table(name="restaurants")
public class Restaurant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String address;

    private String phone;
    private String email;
    private String description;

    @Column(nullable = false)
    private Boolean isActive = true;

    // PostGIS Point for location storage
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    public Restaurant(){

    }
    public Restaurant(Long id, String name, String phone, Boolean isActive, String description, String email, String address, Point location) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.isActive = isActive;
        this.description = description;
        this.email = email;
        this.address = address;
        this.location = location;
    }



    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setLocation(Point location) {
        this.location = location;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public Point getLocation() {
        return location;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public String getDescription() {
        return description;
    }



    // Helper methods for latitude and longitude
    public Double getLatitude() {
        return location != null ? location.getY() : null;
    }

    public Double getLongitude() {
        return location != null ? location.getX() : null;
    }
}
