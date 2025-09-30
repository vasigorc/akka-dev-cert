package io.example.api;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import io.example.api.FlightEndpoint.BookingRequest;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import io.example.domain.Participant.ParticipantType;

public class FlightEndpointTest extends TestKitSupport {

  private final String testPilot = "test-pilot";
  private final String studentId = "sofia";
  private final String instructorId = "mr-reyes";
  private final String aircraftId = "xb-abc";
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
    // Given a valid booking request

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
    // Given an existing booking
    var putResponse = httpClient.PUT("/bookings/" + testPilot).withRequestBody(booking).invoke();

    // When the booking is cancelled via an HTTP request
    var deleteResponse = httpClient.DELETE("/bookings/" + testPilot + "/" + bookingId).invoke();

    // Then the request is successful and the slot state is updated correctly
    Assertions.assertEquals(StatusCodes.OK, deleteResponse.status());

    var getResponse = httpClient.GET("/availability/" + testPilot).responseBodyAs(Timeslot.class).invoke();
    Assertions.assertEquals(StatusCodes.OK, getResponse.status());

    var timeslot = getResponse.body();
    assertThat(timeslot.available()).isEmpty();
    assertThat(timeslot.bookings()).isEmpty();
  }

  @Test
  public void slotsByStatusReturnsAllSlotsForParticipantStatusCombinationOverHttp() {
    fail();
  }
}
