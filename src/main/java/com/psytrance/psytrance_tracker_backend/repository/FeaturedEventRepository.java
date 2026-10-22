package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.FeaturedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeaturedEventRepository extends JpaRepository<FeaturedEvent, Long> {

    List<FeaturedEvent> findAllByOrderByFeaturedAtDesc();
}
