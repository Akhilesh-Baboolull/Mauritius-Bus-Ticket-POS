package com.ayb.busticketpos;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only list of Settings audit events and app errors kept on this device (last 10 days, newest first). */
public class AuditLogActivity extends AppCompatActivity {

    private final List<SettingsAudit.Entry> entries = new ArrayList<>();
    private AuditAdapter adapter;
    private TextView tvSummary;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audit_log);

        tvSummary = findViewById(R.id.audit_summary);
        tvEmpty = findViewById(R.id.audit_empty);
        RecyclerView list = findViewById(R.id.audit_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AuditAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.btn_audit_back).setOnClickListener(v -> finish());

        ChipGroup filter = findViewById(R.id.audit_filter);
        filter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int id = checkedIds.isEmpty() ? R.id.chip_all : checkedIds.get(0);
            currentFilter = id == R.id.chip_settings ? FILTER_SETTINGS
                    : id == R.id.chip_app ? FILTER_APP : FILTER_ALL;
            applyFilter();
        });

        ChipGroup categoryFilter = findViewById(R.id.audit_category_filter);
        categoryFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            selectedCategories.clear();
            for (int id : checkedIds) {
                if (id == R.id.chip_cat_info) selectedCategories.add(SettingsAudit.Category.INFO);
                else if (id == R.id.chip_cat_warning) selectedCategories.add(SettingsAudit.Category.WARNING);
                else if (id == R.id.chip_cat_error) selectedCategories.add(SettingsAudit.Category.ERROR);
                else if (id == R.id.chip_cat_critical) selectedCategories.add(SettingsAudit.Category.CRITICAL);
            }
            applyFilter();
        });

        EditText search = findViewById(R.id.audit_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        btnExport = findViewById(R.id.btn_audit_export);
        btnExport.setOnClickListener(v -> exportCsv());

        loadEntries();
    }

    private static final int FILTER_ALL = 0;
    private static final int FILTER_SETTINGS = 1;
    private static final int FILTER_APP = 2;
    private static final String EXPORT_FOLDER = "BusTicketPOSLogs";
    private final List<SettingsAudit.Entry> allEntries = new ArrayList<>();
    private final Set<SettingsAudit.Category> selectedCategories = EnumSet.noneOf(SettingsAudit.Category.class);
    private int currentFilter = FILTER_ALL;
    private String searchQuery = "";
    private MaterialButton btnExport;

    private void loadEntries() {
        AppExecutors.runBg(() -> {
            List<SettingsAudit.Entry> recent = SettingsAudit.readRecent(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                allEntries.clear();
                allEntries.addAll(recent);
                applyFilter();
            });
        });
    }

    /**
     * Source chip (All / Settings / App errors), category chips (none selected = all) and the
     * keyword (matched against event name, description, code, category and date/time).
     * App errors / warnings / crashes are the APP_* events written by AppLog.
     */
    private void applyFilter() {
        entries.clear();
        for (SettingsAudit.Entry e : allEntries) {
            boolean isApp = e.code != null && e.code.startsWith("APP_");
            boolean sourceOk = currentFilter == FILTER_ALL
                    || (currentFilter == FILTER_APP && isApp)
                    || (currentFilter == FILTER_SETTINGS && !isApp);
            boolean categoryOk = selectedCategories.isEmpty() || selectedCategories.contains(e.category);
            if (sourceOk && categoryOk && matchesSearch(e)) {
                entries.add(e);
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        boolean filtered = currentFilter != FILTER_ALL || !selectedCategories.isEmpty() || !searchQuery.isEmpty();
        tvSummary.setText(filtered
                ? entries.size() + " of " + allEntries.size() + " event(s) match (last 10 days)"
                : entries.size() + " event(s) from the last 10 days");
    }

    private boolean matchesSearch(SettingsAudit.Entry e) {
        if (searchQuery.isEmpty()) return true;
        String haystack = (e.title + " " + (e.description != null ? e.description : "") + " "
                + e.code + " " + e.category.label + " " + formatTime(e.timeMillis))
                .toLowerCase(Locale.ROOT);
        return haystack.contains(searchQuery);
    }

    private static String formatTime(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    // ---------------------------------------------------------------------
    // CSV export (what is currently shown, i.e. after filters and search)
    // ---------------------------------------------------------------------

    private void exportCsv() {
        if (entries.isEmpty()) {
            Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<SettingsAudit.Entry> toExport = new ArrayList<>(entries);
        btnExport.setEnabled(false);
        final Context app = getApplicationContext();

        AppExecutors.runBg(() -> {
            String machine = PrefsSecure.getMachineId(app);
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "audit_log_BTM" + (machine != null && !machine.trim().isEmpty() ? machine.trim() : "")
                    + "_" + stamp + ".csv";
            String savedTo = null;
            Throwable error = null;
            try {
                byte[] csv = buildCsv(toExport).getBytes(StandardCharsets.UTF_8);
                savedTo = writeToDocuments(app, fileName, csv);
            } catch (Throwable t) {
                error = t;
                AppLog.e("AuditLog", "CSV export failed", t);
            }

            final String path = savedTo;
            final Throwable failure = error;
            if (path != null) {
                SettingsAudit.log(app, SettingsAudit.Event.AUDIT_LOG_EXPORTED,
                        toExport.size() + " entries to " + path);
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                btnExport.setEnabled(true);
                if (path != null) {
                    AppDialog.with(this)
                            .type(AppDialog.Type.SUCCESS)
                            .title("Exported")
                            .message(toExport.size() + " entries saved to\n" + path)
                            .positive("OK", null)
                            .show();
                } else {
                    AppDialog.with(this)
                            .type(AppDialog.Type.ERROR)
                            .title("Export failed")
                            .message(failure != null && failure.getMessage() != null
                                    ? failure.getMessage() : "The file could not be saved.")
                            .positive("OK", null)
                            .show();
                }
            });
        });
    }

    private static String buildCsv(List<SettingsAudit.Entry> rows) {
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss", Locale.US);
        StringBuilder sb = new StringBuilder("\uFEFF"); // BOM so Excel reads UTF-8 correctly
        sb.append("Date,Time,Category,Category No,Event Code,Event,Description\r\n");
        for (SettingsAudit.Entry e : rows) {
            Date d = new Date(e.timeMillis);
            sb.append(csv(date.format(d))).append(',')
                    .append(csv(time.format(d))).append(',')
                    .append(csv(e.category.label)).append(',')
                    .append(e.category.number).append(',')
                    .append(csv(e.code)).append(',')
                    .append(csv(e.title)).append(',')
                    .append(csv(e.description)).append("\r\n");
        }
        return sb.toString();
    }

    private static String csv(@Nullable String s) {
        if (s == null) return "\"\"";
        String clean = s.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"");
        return "\"" + clean + "\"";
    }

    /**
     * Saves into the shared Documents/BusTicketPOSLogs folder (same folder as the crash logs).
     * Falls back to the app's own Documents folder if the shared one is not writable.
     * @return a human-readable location of the saved file
     */
    private static String writeToDocuments(Context app, String fileName, byte[] data) throws Exception {
        String relative = Environment.DIRECTORY_DOCUMENTS + "/" + EXPORT_FOLDER;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relative);
            Uri uri = app.getContentResolver().insert(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            if (uri == null) throw new IllegalStateException("MediaStore insert returned null");
            try (OutputStream os = app.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IllegalStateException("Could not open output stream");
                os.write(data);
            }
            return "Internal storage/" + relative + "/" + fileName;
        } catch (Throwable shared) {
            AppLog.w("AuditLog", "Shared Documents not writable, using app folder", shared);
            File dir = new File(app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), EXPORT_FOLDER);
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create " + dir);
            File out = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(data);
            }
            return out.getAbsolutePath();
        }
    }

    private static int colorFor(SettingsAudit.Category c) {
        switch (c) {
            case WARNING:
                return Color.parseColor("#EF6C00");
            case ERROR:
                return Color.parseColor("#D32F2F");
            case CRITICAL:
                return Color.parseColor("#6A1B9A");
            case INFO:
            default:
                return Color.parseColor("#1976D2");
        }
    }

    private final class AuditAdapter extends RecyclerView.Adapter<AuditAdapter.Holder> {
        private final SimpleDateFormat timeFormat =
                new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

        final class Holder extends RecyclerView.ViewHolder {
            final View bar;
            final TextView category, time, title, description;

            Holder(@NonNull View itemView) {
                super(itemView);
                itemView.setClipToOutline(true);
                bar = itemView.findViewById(R.id.audit_category_bar);
                category = itemView.findViewById(R.id.audit_category);
                time = itemView.findViewById(R.id.audit_time);
                title = itemView.findViewById(R.id.audit_title);
                description = itemView.findViewById(R.id.audit_description);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_audit_log, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            SettingsAudit.Entry e = entries.get(position);
            int color = colorFor(e.category);
            h.bar.setBackgroundColor(color);
            h.category.setText(e.category.label.toUpperCase(Locale.US));
            h.category.setBackgroundTintList(ColorStateList.valueOf(color));
            h.time.setText(timeFormat.format(new Date(e.timeMillis)));
            h.title.setText(e.title);
            if (e.description != null && !e.description.isEmpty()) {
                h.description.setText(e.description);
                h.description.setVisibility(View.VISIBLE);
            } else {
                h.description.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }
}
