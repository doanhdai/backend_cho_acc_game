package com.shopaccgame.repository;

import com.shopaccgame.model.Skin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SkinRepository extends JpaRepository<Skin, Integer> {
    @Query("SELECT s FROM Skin s WHERE s.championName LIKE %:q% OR s.skinName LIKE %:q%")
    List<Skin> searchSkins(@Param("q") String q);
}
