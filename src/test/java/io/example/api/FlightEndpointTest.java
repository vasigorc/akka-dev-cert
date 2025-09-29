package io.example.api;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import io.example.api.FlightEndpoint.BookingRequest;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import io.example.domain.Participant.ParticipantType;

public class FlightEndpointTest extends TestKitSupport {
  @Test
  public void createBookingAddsReservationOverHttp() {
    String testPilot = "test-pilot";
    String studentId = "sofia";
    String instructorId = "mr-reyes";
    String aircraftId = "xb-abc";
    String bookingId = UUID.randomUUID().toString();
    var booking = new BookingRequest(studentId, aircraftId, instructorId, bookingId);

    var putResponse = httpClient.PUT("/bookings/" + testPilot).withRequestBody(booking).invoke();

    Assertions.assertEquals(StatusCodes.OK, putResponse.status());

    var getResponse = httpClient.GET("/availability/" + testPilot).responseBodyAs(Timeslot.class).invoke();

    Assertions.assertEquals(StatusCodes.OK, getResponse.status());

    var expectedStudent = new Participant(studentId, ParticipantType.STUDENT);
    var expectedInstructor = new Participant(instructorId, ParticipantType.INSTRUCTOR);
    var expectedAircraft = new Participant(aircraftId, ParticipantType.AIRCRAFT);
    var expectedStudentBooking = new Timeslot.Booking(expectedStudent, bookingId);
    var expectedInstructorBooking = new Timeslot.Booking(expectedInstructor, bookingId);
    var expectedAircraftBooking = new Timeslot.Booking(expectedAircraft, bookingId);

    var actualParticipants = getResponse.body().available();

    assertThat(actualParticipants).isEmpty();

    var actualBookings = getResponse.body().bookings();

    assertThat(actualBookings).hasSize(3);
    assertThat(actualBookings)
        .containsExactlyInAnyOrder(expectedStudentBooking, expectedInstructorBooking, expectedAircraftBooking);
  }
}
