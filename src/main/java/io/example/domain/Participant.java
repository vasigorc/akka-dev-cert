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
    BOOKED,
    AVAILABLE,
    UNAVAILABLE;

    public String getValue() {
      return this.name().toLowerCase();
    }
  }
}
