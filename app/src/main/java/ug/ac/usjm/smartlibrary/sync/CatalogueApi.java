package ug.ac.usjm.smartlibrary.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import ug.ac.usjm.smartlibrary.data.Book;
import ug.ac.usjm.smartlibrary.util.ServerAddress;

/**
 * Downloads the catalogue from the USJM Smart Library web system (GET /api/books/).
 * Uses Android's built-in HttpURLConnection and org.json, so no extra libraries are needed.
 * Must be called on a background thread.
 */
public final class CatalogueApi {

    private static final int TIMEOUT_MS = 8000;

    private CatalogueApi() {
    }

    public static List<Book> fetchBooks(String serverBase) throws IOException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(ServerAddress.booksUrl(serverBase)).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        try {
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("The server answered " + status);
            }
            return parse(readAll(conn.getInputStream()));
        } finally {
            conn.disconnect();
        }
    }

    /** Turns the API's JSON into Book objects. */
    static List<Book> parse(String json) throws JSONException {
        JSONArray items = new JSONObject(json).getJSONArray("books");
        List<Book> books = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            JSONObject b = items.getJSONObject(i);
            books.add(new Book(
                    b.getLong("id"),
                    b.getString("title"),
                    b.optString("author", ""),
                    b.optString("category", "General Collection"),
                    b.optString("shelf", "Ask at the circulation desk"),
                    b.optInt("year", 0),
                    b.optString("description", ""),
                    b.optInt("total_copies", 0),
                    b.optInt("available_copies", 0)));
        }
        return books;
    }

    private static String readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
