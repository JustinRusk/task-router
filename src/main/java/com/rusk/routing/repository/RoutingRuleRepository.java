package com.rusk.routing.repository;

import com.rusk.routing.model.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {

	/** Enabled rules with their target queues loaded in the same query, lowest priority number first. */
	@Query("select r from RoutingRule r join fetch r.targetQueue where r.enabled = true order by r.priority")
	List<RoutingRule> findEnabledInPriorityOrder();
}
