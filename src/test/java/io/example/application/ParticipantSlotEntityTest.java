package io.example.application;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.*;

import akka.Done;
import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.application.ParticipantSlotEntity.Commands;
import io.example.domain.Participant;
import io.example.domain.Participant.ParticipantAvailabilityStatus;
import io.example.domain.Participant.ParticipantType;

public class ParticipantSlotEntityTest {

  private final String slotId = "slot-id";
  private final String studentId = "liam";
  private Participant studentParticipant;

  @BeforeEach
  void setUp() {
    studentParticipant = new Participant(studentId, ParticipantType.STUDENT);
  }

  @Test
  void testMarkSlotAvailable() {
    var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
    // Given a participant
    // When sending a command to mark slot available for them
    var participantAvailableResult = testKit.method(ParticipantSlotEntity::markAvailable)
        .invoke(
            new Commands.MarkAvailable(slotId, studentParticipant.id(), studentParticipant.participantType()));
    Assertions.assertEquals(Done.getInstance(), participantAvailableResult.getReply());

    // Then current state should point to the participant
    var state = testKit.getState();
    Assertions.assertEquals(slotId, state.slotId());
    Assertions.assertEquals(studentParticipant.id(), state.participantId());
    Assertions.assertEquals(studentParticipant.participantType(), state.participantType());
    Assertions.assertEquals(ParticipantAvailabilityStatus.AVAILABLE.getValue(), state.status());
  }

  @Test
  void testUnmarkSlotAvailable() {
    var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
    // Given an available slot participant
    var participantMarkAvailableResult = testKit.method(ParticipantSlotEntity::markAvailable)
        .invoke(
            new Commands.MarkAvailable(slotId, studentParticipant.id(), studentParticipant.participantType()));
    Assertions.assertEquals(Done.getInstance(), participantMarkAvailableResult.getReply());

    // When sending a command to unmark slot available for them
    testKit.method(ParticipantSlotEntity::unmarkAvailable)
        .invoke(
            new Commands.UnmarkAvailable(slotId, studentParticipant.id(),
                studentParticipant.participantType()));

    // Then current state should point to the participant
    var state = testKit.getState();
    Assertions.assertNull(state);
  }

  @Test
  void testFailToBookWithUnavailableParticipant() {
    var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
    // Given an unavailable slot participant

    // When sending a command to book
    var bookResult = testKit.method(ParticipantSlotEntity::book)
        .invoke(
            new Commands.Book(slotId, studentParticipant.id(),
                studentParticipant.participantType(), UUID.randomUUID().toString()));

    // Then the result should be an error
    Assertions.assertTrue(bookResult.isError());
    Assertions.assertEquals("Requested participant is not available", bookResult.getError());
  }

  @Test
  void testFailToBookAlreadyBookedParticipant() {
    var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
    // Given a booked participant slot
    var participantMarkAvailableResult = testKit.method(ParticipantSlotEntity::markAvailable)
        .invoke(
            new Commands.MarkAvailable(slotId, studentParticipant.id(), studentParticipant.participantType()));
    var bookResult = testKit.method(ParticipantSlotEntity::book)
        .invoke(
            new Commands.Book(slotId, studentParticipant.id(),
                studentParticipant.participantType(), UUID.randomUUID().toString()));

    Set.of(participantMarkAvailableResult, bookResult).forEach(result -> {
      Assertions.assertEquals(Done.getInstance(), result.getReply());
    });

    // When sending a command to book
    var secondBookResult = testKit.method(ParticipantSlotEntity::book)
        .invoke(
            new Commands.Book(slotId, studentParticipant.id(),
                studentParticipant.participantType(), UUID.randomUUID().toString()));

    // Then the result should be an error
    Assertions.assertTrue(secondBookResult.isError());
    Assertions.assertEquals("Requested participant is already booked for the given slot", bookResult.getError());
  }

  @Test
  void testBookBooksAvailableParticipant() {
    var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
    // Given an available slot participant
    var participantMarkAvailableResult = testKit.method(ParticipantSlotEntity::markAvailable)
        .invoke(
            new Commands.MarkAvailable(slotId, studentParticipant.id(), studentParticipant.participantType()));
    Assertions.assertEquals(Done.getInstance(), participantMarkAvailableResult.getReply());

    // When sending a book command
    var bookResult = testKit.method(ParticipantSlotEntity::book)
        .invoke(
            new Commands.Book(slotId, studentParticipant.id(),
                studentParticipant.participantType(), UUID.randomUUID().toString()));

    // Then the command should be processed
    Assertions.assertEquals(Done.getInstance(), bookResult.getReply());

    // And the entity state should be updated accordinlgy
    var state = testKit.getState();
    Assertions.assertEquals(slotId, state.slotId());
    Assertions.assertEquals(studentParticipant.id(), state.participantId());
    Assertions.assertEquals(studentParticipant.participantType(), state.participantType());
    Assertions.assertEquals(ParticipantAvailabilityStatus.BOOKED.getValue(), state.status());
  }

  @Test
  void testFailToCancelUnavailableParticipantSlot() {
    var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
    // Given an unavailable slot participant
    // When sending a cancel command
    var cancelResult = testKit.method(ParticipantSlotEntity::cancel)
        .invoke(
            new Commands.Cancel(slotId, studentParticipant.id(),
                studentParticipant.participantType(), UUID.randomUUID().toString()));

    // Then the command should fail to be processed
    Assertions.assertTrue(cancelResult.isError());
    Assertions.assertEquals("Failed to cancel unavailable participant slot", cancelResult.getError());
  }

  @Test
  void testFailToCancelAvailableButNotBookedParticipantSlot() {
    var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
    // Given an available slot participant
    var participantMarkAvailableResult = testKit.method(ParticipantSlotEntity::markAvailable)
        .invoke(
            new Commands.MarkAvailable(slotId, studentParticipant.id(), studentParticipant.participantType()));
    Assertions.assertEquals(Done.getInstance(), participantMarkAvailableResult.getReply());

    // When sending a cancel command
    var cancelResult = testKit.method(ParticipantSlotEntity::cancel)
        .invoke(
            new Commands.Cancel(slotId, studentParticipant.id(),
                studentParticipant.participantType(), UUID.randomUUID().toString()));

    // Then the command should fail to be processed
    Assertions.assertTrue(cancelResult.isError());
    Assertions.assertEquals("Failed to cancel available but not booked participant slot", cancelResult.getError());
  }

  @Test
  void testCancelBookedParticipantSlot() {
    var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);

    // Given a booked slot participant
    var participantMarkAvailableResult = testKit.method(ParticipantSlotEntity::markAvailable)
        .invoke(
            new Commands.MarkAvailable(slotId, studentParticipant.id(), studentParticipant.participantType()));
    var bookResult = testKit.method(ParticipantSlotEntity::book)
        .invoke(
            new Commands.Book(slotId, studentParticipant.id(),
                studentParticipant.participantType(), UUID.randomUUID().toString()));

    Set.of(participantMarkAvailableResult, bookResult).forEach(result -> {
      Assertions.assertEquals(Done.getInstance(), result.getReply());
    });

    // When sending a cancel command
    var cancelResult = testKit.method(ParticipantSlotEntity::cancel)
        .invoke(
            new Commands.Cancel(slotId, studentParticipant.id(),
                studentParticipant.participantType(), UUID.randomUUID().toString()));

    // Then the command should succeed
    Assertions.assertEquals(Done.getInstance(), cancelResult.getReply());

    // And the state should be updated accordingly
    Assertions.assertNull(testKit.getState());
  }
}
