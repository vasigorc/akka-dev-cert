package io.example.application;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import akka.Done;
import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.domain.Participant;
import io.example.domain.Participant.ParticipantType;
import io.example.application.BookingSlotEntity.Command;

public class BookingSlotEntityTest {
  @Test
  void testFailToBookWithUnavailableParticipant() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    // Given available student and instructor participants
    // And unavailable aircraft participatn
    var studentParticipant = new Participant("liam", ParticipantType.STUDENT);
    var instructorParticipant = new Participant("mr-delgado", ParticipantType.INSTRUCTOR);
    var studentAvailableResult = testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(studentParticipant));
    var instructorAvailableResult = testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(instructorParticipant));
    Set.of(studentAvailableResult, instructorAvailableResult).forEach(result -> {
      Assertions.assertEquals(Done.getInstance(), result.getReply());
    });

    // When booking a slot
    var unavailableAircraftId = "unavailable";
    var bookingResult = testKit.method(BookingSlotEntity::bookSlot).invoke(new Command.BookReservation(
        studentParticipant.id(), unavailableAircraftId, instructorParticipant.id(), UUID.randomUUID().toString()));

    // Then the command should fail
    Assertions.assertTrue(bookingResult.isError());
    Assertions.assertEquals("Not all of the requested participants are available for the training flight",
        bookingResult.getError());
  }

  @Test
  void testMarkSlotAvailable() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);
    // Given a participant
    var aircraftParticipant = new Participant("piper-pa-28", ParticipantType.AIRCRAFT);

    // When sending a command to mark slot available for it
    var aircraftAvailableResult = testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(aircraftParticipant));
    Assertions.assertEquals(Done.getInstance(), aircraftAvailableResult.getReply());

    // Then current state should include this participant
    var getStateResult = testKit.method(BookingSlotEntity::getSlot).invoke();
    Assertions.assertEquals(Done.getInstance(), getStateResult.getReply());
    var state = testKit.getState();
    assertThat(state.available()).containsExactly(aircraftParticipant);
  }
}
