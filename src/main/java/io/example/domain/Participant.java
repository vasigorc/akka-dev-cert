package io.example.domain;

// A tuple-style class that holds a participant ID and the corresponding
// type: student, instructor, or aircraft.
public record Participant(String id, ParticipantType participantType) {
  public enum ParticipantType {
    STUDENT,
    INSTRUCTOR,
    AIRCRAFT
  }

  public enum ParticipantAvailabilityStatus {
    BOOKED("booked"),
    AVAILABLE("available");

    private final String value;

    private ParticipantAvailabilityStatus(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }
}
