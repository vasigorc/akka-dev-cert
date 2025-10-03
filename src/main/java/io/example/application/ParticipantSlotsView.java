package io.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.example.application.ParticipantSlotEntity.Event;
import io.example.domain.Participant.ParticipantAvailabilityStatus;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("view-participant-slots")
public class ParticipantSlotsView extends View {

  private static Logger logger = LoggerFactory.getLogger(ParticipantSlotsView.class);

  @Consume.FromEventSourcedEntity(ParticipantSlotEntity.class)
  public static class ParticipantSlotsViewUpdater extends TableUpdater<SlotRow> {

    public Effect<SlotRow> onEvent(ParticipantSlotEntity.Event event) {
      return switch (event) {
        case Event.MarkedAvailable marked ->
          effects().updateRow(new SlotRow(marked.slotId(), marked.participantId(), marked.participantType().name(),
              "", ParticipantAvailabilityStatus.AVAILABLE.getValue()));
        case Event.UnmarkedAvailable unmarked ->
          effects()
              .updateRow(new SlotRow(unmarked.slotId(), unmarked.participantId(), unmarked.participantType().name(),
                  rowState().bookingId(), ParticipantAvailabilityStatus.UNAVAILABLE.getValue()));
        case Event.Booked booked ->
          effects()
              .updateRow(new SlotRow(booked.slotId(), booked.participantId(), booked.participantType().name(),
                  booked.bookingId(), ParticipantAvailabilityStatus.UNAVAILABLE.getValue()));
        case Event.Canceled canceled ->
          effects()
              .updateRow(new SlotRow(canceled.slotId(), canceled.participantId(), canceled.participantType().name(),
                  canceled.bookingId(), ParticipantAvailabilityStatus.AVAILABLE.getValue()));
      };
    }
  }

  public record SlotRow(
      String slotId,
      String participantId,
      String participantType,
      String bookingId,
      String status) {
  }

  public record ParticipantStatusInput(String participantId, String status) {
  }

  public record SlotList(List<SlotRow> slots) {
  }

  @Query("SELECT * AS slots FROM  view_participant_slots WHERE participantId = :participantId")
  public QueryEffect<SlotList> getSlotsByParticipant(String participantId) {
    return queryResult();
  }

  @Query("""
      SELECT * AS slots FROM view_participant_slots
      WHERE participantId = :participantId
      AND status = :status
      """)
  public QueryEffect<SlotList> getSlotsByParticipantAndStatus(ParticipantStatusInput input) {
    return queryResult();
  }
}
