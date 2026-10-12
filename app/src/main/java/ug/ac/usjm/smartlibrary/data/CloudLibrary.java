package ug.ac.usjm.smartlibrary.data;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ug.ac.usjm.smartlibrary.util.Loans;
import ug.ac.usjm.smartlibrary.util.Roles;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * The shared library data in Cloud Firestore, used by everyone's phone:
 *   books/{bookId}      - the catalogue with total and available copies
 *   reservations/{id}   - every hold and loan (see {@link Reservation} for its life)
 *
 * Every change to copy counts runs in a Firestore transaction: if two people reserve the last
 * copy at the same moment, only one succeeds. The security rules (firestore.rules) decide who may
 * change what, e.g. only librarians can mark a book Collected or Returned.
 * Callbacks run on the main thread.
 */
public final class CloudLibrary {

    public interface Result<T> {
        void onSuccess(T value);

        void onError(String message);
    }

    /** Receives live updates until the returned registration is removed. */
    public interface Listener<T> {
        void onChange(T value);

        void onError(String message);
    }

    private CloudLibrary() {
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static CollectionReference books() {
        return db().collection("books");
    }

    private static CollectionReference reservations() {
        return db().collection("reservations");
    }

    private static DocumentReference bookRef(long bookId) {
        return books().document(String.valueOf(bookId));
    }

    // =================================================================== catalogue

    static Book bookFrom(DocumentSnapshot d) {
        long id;
        try {
            id = Long.parseLong(d.getId());
        } catch (NumberFormatException e) {
            return null;   // not one of ours
        }
        return new Book(id, text(d, "title"), text(d, "author"), text(d, "category"), text(d, "shelf"),
                (int) number(d, "year"), text(d, "description"), (int) number(d, "totalCopies"),
                (int) number(d, "availableCopies"));
    }

    private static String text(DocumentSnapshot d, String field) {
        String s = d.getString(field);
        return s == null ? "" : s;
    }

    private static long number(DocumentSnapshot d, String field) {
        Long n = d.getLong(field);
        return n == null ? 0 : n;
    }

    /** Live copy of the shared catalogue (an empty list means no librarian has published one yet). */
    public static ListenerRegistration listenToCatalogue(final Listener<List<Book>> listener) {
        // MetadataChanges.INCLUDE: we also hear when the server confirms the cached answer.
        return books().addSnapshotListener(MetadataChanges.INCLUDE, (snap, error) -> {
            if (error != null) {
                listener.onError(CloudMessages.from(error));
                return;
            }
            // An empty answer from the phone's cache only means "not downloaded yet": wait for the server.
            if (snap.isEmpty() && snap.getMetadata().isFromCache()) return;
            List<Book> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Book b = bookFrom(d);
                if (b != null) list.add(b);
            }
            listener.onChange(list);
        });
    }

    /**
     * Librarians: makes {@code source} the shared catalogue (sample books or the web system's books).
     * Copies already held or on loan are taken off each book's available count. Books that are no
     * longer listed are removed unless someone still holds or borrows them.
     */
    public static void publishCatalogue(final List<Book> source, final Result<Integer> result) {
        countHeldCopies(new Result<Map<Long, Integer>>() {
            @Override
            public void onSuccess(final Map<Long, Integer> held) {
                books().get().addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        result.onError(CloudMessages.from(task.getException()));
                        return;
                    }
                    Set<Long> keep = new HashSet<>();
                    WriteBatch batch = db().batch();
                    long now = System.currentTimeMillis();
                    for (Book b : source) {
                        int out = held.containsKey(b.id) ? held.get(b.id) : 0;
                        int available = Math.max(0, Math.min(b.availableCopies, b.totalCopies) - out);
                        Map<String, Object> m = new HashMap<>();
                        m.put("title", b.title);
                        m.put("author", b.author);
                        m.put("category", b.category);
                        m.put("shelf", b.shelf);
                        m.put("year", (long) b.year);
                        m.put("description", b.description);
                        m.put("totalCopies", (long) b.totalCopies);
                        m.put("availableCopies", (long) available);
                        m.put("updatedAt", now);
                        batch.set(bookRef(b.id), m);
                        keep.add(b.id);
                    }
                    for (DocumentSnapshot d : task.getResult().getDocuments()) {
                        Book old = bookFrom(d);
                        if (old != null && !keep.contains(old.id) && !held.containsKey(old.id)) {
                            batch.delete(d.getReference());
                        }
                    }
                    batch.commit().addOnCompleteListener(done -> {
                        if (done.isSuccessful()) result.onSuccess(source.size());
                        else result.onError(CloudMessages.from(done.getException()));
                    });
                });
            }

