package com.cielo.flashbooking.adapter.out.persistence.event;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EventJpaRepository extends JpaRepository<EventEntity, UUID> {
}
