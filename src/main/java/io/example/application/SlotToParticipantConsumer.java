package io.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.example.domain.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.example.application.ParticipantSlotEntity.*;

// This class is responsible for consuming events from the booking
// slot entity and turning those into command calls on the
// participant slot entity
@ComponentId("booking-slot-consumer")
@Consume.FromEventSourcedEntity(BookingSlotEntity.class)
public class SlotToParticipantConsumer extends Consumer {

  private final ComponentClient client;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public SlotToParticipantConsumer(ComponentClient client) {
    this.client = client;
  }

  public Effect onEvent(BookingEvent event) {
    switch (event) {
      case BookingEvent.ParticipantBooked booked ->
        client.forEventSourcedEntity(participantSlotId(event))
            .method(ParticipantSlotEntity::book)
            .invoke(new Commands.Book(booked.slotId(), booked.participantId(), booked.participantType(),
                booked.bookingId()));
      case BookingEvent.ParticipantCanceled cancelled ->
        client.forEventSourcedEntity(participantSlotId(event))
            .method(ParticipantSlotEntity::cancel)
            .invoke(new Commands.Cancel(cancelled.slotId(), cancelled.participantId(), cancelled.participantType(),
                cancelled.bookingId()));
      case BookingEvent.ParticipantMarkedAvailable participant ->
        client.forEventSourcedEntity(participantSlotId(event))
            .method(ParticipantSlotEntity::markAvailable)
            .invoke(new Commands.MarkAvailable(participant.slotId(), participant.participantId(),
                participant.participantType()));
      case BookingEvent.ParticipantUnmarkedAvailable participant ->
        client.forEventSourcedEntity(participantSlotId(event))
            .method(ParticipantSlotEntity::unmarkAvailable)
            .invoke(new Commands.UnmarkAvailable(participant.slotId(), participant.participantId(),
                participant.participantType()));
    }
    ;

    return effects().done();
  }

  // Participant slots are keyed by a derived key made up of
  // {slotId}-{participantId}
  // We don't need the participant type here because the participant IDs
  // should always be unique/UUIDs
  private String participantSlotId(BookingEvent event) {
    return switch (event) {
      case BookingEvent.ParticipantBooked evt -> evt.slotId() + "-" + evt.participantId();
      case BookingEvent.ParticipantUnmarkedAvailable evt ->
        evt.slotId() + "-" + evt.participantId();
      case BookingEvent.ParticipantMarkedAvailable evt -> evt.slotId() + "-" + evt.participantId();
      case BookingEvent.ParticipantCanceled evt -> evt.slotId() + "-" + evt.participantId();
    };
  }
}
