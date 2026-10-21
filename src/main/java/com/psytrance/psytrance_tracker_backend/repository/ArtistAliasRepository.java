package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.ArtistAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistAliasRepository extends JpaRepository<ArtistAlias, String> {

    List<ArtistAlias> findByArtistId(Long artistId);
}
