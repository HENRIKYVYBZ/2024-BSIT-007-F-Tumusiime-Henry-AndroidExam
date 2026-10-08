package ug.ac.usjm.smartlibrary;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.SQLException;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import ug.ac.usjm.smartlibrary.data.LibraryRepository;
import ug.ac.usjm.smartlibrary.data.Reservation;
import ug.ac.usjm.smartlibrary.notify.PickupReminders;
import ug.ac.usjm.smartlibrary.util.Validator;

/** Screen 4 - My Reservations: the signed-in student's reservations, read from SQLite, with Cancel. */
public class MyReservationsActivity extends AppCompatActivity
        implements ReservationAdapter.OnCancelClickListener {

    private LibraryRepository repo;
    private ReservationAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_reservations);

        repo = LibraryRepository.get(this);
        ListView list = (ListView) findViewById(R.id.reservation_list);
        adapter = new ReservationAdapter(this, this);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.empty_view));

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        try {
            repo.expireUncollected(Validator.today());
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                finish();
                return;
            }
            adapter.setReservations(repo.getReservations(user.getUid()));
        } catch (SQLException e) {
            Toast.makeText(this, R.string.error_loading, Toast.LENGTH_LONG).show();
        }
    }

    /** Ask before cancelling: it cannot be undone. */
    @Override
    public void onCancelClick(final Reservation reservation) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_title)
                .setMessage(getString(R.string.cancel_message, reservation.bookTitle))
                .setNegativeButton(R.string.keep_it, null)
                .setPositiveButton(R.string.cancel_reservation, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            if (repo.cancelReservation(reservation.id)) {
                                PickupReminders.cancel(MyReservationsActivity.this, reservation.id);
                                Toast.makeText(MyReservationsActivity.this, R.string.cancelled_toast,
                                        Toast.LENGTH_SHORT).show();
                            }
                        } catch (SQLException e) {
                            Toast.makeText(MyReservationsActivity.this, R.string.error_saving,
                                    Toast.LENGTH_LONG).show();
                        }
                        load();
                    }
                })
                .show();
    }
}
