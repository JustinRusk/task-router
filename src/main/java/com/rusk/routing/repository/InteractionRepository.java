package com.rusk.routing.repository;

import com.rusk.routing.model.Interaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface InteractionRepository extends JpaRepository<Interaction, Long>, JpaSpecificationExecutor<Interaction> {

	Optional<Interaction> findByCallId(String callId);

	/**
	 * Loads an interaction and locks its row (SELECT ... FOR UPDATE) until the transaction ends,
	 * so concurrent requests cannot route the same interaction twice.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Interaction i where i.id = :id")
	Optional<Interaction> findByIdForUpdate(Long id);

	/**
	 * Filtered, paged search. Loads each interaction's queue and matched rule in the same query,
	 * so a page of results does not trigger one extra query per row.
	 */
	@Override
	@EntityGraph(attributePaths = {"routedQueue", "matchedRule", "matchedRule.targetQueue"})
	Page<Interaction> findAll(Specification<Interaction> spec, Pageable pageable);
}
