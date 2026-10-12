package ug.ac.usjm.smartlibrary;

import android.content.Intent;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import ug.ac.usjm.smartlibrary.auth.Session;
import ug.ac.usjm.smartlibrary.data.Book;
import ug.ac.usjm.smartlibrary.data.LibraryRepository;
import ug.ac.usjm.smartlibrary.data.UserProfile;
import ug.ac.usjm.smartlibrary.util.QrPayload;
import ug.ac.usjm.smartlibrary.util.Roles;

/**
 * Screen 2 - Book Details: full information about one book and a button to reserve it.
 * Librarians also see Edit, Lend (walk-in) and the book's QR label to print or show.
 */
public class BookDetailActivity extends AppCompatActivity {

    public static final String EXTRA_BOOK_ID = "ug.ac.usjm.smartlibrary.BOOK_ID";

    private long bookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);

        bookId = getIntent().getLongExtra(EXTRA_BOOK_ID, -1);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        UserProfile me = user == null ? null : Session.get(this, user.getUid());
        if (me != null && Roles.canRunDesk(me.role, me.approved)) showLibrarianTools();

        findViewById(R.id.btn_reserve).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(BookDetailActivity.this, ReserveActivity.class);
                i.putExtra(EXTRA_BOOK_ID, bookId);
                startActivity(i);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showBook();   // refresh after a reservation changes the copy count
    }

    private void showLibrarianTools() {
        findViewById(R.id.librarian_tools).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_edit_book).setOnClickListener(v -> {
            Intent i = new Intent(this, BookEditActivity.class);
            i.putExtra(BookEditActivity.EXTRA_BOOK_ID, bookId);
            startActivity(i);
        });
        findViewById(R.id.btn_lend_book).setOnClickListener(v -> {
            Intent i = new Intent(this, IssueActivity.class);
            i.putExtra(IssueActivity.EXTRA_BOOK_ID, bookId);
            startActivity(i);
        });
        // The same QR code the scanner reads (usjm-library://book/<id>), drawn by the ZXing library.
        try {
            Bitmap qr = new BarcodeEncoder().encodeBitmap(QrPayload.forBook(bookId), BarcodeFormat.QR_CODE, 600, 600);
            ((ImageView) findViewById(R.id.qr_label)).setImageBitmap(qr);
            ((TextView) findViewById(R.id.qr_caption)).setText(getString(R.string.qr_caption, bookId));
        } catch (Exception e) {
            findViewById(R.id.qr_label).setVisibility(View.GONE);
        }
    }

    private void showBook() {
        Book book;
        try {
            book = LibraryRepository.get(this).getBook(bookId);
        } catch (SQLException e) {
            book = null;
        }
        if (book == null) {
            Toast.makeText(this, R.string.error_book_not_found, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ((TextView) findViewById(R.id.detail_title)).setText(book.title);
        ((TextView) findViewById(R.id.detail_author)).setText(getString(R.string.by_author, book.author));
        ((TextView) findViewById(R.id.detail_category)).setText(book.category);
        ((TextView) findViewById(R.id.detail_year)).setText(String.valueOf(book.year));
        ((TextView) findViewById(R.id.detail_shelf)).setText(book.shelf);
        ((TextView) findViewById(R.id.detail_description)).setText(book.description);

        TextView availability = (TextView) findViewById(R.id.detail_availability);
        availability.setText(book.availabilityText());
        availability.setBackgroundResource(book.isAvailable() ? R.drawable.bg_badge_green : R.drawable.bg_badge_red);
        availability.setTextColor(ContextCompat.getColor(this, book.isAvailable() ? R.color.lib_green : R.color.lib_red));

        Button reserve = (Button) findViewById(R.id.btn_reserve);
        reserve.setEnabled(book.isAvailable());
        reserve.setText(book.isAvailable() ? R.string.reserve_for_pickup : R.string.no_copies_left);
    }
}
