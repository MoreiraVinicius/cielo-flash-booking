package com.cielo.flashbooking.adapter.out.persistence.event;

import com.cielo.flashbooking.domain.event.Event;
import com.cielo.flashbooking.event.application.EventReader;
import com.cielo.flashbooking.event.application.EventWriter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class EventPersistenceAdapter implements EventWriter, EventReader {

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

    @Override
    @Transactional(readOnly = true)
    public Optional<Event> findById(UUID id) {
        return repository.findById(id).map(EventPersistenceAdapter::toDomain);
    }

    private static Event toDomain(EventEntity entity) {
        return Event.restore(
                entity.id(), entity.name(), entity.capacity(), entity.available(), entity.createdAt());
    }
}
