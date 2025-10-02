package io.example.api;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import io.example.api.FlightEndpoint.*;
import io.example.application.ParticipantSlotsView.SlotList;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import io.example.domain.Participant.ParticipantAvailabilityStatus;
import io.example.domain.Participant.ParticipantType;

public class FlightEndpointIT extends TestKitSupport {

  private final String testPilot = "test-pilot";
  private final String studentId = "sofia";
  private final String instructorId = "mr-reyes";
  private final String aircraftId = "cenizo";
  private String bookingId;
  private BookingRequest booking;
  private Timeslot.Booking expectedStudentBooking;
  private Timeslot.Booking expectedInstructorBooking;
  private Timeslot.Booking expectedAircraftBooking;

  @BeforeEach
  void setUp() {
    bookingId = UUID.randomUUID().toString();
    booking = new BookingRequest(studentId, aircraftId, instructorId, bookingId);
    var expectedStudent = new Participant(studentId, ParticipantType.STUDENT);
    var expectedInstructor = new Participant(instructorId, ParticipantType.INSTRUCTOR);
    var expectedAircraft = new Participant(aircraftId, ParticipantType.AIRCRAFT);
    expectedStudentBooking = new Timeslot.Booking(expectedStudent, bookingId);
    expectedInstructorBooking = new Timeslot.Booking(expectedInstructor, bookingId);
    expectedAircraftBooking = new Timeslot.Booking(expectedAircraft, bookingId);
  }

  @Test
  public void createBookingAddsReservationOverHttp() {
    // Given available participants
    var studentAvailableResponse = httpClient.POST("/availability/" + testPilot)
        .withRequestBody(new AvailabilityRequest(studentId, ParticipantType.STUDENT.name())).invoke();
    var instructorAvailableResponse = httpClient.POST("/availability/" + testPilot)
        .withRequestBody(new AvailabilityRequest(instructorId, ParticipantType.INSTRUCTOR.name())).invoke();
    var aircraftAvailableResponse = httpClient.POST("/availability/" + testPilot)
        .withRequestBody(new AvailabilityRequest(aircraftId, ParticipantType.AIRCRAFT.name())).invoke();
    List.of(studentAvailableResponse, instructorAvailableResponse, aircraftAvailableResponse).forEach(response -> {
      Assertions.assertEquals(StatusCodes.OK, response.status());
    });

    // And a valid booking request

    // When the booking is created via an HTTP request
    var putResponse = httpClient.PUT("/bookings/" + testPilot).withRequestBody(booking).invoke();

    // Then the request is successful and the slot state is updated correctly
    Assertions.assertEquals(StatusCodes.OK, putResponse.status());

    var getResponse = httpClient.GET("/availability/" + testPilot).responseBodyAs(Timeslot.class).invoke();
    Assertions.assertEquals(StatusCodes.OK, getResponse.status());

    var timeslot = getResponse.body();
    assertThat(timeslot.available()).isEmpty();
    assertThat(timeslot.bookings()).hasSize(3);
    assertThat(timeslot.bookings())
        .containsExactlyInAnyOrder(expectedStudentBooking, expectedInstructorBooking, expectedAircraftBooking);
  }

  @Test
  public void cancelBookingRemovesBookingsOverHttp() {
    // Given available participants
    var studentAvailableResponse = httpClient.POST("/availability/" + testPilot)
        .withRequestBody(new AvailabilityRequest(studentId, ParticipantType.STUDENT.name())).invoke();
    var instructorAvailableResponse = httpClient.POST("/availability/" + testPilot)
        .withRequestBody(new AvailabilityRequest(instructorId, ParticipantType.INSTRUCTOR.name())).invoke();
    var aircraftAvailableResponse = httpClient.POST("/availability/" + testPilot)
        .withRequestBody(new AvailabilityRequest(aircraftId, ParticipantType.AIRCRAFT.name())).invoke();
    List.of(studentAvailableResponse, instructorAvailableResponse, aircraftAvailableResponse).forEach(postResponse -> {
      Assertions.assertEquals(StatusCodes.OK, postResponse.status());
    });

    // And an existing booking
    httpClient.PUT("/bookings/" + testPilot).withRequestBody(booking).invoke();

    // When the booking is cancelled via an HTTP request
    var deleteResponse = httpClient.DELETE("/bookings/" + testPilot + "/" + bookingId).invoke();

    // Then the request is successful
    Assertions.assertEquals(StatusCodes.OK, deleteResponse.status());

    var getResponse = httpClient.GET("/availability/" + testPilot).responseBodyAs(Timeslot.class).invoke();
    Assertions.assertEquals(StatusCodes.OK, getResponse.status());

    // And the slot state is updated correctly
    var timeslot = getResponse.body();
    assertThat(timeslot.available()).isEmpty();
    assertThat(timeslot.bookings()).isEmpty();
  }