            @Override
            public void onError(String message) {
                result.onError(message);
            }
        });
    }

    /** bookId -> copies that are held (ACTIVE) or on loan (COLLECTED) right now. */
    private static void countHeldCopies(final Result<Map<Long, Integer>> result) {
        final Map<Long, Integer> held = new HashMap<>();
        reservations().whereEqualTo("status", Reservation.ACTIVE).get().addOnCompleteListener(first -> {
            if (!first.isSuccessful()) {
                result.onError(CloudMessages.from(first.getException()));
                return;
            }
            add(held, first.getResult());
            reservations().whereEqualTo("status", Reservation.COLLECTED).get().addOnCompleteListener(second -> {
                if (!second.isSuccessful()) {
                    result.onError(CloudMessages.from(second.getException()));
                    return;
                }
                add(held, second.getResult());
                result.onSuccess(held);
            });
        });
    }

    private static void add(Map<Long, Integer> held, QuerySnapshot snap) {
        for (DocumentSnapshot d : snap.getDocuments()) {
            Long bookId = d.getLong("bookId");
            if (bookId == null) continue;
            Integer n = held.get(bookId);
            held.put(bookId, n == null ? 1 : n + 1);
        }
    }

    // ================================================================ reservations

    /**
     * Reserves a copy for the signed-in person. Checks their limits first, then takes the copy in
     * a transaction so the last copy can never be given to two people.
     *
     * @param maxActive how many holds this person's role allows (see Roles.maxActiveReservations)
     */
    public static void reserve(final Book book, final UserProfile person, final String name, final String idNumber,
                               final String pickupDate, final int maxActive, final Result<Reservation> result) {
        reservations().whereEqualTo("userUid", person.uid).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                result.onError(CloudMessages.from(task.getException()));
                return;
            }
            int active = 0;
            for (DocumentSnapshot d : task.getResult().getDocuments()) {
                Reservation r = Reservation.from(d);
                if (r.bookId == book.id && (r.isActive() || r.isOnLoan())) {
                    result.onError("You already have \"" + book.title + "\" reserved or on loan.");
                    return;
                }
                if (r.isActive()) active++;
            }
            if (active >= maxActive) {
                result.onError("You can hold at most " + maxActive
                        + " reservations at a time. Cancel one in My reservations first.");
                return;
            }

            final DocumentReference newRef = reservations().document();
            final long now = System.currentTimeMillis();
            final String role = person.effectiveRole();
            db().runTransaction(t -> {
                DocumentSnapshot b = t.get(bookRef(book.id));
                if (!b.exists()) {
                    return "This book is not in the shared catalogue yet. Ask the librarian to publish the catalogue.";
                }
                long available = number(b, "availableCopies");
                if (available <= 0) {
                    return "All copies of \"" + book.title + "\" are out right now. Please try again later.";
                }
                // lastReservation lets the security rules check that a copy is only taken for a new hold.
                t.update(bookRef(book.id), "availableCopies", available - 1, "updatedAt", now,
                        "lastReservation", newRef.getId());
                Map<String, Object> m = new HashMap<>();
                m.put("bookId", book.id);
                m.put("bookTitle", text(b, "title"));
                m.put("shelf", text(b, "shelf"));
                m.put("userUid", person.uid);
                m.put("userName", name);
                m.put("idNumber", idNumber);
                m.put("role", role);
                m.put("pickupDate", pickupDate);
                m.put("status", Reservation.ACTIVE);
                m.put("createdAt", now);
                m.put("updatedAt", now);
                t.set(newRef, m);
                return null;   // no problem
            }).addOnCompleteListener(done -> {
                if (!done.isSuccessful()) {
                    result.onError(CloudMessages.from(done.getException()));
                } else if (done.getResult() != null) {
                    result.onError(done.getResult());
                } else {
                    result.onSuccess(new Reservation(newRef.getId(), book.id, book.title, book.shelf, person.uid,
                            name, idNumber, role, pickupDate, Reservation.ACTIVE, now, null, null));
                }
            });
        });
    }

    /** Live list of one person's reservations: active first, then on loan, then newest first. */
    public static ListenerRegistration listenToMine(String uid, final Listener<List<Reservation>> listener) {
        return reservations().whereEqualTo("userUid", uid).addSnapshotListener((snap, error) -> {
            if (error != null) {
                listener.onError(CloudMessages.from(error));
                return;
            }
            List<Reservation> list = toList(snap);
            Collections.sort(list, (a, b) -> {
                int ra = rank(a), rb = rank(b);
                if (ra != rb) return ra - rb;
                return Long.compare(b.createdAt, a.createdAt);
            });
            listener.onChange(list);
        });
    }

    private static int rank(Reservation r) {
        if (r.isActive()) return 0;
        if (r.isOnLoan()) return 1;
        return 2;
    }

    /** Librarians: live list of every reservation with this status (e.g. ACTIVE or COLLECTED). */
    public static ListenerRegistration listenToStatus(String status, final Listener<List<Reservation>> listener) {
        return reservations().whereEqualTo("status", status).addSnapshotListener((snap, error) -> {
            if (error != null) {
                listener.onError(CloudMessages.from(error));
                return;
            }
            listener.onChange(toList(snap));
        });
    }

    private static List<Reservation> toList(QuerySnapshot snap) {
        List<Reservation> list = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            list.add(Reservation.from(d));
        }
        return list;
    }

    /** The person cancels their own hold; the copy goes back on the shelf. */
    public static void cancel(Reservation r, Result<Void> result) {
        changeStatus(r, Reservation.ACTIVE, Reservation.CANCELLED, true, null, result);
    }

    /** A hold whose pickup day passed without collection; the copy goes back on the shelf. */
    public static void expire(Reservation r, Result<Void> result) {
        changeStatus(r, Reservation.ACTIVE, Reservation.EXPIRED, true, null, result);
    }

    /** Librarians: the person collected the book; it is now on loan with a due date. */
    public static void markCollected(Reservation r, String librarian, Result<Void> result) {
        String today = Validator.today();
        Map<String, Object> extra = new HashMap<>();
        extra.put("collectedOn", today);
        extra.put("dueDate", Loans.dueDate(today, Roles.loanDays(r.role, true)));
        extra.put("handledBy", librarian);
        changeStatus(r, Reservation.ACTIVE, Reservation.COLLECTED, false, extra, result);
    }

    /** Librarians: the person did not come; the copy goes back on the shelf. */
    public static void markNoShow(Reservation r, String librarian, Result<Void> result) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("handledBy", librarian);
        changeStatus(r, Reservation.ACTIVE, Reservation.NO_SHOW, true, extra, result);
    }

    /** Librarians: the book came back; the copy goes back on the shelf. */
    public static void markReturned(Reservation r, String librarian, Result<Void> result) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("returnedOn", Validator.today());
        extra.put("handledBy", librarian);
        changeStatus(r, Reservation.COLLECTED, Reservation.RETURNED, true, extra, result);
    }

    /**
     * Moves a reservation from {@code from} to {@code to} in one transaction, and puts the copy back
     * on the shelf when {@code copyBack} is true. Fails politely if someone already changed it.
     */
    private static void changeStatus(final Reservation r, final String from, final String to, final boolean copyBack,
                                     final Map<String, Object> extra, final Result<Void> result) {
        final DocumentReference resRef = reservations().document(r.id);
        final DocumentReference bRef = bookRef(r.bookId);
        db().runTransaction(t -> {
            // Firestore needs every read before any write.
            DocumentSnapshot current = t.get(resRef);
            DocumentSnapshot book = copyBack ? t.get(bRef) : null;
            if (!current.exists() || !from.equals(current.getString("status"))) {
                return "This reservation was already updated. The list will refresh.";
            }
            Map<String, Object> changes = new HashMap<>();
            changes.put("status", to);
            changes.put("updatedAt", System.currentTimeMillis());
            if (extra != null) changes.putAll(extra);
            t.update(resRef, changes);
            if (book != null && book.exists()) {
                long total = number(book, "totalCopies");
                long available = number(book, "availableCopies");
                if (available < total) {
                    t.update(bRef, "availableCopies", FieldValue.increment(1), "updatedAt",
                            System.currentTimeMillis(), "lastReservation", r.id);
                }
            }
            return null;
        }).addOnCompleteListener(done -> {
            if (!done.isSuccessful()) result.onError(CloudMessages.from(done.getException()));
            else if (done.getResult() != null) result.onError(done.getResult());
            else result.onSuccess(null);
        });
    }

    // ============================================================ one-off reads

    /** The signed-in person's reservations, once (used on launch for reminders). */
    public static void getMine(String uid, final Result<List<Reservation>> result) {
        reservations().whereEqualTo("userUid", uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) result.onSuccess(toList(task.getResult()));
            else result.onError(CloudMessages.from(task.getException()));
        });
    }

    // =========================================================== walk-in issue

    /** Librarians: finds the reader with this registration number or staff ID. */
    public static void findReader(final String idNumber, final Result<UserProfile> result) {
        db().collection("users").whereEqualTo("idNumber", idNumber).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                result.onError(CloudMessages.from(task.getException()));
                return;
            }
            List<DocumentSnapshot> docs = task.getResult().getDocuments();
            if (docs.isEmpty()) {
                result.onError("No account has the ID " + idNumber
                        + ". The reader needs to create an account in the app first.");
                return;
            }
            UserProfile reader = UserProfile.from(docs.get(0));
            if (reader.suspended) {
                result.onError(reader.fullName + "'s account is suspended, so they can't borrow books.");
                return;
            }
            result.onSuccess(reader);
        });
    }

    /**
     * Librarians: lends a copy straight away to a reader at the desk (no reservation).
     * The loan appears in the reader's My reservations with its due date.
     */
    public static void issueWalkIn(final Book book, final UserProfile reader, final String librarian,
                                   final Result<Reservation> result) {
        reservations().whereEqualTo("userUid", reader.uid).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                result.onError(CloudMessages.from(task.getException()));
                return;
            }
            for (DocumentSnapshot d : task.getResult().getDocuments()) {
                Reservation r = Reservation.from(d);
                if (r.bookId != book.id) continue;
                if (r.isActive()) {
                    result.onError(reader.fullName + " already reserved this book. Find it under Pickups and tap Collected.");
                    return;
                }
                if (r.isOnLoan()) {
                    result.onError(reader.fullName + " already has this book on loan.");
                    return;
                }
            }
            final DocumentReference newRef = reservations().document();
            final long now = System.currentTimeMillis();
            final String today = Validator.today();
            final String role = reader.effectiveRole();
            final String due = Loans.dueDate(today, Roles.loanDays(role, true));
            db().runTransaction(t -> {
                DocumentSnapshot b = t.get(bookRef(book.id));
                if (!b.exists()) return "This book is not in the shared catalogue.";
                long available = number(b, "availableCopies");
                if (available <= 0) return "No copy of \"" + book.title + "\" is on the shelf right now.";
                t.update(bookRef(book.id), "availableCopies", available - 1, "updatedAt", now,
                        "lastReservation", newRef.getId());
                Map<String, Object> m = new HashMap<>();
                m.put("bookId", book.id);
                m.put("bookTitle", text(b, "title"));
                m.put("shelf", text(b, "shelf"));
                m.put("userUid", reader.uid);
                m.put("userName", reader.fullName);
                m.put("idNumber", reader.idNumber);
                m.put("role", role);
                m.put("pickupDate", today);
                m.put("status", Reservation.COLLECTED);
                m.put("collectedOn", today);
                m.put("dueDate", due);
                m.put("walkIn", true);
                m.put("handledBy", librarian);
                m.put("createdAt", now);
                m.put("updatedAt", now);
                t.set(newRef, m);
                return null;
            }).addOnCompleteListener(done -> {
                if (!done.isSuccessful()) {
                    result.onError(CloudMessages.from(done.getException()));
                } else if (done.getResult() != null) {
                    result.onError(done.getResult());
                } else {
                    Reservation r = new Reservation(newRef.getId(), book.id, book.title, book.shelf, reader.uid,
                            reader.fullName, reader.idNumber, role, today, Reservation.COLLECTED, now, due, librarian);
                    r.walkIn = true;
                    result.onSuccess(r);
                }
            });
        });
    }

    /** Librarians: asks the borrower to bring an overdue book back (shown on their phone). */
    public static void sendReturnReminder(Reservation r, String librarian, final Result<Void> result) {
        Map<String, Object> m = new HashMap<>();
        m.put("reminderSentOn", Validator.today());
        m.put("handledBy", librarian);
        m.put("updatedAt", System.currentTimeMillis());
        reservations().document(r.id).update(m).addOnCompleteListener(task -> {
            if (task.isSuccessful()) result.onSuccess(null);
            else result.onError(CloudMessages.from(task.getException()));
        });
    }

    // ============================================================ edit books

    /**
     * Librarians: adds a new book (id = highest id + 1) or saves changes to one.
     * Changing the number of copies keeps the copies that are out: available = total - out.
     *
     * @param bookId -1 for a new book
     * @return (to result) the book's id
     */
    public static void saveBook(final long bookId, final Book changes, final Result<Long> result) {
        if (bookId < 0) {
            books().get().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    result.onError(CloudMessages.from(task.getException()));
                    return;
                }
                long max = 0;
                for (DocumentSnapshot d : task.getResult().getDocuments()) {
                    Book b = bookFrom(d);
                    if (b != null && b.id > max) max = b.id;
                }
                final long newId = max + 1;
                Map<String, Object> m = bookFields(changes);
                m.put("availableCopies", (long) changes.totalCopies);
                bookRef(newId).set(m).addOnCompleteListener(done -> {
                    if (done.isSuccessful()) result.onSuccess(newId);
                    else result.onError(CloudMessages.from(done.getException()));
                });
            });
            return;
        }
        db().runTransaction(t -> {
            DocumentSnapshot b = t.get(bookRef(bookId));
            if (!b.exists()) return "This book was removed from the catalogue.";
            long out = number(b, "totalCopies") - number(b, "availableCopies");
            if (changes.totalCopies < out) {
                return out + " copies are reserved or on loan, so the total can't be less than " + out + ".";
            }
            Map<String, Object> m = bookFields(changes);
            m.put("availableCopies", changes.totalCopies - out);
            t.update(bookRef(bookId), m);
            return null;
        }).addOnCompleteListener(done -> {
            if (!done.isSuccessful()) result.onError(CloudMessages.from(done.getException()));
            else if (done.getResult() != null) result.onError(done.getResult());
            else result.onSuccess(bookId);
        });
    }

    private static Map<String, Object> bookFields(Book b) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", b.title);
        m.put("author", b.author);
        m.put("category", b.category);
        m.put("shelf", b.shelf);
        m.put("year", (long) b.year);
        m.put("description", b.description);
        m.put("totalCopies", (long) b.totalCopies);
        m.put("updatedAt", System.currentTimeMillis());
        return m;
    }

    /** Librarians: removes a book nobody holds or borrows. */
    public static void deleteBook(final long bookId, final Result<Void> result) {
        reservations().whereEqualTo("bookId", bookId).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                result.onError(CloudMessages.from(task.getException()));
                return;
            }
            for (DocumentSnapshot d : task.getResult().getDocuments()) {
                Reservation r = Reservation.from(d);
                if (r.isActive() || r.isOnLoan()) {
                    result.onError("Someone still has this book reserved or on loan. Remove it after it is returned.");
                    return;
                }
            }
            bookRef(bookId).delete().addOnCompleteListener(done -> {
                if (done.isSuccessful()) result.onSuccess(null);
                else result.onError(CloudMessages.from(done.getException()));
            });
        });
    }
}
