package ug.ac.usjm.smartlibrary;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ug.ac.usjm.smartlibrary.data.CloudLibrary;
import ug.ac.usjm.smartlibrary.data.Reservation;
import ug.ac.usjm.smartlibrary.notify.PickupReminders;
import ug.ac.usjm.smartlibrary.util.Loans;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Screen 4 - My reservations: the signed-in person's holds and loans, live from Cloud Firestore.
 * When the librarian marks a book Collected or Returned, this list changes on its own.
 */
public class MyReservationsActivity extends AppCompatActivity
        implements ReservationAdapter.OnCancelClickListener {

    private ReservationAdapter adapter;
    private TextView emptyView;
    private ListenerRegistration registration;
    private final Set<String> expiring = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_reservations);

        ListView list = (ListView) findViewById(R.id.reservation_list);
        emptyView = (TextView) findViewById(R.id.empty_view);
        adapter = new ReservationAdapter(this, this);
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        emptyView.setText(R.string.loading);
        registration = CloudLibrary.listenToMine(user.getUid(), new CloudLibrary.Listener<List<Reservation>>() {
            @Override
            public void onChange(List<Reservation> reservations) {
                emptyView.setText(R.string.empty_reservations);
                String today = Validator.today();
                for (Reservation r : reservations) {
                    // A hold whose pickup day has passed was not collected: it expires and the copy goes back.
                    if (r.isActive() && Loans.pickupMissed(r.pickupDate, today) && expiring.add(r.id)) {
                        CloudLibrary.expire(r, retryLater(r.id));
                    }
                    PickupReminders.sync(MyReservationsActivity.this, r);
                }
                adapter.setReservations(reservations);
            }

            @Override
            public void onError(String message) {
                emptyView.setText(message);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) registration.remove();   // stop listening while the screen is hidden
        registration = null;
    }

    /** If expiring fails (e.g. offline), forget it so the next update tries again. */
    private CloudLibrary.Result<Void> retryLater(final String reservationId) {
        return new CloudLibrary.Result<Void>() {
            @Override
            public void onSuccess(Void v) {
            }

            @Override
            public void onError(String message) {
                expiring.remove(reservationId);
            }
        };
    }

    /** Ask before cancelling: it cannot be undone. */
    @Override
    public void onCancelClick(final Reservation reservation) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_title)
                .setMessage(getString(R.string.cancel_message, reservation.bookTitle))
                .setNegativeButton(R.string.keep_it, null)
                .setPositiveButton(R.string.cancel_reservation, (dialog, which) ->
                        CloudLibrary.cancel(reservation, new CloudLibrary.Result<Void>() {
                            @Override
                            public void onSuccess(Void v) {
                                PickupReminders.cancel(MyReservationsActivity.this, reservation);
                                Toast.makeText(MyReservationsActivity.this, R.string.cancelled_toast,
                                        Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(MyReservationsActivity.this, message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }
}
