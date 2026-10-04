package ug.ac.usjm.smartlibrary;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import ug.ac.usjm.smartlibrary.data.Book;

/** Shows each book as a card in the catalogue list (layout: item_book.xml). */
public class BookAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<Book> books = new ArrayList<>();

    public BookAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setBooks(List<Book> newBooks) {
        books.clear();
        books.addAll(newBooks);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return books.size();
    }

    @Override
    public Book getItem(int position) {
        return books.get(position);
    }

    @Override
    public long getItemId(int position) {
        return books.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_book, parent, false);
            h = new ViewHolder(convertView);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();   // reuse the row instead of inflating a new one
        }
        Book b = getItem(position);
        h.title.setText(b.title);
        h.author.setText(b.author);
        h.category.setText(b.category + "  •  " + b.shelf);
        h.availability.setText(b.availabilityText());
        h.availability.setBackgroundResource(b.isAvailable() ? R.drawable.bg_badge_green : R.drawable.bg_badge_red);
        h.availability.setTextColor(ContextCompat.getColor(convertView.getContext(),
                b.isAvailable() ? R.color.lib_green : R.color.lib_red));
        return convertView;
    }

    private static class ViewHolder {
        final TextView title, author, category, availability;

        ViewHolder(View v) {
            title = (TextView) v.findViewById(R.id.book_title);
            author = (TextView) v.findViewById(R.id.book_author);
            category = (TextView) v.findViewById(R.id.book_category);
            availability = (TextView) v.findViewById(R.id.book_availability);
        }
    }
}