  @Test
  public void slotsForAvailableStatusReturnsAllSlotsForSingleParticipantOverHttp() {
    // Given the same available participant across two slots
    String localFlight = "local-flight";
    var instructorAvailableForTestPilotResponse = httpClient.POST("/availability/" + testPilot)
        .withRequestBody(new AvailabilityRequest(instructorId, ParticipantType.INSTRUCTOR.name())).invoke();
    var instructorAvailableForLocalFlightResponse = httpClient.POST("/availability/" + localFlight)
        .withRequestBody(new AvailabilityRequest(instructorId, ParticipantType.INSTRUCTOR.name())).invoke();
    List.of(instructorAvailableForTestPilotResponse, instructorAvailableForLocalFlightResponse)
        .forEach(postResponse -> {
          Assertions.assertEquals(StatusCodes.OK, postResponse.status());
        });

    // When searching for availalable slots for the given instuctor
    var getResponse = httpClient
        .GET("/slots/" + instructorId + "/" + ParticipantAvailabilityStatus.AVAILABLE.getValue())
        .responseBodyAs(SlotList.class).invoke();

    // Then the request is successful
    Assertions.assertEquals(StatusCodes.OK, getResponse.status());

    var actualSlots = getResponse.body().slots();
    // And the number of available slots should be two
    assertThat(actualSlots).hasSize(2);

    // And slot rows should match the participant's type, id and status
    actualSlots.forEach(slotRow -> {
      Assertions.assertEquals(ParticipantType.INSTRUCTOR.name(), slotRow.participantType());
      Assertions.assertEquals(instructorId, slotRow.participantId());
      Assertions.assertEquals(ParticipantAvailabilityStatus.AVAILABLE.getValue(), slotRow.status());
    });
  }

  @Test
  public void markAvailableMarksParticipantAvailableForSlotOverHttp() {
    // Given a student participant

    // When marking them as available
    var postResponse = httpClient.POST("/availability/" + testPilot)
        .withRequestBody(new AvailabilityRequest(studentId, ParticipantType.STUDENT.name())).invoke();
    Assertions.assertEquals(StatusCodes.OK, postResponse.status());

    // Then the given participant should be retrievable through Timeslot information
    var getResponse = httpClient.GET("/availability/" + testPilot).responseBodyAs(Timeslot.class).invoke();
    Assertions.assertEquals(StatusCodes.OK, getResponse.status());
    var actualTimeslot = getResponse.body();
    assertThat(actualTimeslot.available()).containsExactly(new Participant(studentId, ParticipantType.STUDENT));
  }

  @Test
  public void unmarkAvailableUnmarksParticipantAvailabilityForSlotOverhttp() {
    // Given a student participant with an availability within a slot
    var postResponse = httpClient.POST("/availability/" + testPilot)
        .withRequestBody(new AvailabilityRequest(studentId, ParticipantType.STUDENT.name())).invoke();
    Assertions.assertEquals(StatusCodes.OK, postResponse.status());

    // When unmarking them as available
    var deleteResponse = httpClient.DELETE("/availability/" + testPilot)
        .withRequestBody(new AvailabilityRequest(studentId, ParticipantType.STUDENT.name())).invoke();

    // Then the request is successful
    Assertions.assertEquals(StatusCodes.OK, deleteResponse.status());

    // And the given participant should no longer be retrievable through Timeslot
    // information
    var getResponse = httpClient.GET("/availability/" + testPilot).responseBodyAs(Timeslot.class).invoke();
    Assertions.assertEquals(StatusCodes.OK, getResponse.status());
    var actualTimeslot = getResponse.body();
    assertThat(actualTimeslot.available()).isEmpty();
  }
}
