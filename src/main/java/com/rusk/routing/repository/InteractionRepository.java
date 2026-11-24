package com.rusk.routing.repository;

import com.rusk.routing.model.Interaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

	/**
	 * Loads an interaction and locks its row (SELECT ... FOR UPDATE) until the transaction ends,
	 * so concurrent requests cannot route the same interaction twice.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Interaction i where i.id = :id")
	Optional<Interaction> findByIdForUpdate(Long id);
}
