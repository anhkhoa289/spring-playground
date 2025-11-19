package com.khoa.spring.playground.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String email;

    // Removed JPA cascade - using database-level ON DELETE CASCADE instead
    // This improves performance for bulk delete operations
    @OneToMany(mappedBy = "user")
    @JsonManagedReference
    private List<Post> posts = new ArrayList<>();

    // Removed JPA cascade - using database-level ON DELETE CASCADE instead
    // This improves performance for bulk delete operations
    @OneToMany(mappedBy = "user")
    @JsonManagedReference
    private List<Favorite> favorites = new ArrayList<>();

    // Removed JPA cascade - using database-level ON DELETE CASCADE instead
    // This improves performance for bulk delete operations
    @OneToMany(mappedBy = "user")
    @JsonManagedReference
    private List<Resource> resources = new ArrayList<>();
}
