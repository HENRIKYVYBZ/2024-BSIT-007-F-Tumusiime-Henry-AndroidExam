package ug.ac.usjm.smartlibrary.data;

/** Thrown when a library rule stops a reservation, e.g. no copies left. The message is shown to the user. */
public class ReservationException extends Exception {
    public ReservationException(String message) {
        super(message);
    }
}
