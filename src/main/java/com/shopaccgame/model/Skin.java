package com.shopaccgame.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "skins")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "champion_name", nullable = false, length = 100)
    private String championName;

    @Column(name = "skin_name", nullable = false, length = 100)
    private String skinName;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;
}
