package io.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import io.example.domain.Participant.ParticipantType;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

  private final String entityId;
  private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

  public BookingSlotEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
    return effects().persist(
        new BookingEvent.ParticipantMarkedAvailable(entityId, cmd.participant.id(), cmd.participant.participantType()))
        .thenReply(newState -> Done.getInstance());
  }

  public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
    return effects().persist(new BookingEvent.ParticipantUnmarkedAvailable(entityId, cmd.participant.id(),
        cmd.participant.participantType())).thenReply(newState -> Done.getInstance());
  }

  // NOTE: booking a slot should produce 3
  // `ParticipantBooked` events
  public Effect<Done> bookSlot(Command.BookReservation cmd) {
    var isAllParticipantAvailable = currentState().available()
        .containsAll(Set.of(new Participant(cmd.studentId, ParticipantType.STUDENT),
            new Participant(cmd.instructorId, ParticipantType.INSTRUCTOR),
            new Participant(cmd.aircraftId, ParticipantType.AIRCRAFT)));
    if (!isAllParticipantAvailable) {
      return effects().error("Not all of the requested participants are available for the training flight");
    }
    return effects()
        .persist(new BookingEvent.ParticipantBooked(entityId, cmd.studentId, ParticipantType.STUDENT, cmd.bookingId),
            new BookingEvent.ParticipantBooked(entityId, cmd.instructorId, ParticipantType.INSTRUCTOR, cmd.bookingId),
            new BookingEvent.ParticipantBooked(entityId, cmd.aircraftId, ParticipantType.AIRCRAFT, cmd.bookingId))
        .thenReply(newState -> Done.getInstance());
  }

  // NOTE: canceling a booking should produce 3
  // `ParticipantCanceled` events
  public Effect<Done> cancelBooking(String bookingId) {
    logger.info("Cancelling booking {}", bookingId);
    var cancelRelatedParticipantsEvents = currentState().findBooking(bookingId).stream()
        .map(booking -> booking.participant())
        .map(participant -> new BookingEvent.ParticipantCanceled(entityId, participant.id(),
            participant.participantType(), bookingId))
        .collect(Collectors.toList());
    return effects().persistAll(cancelRelatedParticipantsEvents).thenReply(newState -> Done.getInstance());
  }

  public ReadOnlyEffect<Timeslot> getSlot() {
    return effects().reply(currentState());
  }

  @Override
  public Timeslot emptyState() {
    return new Timeslot(
        // NOTE: these are just estimates for capacity based on it being a sample
        HashSet.newHashSet(10), HashSet.newHashSet(10));
  }

  @Override
  public Timeslot applyEvent(BookingEvent event) {
    return switch (event) {
      case BookingEvent.ParticipantBooked booked -> currentState().book(booked);
      case BookingEvent.ParticipantCanceled cancelled -> currentState().cancelBooking(cancelled.bookingId());
      case BookingEvent.ParticipantMarkedAvailable participant -> currentState().reserve(participant);
      case BookingEvent.ParticipantUnmarkedAvailable participant -> currentState().unreserve(participant);
    };
  }

  public sealed interface Command {
    record MarkSlotAvailable(Participant participant) implements Command {
    }

    record UnmarkSlotAvailable(Participant participant) implements Command {
    }

    record BookReservation(
        String studentId, String aircraftId, String instructorId, String bookingId)
        implements Command {
    }
  }
}
