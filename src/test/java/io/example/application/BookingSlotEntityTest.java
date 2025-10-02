package io.example.application;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import akka.Done;
import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.domain.Participant;
import io.example.domain.Participant.ParticipantType;
import io.example.application.BookingSlotEntity.Command;

public class BookingSlotEntityTest {

  private final String studentId = "liam";
  private final String instructorId = "mr-delgado";
  private final String aircraftId = "piper-pa-28";
  private Participant studentParticipant;
  private Participant instructorParticipant;
  private Participant aircraftParticipant;

  @BeforeEach
  void setUp() {
    studentParticipant = new Participant(studentId, ParticipantType.STUDENT);
    instructorParticipant = new Participant(instructorId, ParticipantType.INSTRUCTOR);
    aircraftParticipant = new Participant(aircraftId, ParticipantType.AIRCRAFT);
  }

  @Test
  void testFailToBookWithUnavailableParticipant() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    // Given available student and instructor participants
    // And unavailable aircraft participatn
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
  void testBookWhenRequestedParticipantsAreAvailable() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    // Given all available participants (student, instructor, and aircraft)
    var studentAvailableResult = testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(studentParticipant));
    var instructorAvailableResult = testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(instructorParticipant));
    var aircraftAvailableResult = testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(aircraftParticipant));
    Set.of(studentAvailableResult, instructorAvailableResult, aircraftAvailableResult).forEach(result -> {
      Assertions.assertEquals(Done.getInstance(), result.getReply());
    });
    // When booking a slot
    var expectedBookingId = UUID.randomUUID().toString();
    var bookingResult = testKit.method(BookingSlotEntity::bookSlot).invoke(new Command.BookReservation(
        studentParticipant.id(), aircraftParticipant.id(), instructorParticipant.id(), expectedBookingId));

    // Then the command should succeed
    Assertions.assertEquals(Done.getInstance(), bookingResult.getReply());

    // And current state should include booking for all three participants
    // with the expected booking id
    var state = testKit.getState();
    var expectedParticipants = Set.of(studentParticipant, instructorParticipant, aircraftParticipant);
    assertThat(state.bookings()).hasSize(expectedParticipants.size());
    state.bookings().forEach(booking -> {
      assertThat(expectedParticipants).contains(booking.participant());
      Assertions.assertEquals(expectedBookingId, booking.bookingId());
    });
  }

  @Test
  void testMarkSlotAvailable() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);
    // Given a participant

    // When sending a command to mark slot available for it
    var aircraftAvailableResult = testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(aircraftParticipant));
    Assertions.assertEquals(Done.getInstance(), aircraftAvailableResult.getReply());

    // Then current state should include this participant
    var state = testKit.getState();
    assertThat(state.available()).containsExactly(aircraftParticipant);
  }

  @Test
  void testUnmarkSlotAvailable() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);
    // Given an available participant
    var studentAvailableResult = testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(studentParticipant));
    Assertions.assertEquals(Done.getInstance(), studentAvailableResult.getReply());

    // When sendinga command to unmark slot available for them
    var unmarkSlotResult = testKit.method(BookingSlotEntity::unmarkSlotAvailable)
        .invoke(new Command.UnmarkSlotAvailable(studentParticipant));
    Assertions.assertEquals(Done.getInstance(), unmarkSlotResult.getReply());

    // Then the current state should not include this participant
    var state = testKit.getState();
    assertThat(state.available()).isEmpty();
  }

  @Test
  void testCancelBookingRemovesThreeBookings() {
    var testKit = EventSourcedTestKit.of(BookingSlotEntity::new);

    // Given three booked participants on a clean slate slot
    testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(studentParticipant));
    testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(instructorParticipant));
    testKit.method(BookingSlotEntity::markSlotAvailable)
        .invoke(new Command.MarkSlotAvailable(aircraftParticipant));
    var expectedBookingId = UUID.randomUUID().toString();
    testKit.method(BookingSlotEntity::bookSlot).invoke(new Command.BookReservation(
        studentParticipant.id(), aircraftParticipant.id(), instructorParticipant.id(), expectedBookingId));

    // When cancelling the respective booking
    var cancelBookingResult = testKit.method(BookingSlotEntity::cancelBooking).invoke(expectedBookingId);

    // Then the command should succeed
    Assertions.assertEquals(Done.getInstance(), cancelBookingResult.getReply());

    // And the state shouldn't include any bookings
    var state = testKit.getState();
    assertThat(state.bookings()).isEmpty();

    // And all participants are not automatically marked as available
    assertThat(state.available()).isEmpty();
  }
}
