package ug.ac.usjm.smartlibrary.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ug.ac.usjm.smartlibrary.data.Book;
import ug.ac.usjm.smartlibrary.data.CloudLibrary;

/**
 * Librarians: imports the catalogue from the USJM Smart Library web system (Django REST API) and
 * publishes it to Cloud Firestore, so every phone gets it. The download runs on a background thread
 * (network is not allowed on the main thread); the result is handed back on the main thread.
 */
public final class CatalogueSync {

    public interface Callback {
        void onSynced(int bookCount);

        void onFailed(String message);
    }

    private static final String PREFS = "catalogue_sync";
    private static final String KEY_SERVER = "server";
    private static final String KEY_LAST_SYNC = "last_sync";

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CatalogueSync() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The saved server address, e.g. "http://192.168.1.10:8000", or null if none yet. */
    public static String server(Context c) {
        return prefs(c).getString(KEY_SERVER, null);
    }

    public static void setServer(Context c, String base) {
        prefs(c).edit().putString(KEY_SERVER, base).apply();
    }

    /** Time of the last successful sync in milliseconds, or 0 if never. */
    public static long lastSync(Context c) {
        return prefs(c).getLong(KEY_LAST_SYNC, 0);
    }

    /** Downloads the web system's catalogue and publishes it to Firestore. The callback runs on the main thread. */
    public static void run(Context context, final Callback callback) {
        final Context app = context.getApplicationContext();
        final String server = server(app);
        if (server == null) {
            callback.onFailed("Set the library server address first.");
            return;
        }
        WORKER.execute(() -> {
            try {
                List<Book> books = CatalogueApi.fetchBooks(server);
                if (books.isEmpty()) throw new IllegalStateException("The server has no books yet.");

                MAIN.post(() -> CloudLibrary.publishCatalogue(books, new CloudLibrary.Result<Integer>() {
                    @Override
                    public void onSuccess(Integer count) {
                        prefs(app).edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
                        callback.onSynced(count);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onFailed(message);
                    }
                }));
            } catch (java.io.IOException e) {
                MAIN.post(() -> callback.onFailed("Can't reach the library server at " + server
                        + ". Check that the phone and the server are on the same Wi-Fi and the server is running."));
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailed("The server's catalogue could not be read: " + e.getMessage()));
            }
        });
    }
}
