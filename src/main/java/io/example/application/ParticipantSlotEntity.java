package io.example.application;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.example.domain.Participant.ParticipantAvailabilityStatus;
import io.example.domain.Participant.ParticipantType;

@ComponentId("participant-slot")
public class ParticipantSlotEntity
    extends EventSourcedEntity<ParticipantSlotEntity.State, ParticipantSlotEntity.Event> {
  private static final Logger logger = LoggerFactory.getLogger(ParticipantSlotEntity.class);

  public Effect<Done> unmarkAvailable(ParticipantSlotEntity.Commands.UnmarkAvailable unmark) {
    if (currentState() == null) {
      logger.warn("Command to unmark unavailable participant {} for slot {} skipped",
          unmark.participantId(), unmark.slotId());
      return effects().reply(Done.done());
    }

    logger.info("Unmarking availability for participant {} and slot {}", currentState().participantId(),
        currentState().slotId());
    return effects()
        .persist(new Event.UnmarkedAvailable(unmark.slotId(), unmark.participantId(), unmark.participantType()))
        .thenReply(newState -> Done.getInstance());
  }

  public Effect<Done> markAvailable(ParticipantSlotEntity.Commands.MarkAvailable mark) {
    if (isUnavailable()) {
      return effects().persist(new Event.MarkedAvailable(mark.slotId(), mark.participantId(), mark.participantType()))
          .thenReply(newState -> Done.getInstance());
    }

    logger.warn("Command to mark participant slot available that was already in {} status skipped",
        currentState().status());
    return effects().reply(Done.done());
  }

  public Effect<Done> book(ParticipantSlotEntity.Commands.Book book) {
    if (isUnavailable()) {
      return effects().error("Requested participant is not available");
    }

    if (ParticipantAvailabilityStatus.BOOKED.getValue().equalsIgnoreCase(currentState().status())) {
      return effects().error("Requested participant is already booked for the given slot");
    }

    return effects()
        .persist(new Event.Booked(book.slotId(), book.participantId(), book.participantType(), book.bookingId()))
        .thenReply(newState -> Done.getInstance());
  }

  private boolean isUnavailable() {
    return currentState() == null
        || ParticipantAvailabilityStatus.UNAVAILABLE.getValue().equalsIgnoreCase(currentState().status());
  }

  public Effect<Done> cancel(ParticipantSlotEntity.Commands.Cancel cancel) {
    if (isUnavailable()) {
      return effects().error("Failed to cancel unavailable participant slot");
    }

    if (!currentState().status().equalsIgnoreCase(ParticipantAvailabilityStatus.BOOKED.getValue())) {
      logger.error("Cancelled event ignored for non booked participant {} for slot {}", cancel.participantId(),
          cancel.slotId());
      return effects().error("Failed to cancel available but not booked participant slot");
    }

    return effects().persist(
        new Event.Canceled(cancel.slotId(), cancel.participantId(), cancel.participantType(), cancel.bookingId()))
        .thenReply(newState -> Done.getInstance());
  }

  record State(
      String slotId, String participantId, ParticipantType participantType, String status) {
    public State withStatus(String status) {
      return new State(this.slotId, this.participantId, this.participantType, status);
    }
  }

  public sealed interface Commands {
    record MarkAvailable(String slotId, String participantId, ParticipantType participantType)
        implements Commands {
    }

    record UnmarkAvailable(String slotId, String participantId, ParticipantType participantType)
        implements Commands {
    }

    record Book(
        String slotId, String participantId, ParticipantType participantType, String bookingId)
        implements Commands {
    }

    record Cancel(
        String slotId, String participantId, ParticipantType participantType, String bookingId)
        implements Commands {
    }
  }

  public sealed interface Event {
    @TypeName("marked-available")
    record MarkedAvailable(String slotId, String participantId, ParticipantType participantType)
        implements Event {
    }

    @TypeName("unmarked-available")
    record UnmarkedAvailable(String slotId, String participantId, ParticipantType participantType)
        implements Event {
    }

    @TypeName("participant-booked")
    record Booked(
        String slotId, String participantId, ParticipantType participantType, String bookingId)
        implements Event {
    }

    @TypeName("participant-canceled")
    record Canceled(
        String slotId, String participantId, ParticipantType participantType, String bookingId)
        implements Event {
    }
  }

  @Override
  public ParticipantSlotEntity.State applyEvent(ParticipantSlotEntity.Event event) {
    return switch (event) {
      case Event.MarkedAvailable marked -> new State(
          marked.slotId(), marked.participantId(), marked.participantType(),
          ParticipantAvailabilityStatus.AVAILABLE.getValue());
      case Event.UnmarkedAvailable unmarked ->
        currentState().withStatus(ParticipantAvailabilityStatus.UNAVAILABLE.getValue());
      case Event.Booked booked -> currentState().withStatus(ParticipantAvailabilityStatus.BOOKED.getValue());
      case Event.Canceled canceled -> currentState().withStatus(ParticipantAvailabilityStatus.AVAILABLE.getValue());
    };
  }
}
