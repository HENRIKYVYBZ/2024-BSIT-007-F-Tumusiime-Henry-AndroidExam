package ug.ac.usjm.smartlibrary;

import android.database.SQLException;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import ug.ac.usjm.smartlibrary.data.Book;
import ug.ac.usjm.smartlibrary.data.LibraryRepository;

/** Screen 1 - Catalogue: every book from the phone's SQLite database, with live search. */
public class MainActivity extends AppCompatActivity {

    private LibraryRepository repo;
    private BookAdapter adapter;
    private EditText searchBox;
    private TextView resultCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repo = LibraryRepository.get(this);
        searchBox = (EditText) findViewById(R.id.search_box);
        resultCount = (TextView) findViewById(R.id.result_count);

        ListView list = (ListView) findViewById(R.id.book_list);
        adapter = new BookAdapter(this);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.empty_view));

        // Search as the user types.
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                loadBooks();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Runs on first open and every time the user comes back, so the list is always current.
        loadBooks();
    }

    private void loadBooks() {
        try {
            List<Book> books = repo.getBooks(searchBox.getText().toString());
            adapter.setBooks(books);
            resultCount.setText(getResources().getQuantityString(R.plurals.books_found, books.size(), books.size()));
        } catch (SQLException e) {
            Toast.makeText(this, R.string.error_loading, Toast.LENGTH_LONG).show();
        }
    }
}
