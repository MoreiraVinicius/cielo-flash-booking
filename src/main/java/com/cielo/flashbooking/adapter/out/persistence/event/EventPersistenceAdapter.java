package com.cielo.flashbooking.adapter.out.persistence.event;

import com.cielo.flashbooking.domain.event.Event;
import com.cielo.flashbooking.event.application.EventWriter;
import org.springframework.stereotype.Repository;

@Repository
class EventPersistenceAdapter implements EventWriter {

    private final EventJpaRepository repository;

    EventPersistenceAdapter(EventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Event save(Event event) {
        EventEntity entity = new EventEntity(
                event.id(), event.name(), event.capacity(), event.available(), event.createdAt());
        repository.save(entity);
        return event;
    }
}
