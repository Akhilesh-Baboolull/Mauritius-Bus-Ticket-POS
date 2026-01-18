package com.ayb.busticketpos;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Calculator extends AppCompatActivity {

    private EditText etMoneyReceived;
    private TextView tvTotal, tvChange;
    private ChipGroup chipsSelected;
    private RecyclerView rvLastTickets;

    private Button btnQuick50, btnQuick100, btnQuick200, btnQuick500, btnBack;

    private TicketDao ticketDao;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // Selected tickets (we keep IDs so we can toggle)
    private final Set<Integer> selectedTicketIds = new HashSet<>();
    private final List<TicketEntity> selectedTickets = new ArrayList<>();

    private LastTicketsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        AppDatabase db = AppDatabase.getInstance(this);
        ticketDao = db.ticketDao();

        bindViews();
        setupRecycler();
        setupQuickCash();
        setupMoneyWatcher();
        setupBack();

        loadLastTickets();
        recalcAndRender();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // In case new tickets were issued while calculator was in background
        loadLastTickets();
    }

    private void bindViews() {
        etMoneyReceived = findViewById(R.id.money_received);
        tvTotal = findViewById(R.id.tv_total);
        tvChange = findViewById(R.id.tv_change);
        chipsSelected = findViewById(R.id.chips_selected);
        rvLastTickets = findViewById(R.id.rv_last_tickets);

        btnQuick50 = findViewById(R.id.quick_50);
        btnQuick100 = findViewById(R.id.quick_100);
        btnQuick200 = findViewById(R.id.quick_200);
        btnQuick500 = findViewById(R.id.quick_500);

        btnBack = findViewById(R.id.btn_back);
    }

    private void setupRecycler() {
        rvLastTickets.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LastTicketsAdapter(new ArrayList<>(), this::toggleTicketSelection);
        rvLastTickets.setAdapter(adapter);
    }

    private void setupQuickCash() {
        View.OnClickListener l = v -> {
            int amount = 0;
            if (v.getId() == R.id.quick_50) amount = 50;
            else if (v.getId() == R.id.quick_100) amount = 100;
            else if (v.getId() == R.id.quick_200) amount = 200;
            else if (v.getId() == R.id.quick_500) amount = 500;

            etMoneyReceived.setText(String.valueOf(amount));
            etMoneyReceived.setSelection(etMoneyReceived.getText().length());
            recalcAndRender();
        };

        btnQuick50.setOnClickListener(l);
        btnQuick100.setOnClickListener(l);
        btnQuick200.setOnClickListener(l);
        btnQuick500.setOnClickListener(l);
    }

    private void setupMoneyWatcher() {
        etMoneyReceived.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                recalcAndRender();
            }
        });
    }

    private void setupBack() {
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadLastTickets() {
        io.execute(() -> {
            List<TicketEntity> last;
            try {
                // requires the DAO query you add
                last = ticketDao.getLastTickets(10);
            } catch (Throwable e) {
                // fallback if query not yet added (won't crash)
                TripEntity lastTrip = ticketDao.getLastTrip();
                if (lastTrip == null) last = new ArrayList<>();
                else {
                    List<TicketEntity> allTripTickets = ticketDao.getTicketsForTrip(lastTrip.tripId);
                    // take last 10 from trip list (assuming order by insertion)
                    int start = Math.max(0, allTripTickets.size() - 10);
                    last = allTripTickets.subList(start, allTripTickets.size());
                }
            }

            // Ensure newest-first display
            // If DAO already returns DESC this is fine; fallback may be ASC.
            // We'll reverse when needed:
            if (last.size() > 1) {
                // If first ticketId < last ticketId, reverse to get DESC
                if (last.get(0).ticketId < last.get(last.size() - 1).ticketId) {
                    List<TicketEntity> reversed = new ArrayList<>();
                    for (int i = last.size() - 1; i >= 0; i--) reversed.add(last.get(i));
                    last = reversed;
                }
            }

            final List<TicketEntity> finalLast = last;
            runOnUiThread(() -> adapter.setData(finalLast));
        });
    }

    private void toggleTicketSelection(TicketEntity t) {
        if (selectedTicketIds.contains(t.ticketId)) {
            // remove
            selectedTicketIds.remove(t.ticketId);

            for (int i = 0; i < selectedTickets.size(); i++) {
                if (selectedTickets.get(i).ticketId == t.ticketId) {
                    selectedTickets.remove(i);
                    break;
                }
            }
        } else {
            // add
            selectedTicketIds.add(t.ticketId);
            selectedTickets.add(t);
        }

        rebuildChips();
        recalcAndRender();
        adapter.setSelectedIds(selectedTicketIds);
    }

    private void rebuildChips() {
        chipsSelected.removeAllViews();

        for (TicketEntity t : selectedTickets) {
            Chip chip = new Chip(this);
            chip.setText(String.format(Locale.getDefault(), "%s (Rs %d)", safeTicketNo(t.ticketNo), t.amount));
            chip.setCloseIconVisible(true);

            chip.setOnCloseIconClickListener(v -> {
                selectedTicketIds.remove(t.ticketId);

                for (int i = 0; i < selectedTickets.size(); i++) {
                    if (selectedTickets.get(i).ticketId == t.ticketId) {
                        selectedTickets.remove(i);
                        break;
                    }
                }

                rebuildChips();
                recalcAndRender();
                adapter.setSelectedIds(selectedTicketIds);
            });

            chipsSelected.addView(chip);
        }
    }

    @SuppressLint("SetTextI18n")
    private void recalcAndRender() {
        int totalSelected = 0;
        for (TicketEntity t : selectedTickets) totalSelected += t.amount;

        int received = parseIntSafe(etMoneyReceived.getText() == null ? "" : etMoneyReceived.getText().toString());
        int change = received - totalSelected;

        tvTotal.setText(String.format(Locale.getDefault(), "TOTAL: Rs %d", totalSelected));

        if (totalSelected == 0) {
            tvChange.setText("CHANGE: Rs 0");
        } else if (change >= 0) {
            tvChange.setText(String.format(Locale.getDefault(), "CHANGE: Rs %d", change));
        } else {
            // If not enough, show how much is still needed
            tvChange.setText(String.format(Locale.getDefault(), "REMAINING: Rs %d", Math.abs(change)));
        }
    }

    private int parseIntSafe(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String safeTicketNo(String ticketNo) {
        if (ticketNo == null) return "TICKET";
        String t = ticketNo.trim();
        return t.isEmpty() ? "TICKET" : t;
    }

    // -------------------------------------------------------------------------
    // RecyclerView Adapter
    // -------------------------------------------------------------------------
    static class LastTicketsAdapter extends RecyclerView.Adapter<LastTicketsAdapter.VH> {

        interface OnTicketClick {
            void onClick(TicketEntity t);
        }

        private final OnTicketClick click;
        private List<TicketEntity> data;
        private Set<Integer> selectedIds = new HashSet<>();

        LastTicketsAdapter(List<TicketEntity> data, OnTicketClick click) {
            this.data = data;
            this.click = click;
        }

        void setData(List<TicketEntity> data) {
            this.data = data == null ? new ArrayList<>() : data;
            notifyDataSetChanged();
        }

        void setSelectedIds(Set<Integer> ids) {
            this.selectedIds = (ids == null) ? new HashSet<>() : ids;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_last_ticket, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            TicketEntity t = data.get(position);

            String route = (safe(t.startStage) + " → " + safe(t.endStage)).trim();
            h.tvRoute.setText(route);

            String meta = (safe(t.fareType) + " • " + safe(t.time)).trim();
            h.tvMeta.setText(meta);

            h.tvAmount.setText(String.format(Locale.getDefault(), "Rs %d", t.amount));

            // Visual selection (simple alpha)
            boolean selected = selectedIds.contains(t.ticketId);
            h.itemView.setAlpha(selected ? 0.6f : 1.0f);

            h.itemView.setOnClickListener(v -> click.onClick(t));
        }

        @Override
        public int getItemCount() {
            return data == null ? 0 : data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvRoute, tvMeta, tvAmount;

            VH(@NonNull View itemView) {
                super(itemView);
                tvRoute = itemView.findViewById(R.id.tv_route);
                tvMeta = itemView.findViewById(R.id.tv_meta);
                tvAmount = itemView.findViewById(R.id.tv_amount);
            }
        }

        private static String safe(String s) {
            return (s == null) ? "" : s;
        }
    }
}
