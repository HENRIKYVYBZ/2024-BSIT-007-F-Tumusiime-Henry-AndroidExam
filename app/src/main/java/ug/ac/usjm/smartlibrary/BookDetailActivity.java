package ug.ac.usjm.smartlibrary;

import android.database.SQLException;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import ug.ac.usjm.smartlibrary.data.Book;
import ug.ac.usjm.smartlibrary.data.LibraryRepository;

/** Screen 2 - Book Details: full information about one book and a button to reserve it. */
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
        findViewById(R.id.btn_reserve).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // The reservation form is added in milestone 3.
                Toast.makeText(BookDetailActivity.this, "Reservations are coming in the next version",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showBook();   // refresh after a reservation changes the copy count
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
