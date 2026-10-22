// src/main/java/com/ayb/busticketpos/Day.java
package com.ayb.busticketpos;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class Day extends AppCompatActivity {
    private LinearLayout layoutDay, layoutEndDay, layoutWaybill;
    private TextView    viewBus;
    private Button      btnLeft, btnRight, btnSubmit, btnEndDay, btnBack, btnBack2, btn_skip_waybill;

    private Button btnBack3, btn_submit_waybill;

    private EditText total_receipt, other_receipt, diesel, wages, well_meal, maintenance, other_expenses;

    private List<String> busNos = new ArrayList<>();
    private int          currentIndex = 0;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_day);

        // 1️⃣ Find views
        layoutDay    = findViewById(R.id.layout_day);
        layoutEndDay = findViewById(R.id.layout_end_day);

        viewBus   = findViewById(R.id.view_stage);
        btnLeft   = findViewById(R.id.button_left);
        btnRight  = findViewById(R.id.button_right);
        btnSubmit = findViewById(R.id.button_submit);
        btnBack   = findViewById(R.id.button_back);

        btnEndDay = findViewById(R.id.button_end_day);
        btnBack2  = findViewById(R.id.button_back2);

        //Waybill
        layoutWaybill = findViewById(R.id.layout_waybill);
        total_receipt = findViewById(R.id.total_receipt);
        other_receipt = findViewById(R.id.other_receipt);
        diesel = findViewById(R.id.diesel);
        wages = findViewById(R.id.wages);
        well_meal = findViewById(R.id.well_meal);
        maintenance = findViewById(R.id.maintenance);
        other_expenses = findViewById(R.id.other_expenses);
        btnBack3 = findViewById(R.id.btn_back3);
        btn_submit_waybill = findViewById(R.id.btn_submit_waybill);
        btn_skip_waybill = findViewById(R.id.button_skip);


        // 2️⃣ Wire up the “Back” buttons to just finish()
        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(Day.this, Menu.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        btnBack2.setOnClickListener(v -> {
            startActivity(new Intent(Day.this, Menu.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });


        // 3️⃣ Check DAY STATUS
        if (Prefs.getDayStatus(this) == 1) {
            // already started → show “End Day” screen
            layoutDay.setVisibility(View.GONE);
            layoutEndDay.setVisibility(View.VISIBLE);
        } else {
            // not started → show “Choose Bus” screen
            layoutDay.setVisibility(View.VISIBLE);
            layoutEndDay.setVisibility(View.GONE);
            initializeBusSelection();
        }

        btnBack3.setOnClickListener(v -> {
            startActivity(new Intent(Day.this, Menu.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        btn_submit_waybill.setOnClickListener(v -> {
            AppExecutors.runBg(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                String today = Prefs.getDayDate(this);

                int serverDayId = Prefs.getCurrentServerDayID(this);
                String todayPrefs = Prefs.getDayDate(this);
                String bus = Prefs.getSelectedBus(this);

                String currentTime = new SimpleDateFormat(
                        "HH:mm:ss", Locale.getDefault()
                ).format(new Date());

                // 1️⃣ Get current day row
                TicketDayEntity day = db.ticketDao().getLastDay();
                if (day == null){
                    Prefs.clearDay(this);
                    Prefs.clearSelectedBus(this);
                    runOnUiThread(() -> {
                        startActivity(new Intent(Day.this, Menu.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        finish();
                    });
                    return;
                }
                db.ticketDao().updateEndTime(currentTime, day.id);
                Prefs.clearSelectedBus(this);
                Prefs.clearDay(this);
                LocationForegroundService.sendOnDayEnd(getApplicationContext());

                // 3️⃣ Check if a waybill already exists
                DayWaybillEntity existing = db.dayWaybillDao().getForDay(day.id);

                // 2️⃣ Get total ticket amount
                Integer total = db.ticketDao().getTotalAmountForDayId(day.id);
                if (total == null) total = 0;

                // 4️⃣ Create or update waybill object
                DayWaybillEntity waybill = (existing != null) ? existing : new DayWaybillEntity();
                waybill.dayId         = day.id;
                waybill.totalReceipt  = total;
                waybill.otherReceipt  = parseIntSafe(other_receipt.getText().toString());
                waybill.diesel        = parseFloatSafe(diesel.getText().toString());
                waybill.wages         = parseFloatSafe(wages.getText().toString());
                waybill.wellMeal      = parseFloatSafe(well_meal.getText().toString());
                waybill.maintenance   = parseFloatSafe(maintenance.getText().toString());
                waybill.otherExpenses = parseFloatSafe(other_expenses.getText().toString());


                int other = parseIntSafe(other_receipt.getText().toString());
                float dsl = parseFloatSafe(diesel.getText().toString());
                float wage = parseFloatSafe(wages.getText().toString());
                float meal = parseFloatSafe(well_meal.getText().toString());
                float maint = parseFloatSafe(maintenance.getText().toString());
                float otherExp = parseFloatSafe(other_expenses.getText().toString());

                // 5️⃣ Insert or Update
                if (existing != null) {
                    db.dayWaybillDao().update(waybill);
                } else {
                    db.dayWaybillDao().insert(waybill);
                }

                runOnUiThread(() -> {
                    Toast.makeText(Day.this, "Waybill saved. Day ended.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(Day.this, Menu.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                });

                SyncClient.endDay(
                        getApplicationContext(),
                        serverDayId,
                        todayPrefs,
                        bus,
                        total,
                        other,
                        dsl,
                        wage,
                        meal,
                        maint,
                        otherExp,
                        null
                );
            });
        });

        btn_skip_waybill.setOnClickListener(v -> {

            AppExecutors.runBg(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                String today = Prefs.getDayDate(this);

                int serverDayId = Prefs.getCurrentServerDayID(this);
                String todayPrefs = Prefs.getDayDate(this);
                String bus = Prefs.getSelectedBus(this);

                String currentTime = new SimpleDateFormat(
                        "HH:mm:ss", Locale.getDefault()
                ).format(new Date());

                // 1️⃣ Get current day row
                TicketDayEntity day = db.ticketDao().getLastDay();
                if (day == null){
                    Prefs.clearDay(this);
                    Prefs.clearSelectedBus(this);
                    runOnUiThread(() -> {
                        startActivity(new Intent(Day.this, Menu.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        finish();
                    });
                    return;
                }
                db.ticketDao().updateEndTime(currentTime, day.id);
                Prefs.clearSelectedBus(this);
                Prefs.clearDay(this);
                LocationForegroundService.sendOnDayEnd(getApplicationContext());

                // 3️⃣ Check if a waybill already exists
                DayWaybillEntity existing = db.dayWaybillDao().getForDay(day.id);

                // 2️⃣ Get total ticket amount
                Integer total = db.ticketDao().getTotalAmountForDayId(day.id);
                if (total == null) total = 0;

                // 4️⃣ Create or update waybill object
                DayWaybillEntity waybill = (existing != null) ? existing : new DayWaybillEntity();
                waybill.dayId         = day.id;
                waybill.totalReceipt  = total;
                waybill.otherReceipt  = 0;
                waybill.diesel        = (float) 0;
                waybill.wages         = (float) 0;
                waybill.wellMeal      = (float) 0;
                waybill.maintenance   = (float) 0;
                waybill.otherExpenses = (float) 0;

                // 5️⃣ Insert or Update
                if (existing != null) {
                    db.dayWaybillDao().update(waybill);
                } else {
                    db.dayWaybillDao().insert(waybill);
                }

                runOnUiThread(() -> {
                    Toast.makeText(Day.this, "Waybill skipped. Day ended.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(Day.this, Menu.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                });

                SyncClient.endDay(
                        getApplicationContext(),
                        serverDayId,
                        todayPrefs,
                        bus,
                        total,
                        0, 0, 0, 0, 0, 0,
                        null
                );
            });


        });


        // 4️⃣ “END DAY” button clears prefs and goes back to bus‐selection
        btnEndDay.setOnClickListener(v -> {

            if(Prefs.getTripStatus(this) == 0) {
                calc_totalReceipt(total_receipt);
                layoutWaybill.setVisibility(View.VISIBLE);
                layoutEndDay.setVisibility(View.GONE);

            }
            else{

                String msg = "Trip not ended yet!";
                SpannableString sizedMsg = new SpannableString(msg);
                // the second parameter "true" means "treat the 18 as SP, not pixels"
                sizedMsg.setSpan(
                        new AbsoluteSizeSpan(48, true),  // 18sp text size
                        0,                                // start
                        msg.length(),                     // end
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                );
                sizedMsg.setSpan(
                        new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                        0, msg.length(),
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                );

                AlertDialog dialog = new AlertDialog.Builder(Day.this)
                        .setMessage(sizedMsg)
                        .setPositiveButton("OK", null)
                        .show();

                // 🚫 Prevent dismiss when touching outside or pressing Back
                dialog.setCanceledOnTouchOutside(false);
                dialog.setCancelable(false);

                Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

                // • Increase text size to 20sp
                okButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

                // • Change text color to white (for contrast)
                okButton.setTextColor(Color.WHITE);

                // • Paint its background blue
                okButton.setBackgroundColor(Color.BLUE);

                // (Optional) If you want rounded corners or padding:
                int pad = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()
                );
                okButton.setPadding(pad, pad, pad, pad);
                okButton.setAllCaps(false);
                LinearLayout parent = (LinearLayout) okButton.getParent();
                parent.setGravity(Gravity.CENTER);

                // (Optional) If you want the button to stretch across and then center its text:
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) okButton.getLayoutParams();
                params.width = LinearLayout.LayoutParams.MATCH_PARENT;
                okButton.setLayoutParams(params);
                okButton.setGravity(Gravity.CENTER);

                TextView messageView = dialog.findViewById(android.R.id.message);
                if (messageView != null) {
                    // 3️⃣ convert 16dp to pixels
                    int extraPx = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            16,
                            getResources().getDisplayMetrics()
                    );

                    // 4️⃣ add it to the bottom padding
                    messageView.setPadding(
                            messageView.getPaddingLeft(),
                            messageView.getPaddingTop(),
                            messageView.getPaddingRight(),
                            messageView.getPaddingBottom() + extraPx
                    );
                }

                dialog.getWindow().setBackgroundDrawable(
                        new ColorDrawable(Color.parseColor("#FFAA00"))
                );
            }
        });
    }

    private Integer parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private Float parseFloatSafe(String value) {
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception e) {
            return 0f;
        }
    }


    private void calc_totalReceipt(@Nullable TextView targetView) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            TicketDayEntity day = db.ticketDao().getLastDay();

            // 🛑 If no active day found → clear prefs and go to Menu
            if (day == null) {
                runOnUiThread(() -> {
                    Prefs.clearDay(this);
                    Intent intent = new Intent(this, Menu.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                });
                return; // Stop further execution
            }

            // ✅ Normal flow when day exists
            int total = db.ticketDao().getTotalAmountForDayId(day.id);
            if (total < 0) total = 0; // Safety fallback if query returns NULL

            int finalTotal = total;
            runOnUiThread(() -> {
                if (targetView != null) {
                    targetView.setText(String.valueOf(finalTotal));
                }
            });
        });
    }


    /** Loads the bus‐list from Room, then wires up the UI controls */
    private void initializeBusSelection() {
        Executors.newSingleThreadExecutor().execute(() -> {
            // 🚍 Fetch all buses
            List<BusEntity> entities = AppDatabase
                    .getInstance(getApplicationContext())
                    .busDao()
                    .getAllBusesLive();

            busNos.clear();
            for (BusEntity b : entities) {
                busNos.add(b.busNo);
            }

            // ▶️ If user had a saved bus, start there
            String saved = Prefs.getSelectedBus(this);
            if (saved != null) {
                int idx = busNos.indexOf(saved);
                if (idx >= 0) currentIndex = idx;
                else           currentIndex = 0;
            } else {
                currentIndex = 0;
            }

            // 🔄 Back to main thread to update UI
            runOnUiThread(() -> {
                updateBusDisplay();

                // ⬅️ Navigate left
                btnLeft.setOnClickListener(v -> {
                    if (currentIndex > 0) {
                        currentIndex--;
                        updateBusDisplay();
                    }
                });

                // ➡️ Navigate right
                btnRight.setOnClickListener(v -> {
                    if (currentIndex < busNos.size() - 1) {
                        currentIndex++;
                        updateBusDisplay();
                    }
                });

                // ✅ Submit / Start Day
                btnSubmit.setOnClickListener(v -> {

                    String selectedBus = busNos.get(currentIndex);

                    Prefs.saveSelectedBus(this, selectedBus);

                    String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                            .format(new Date());
                    String todayPrefs = today;

                    Prefs.SaveDay(this, today, 1);

                    Log.d("DayStatus", "Day Started (local)");

                    // Navigate immediately — do not wait for create_day HTTP (was 1–2s+ on Menu open).
                    returnToMenuAfterStartDay(null);

                    final String startTime = new SimpleDateFormat(
                            "HH:mm:ss", Locale.getDefault()
                    ).format(new Date());
                    final Context app = getApplicationContext();

                    AppExecutors.runBg(() -> {
                        try {
                            AppDatabase db = AppDatabase.getInstance(app);
                            TicketDayEntity dayEntity = new TicketDayEntity();
                            dayEntity.date = today;
                            dayEntity.busNo = selectedBus;
                            dayEntity.start_time = startTime;
                            long thisdayId = db.ticketDao().insertDay(dayEntity);

                            ReportsEntity re = new ReportsEntity();
                            re.dayId = (int) thisdayId;
                            re.date = today;
                            re.current_trip_report = 0;
                            re.all_trip_reports = 0;
                            re.custom_report = 0;
                            re.summary_report = 0;
                            db.reportCountDao().insertCount(re);
                            LocationForegroundService.sendNow(
                                    app, PositioningSnapshot.dayStart(selectedBus));
                        } catch (Exception e) {
                            Log.e("Day", "Local day insert failed", e);
                        }

                        SyncClient.createDay(app, todayPrefs, selectedBus, new SyncClient.Callback() {
                            @Override public void onSuccess(JSONObject resp) { /* server id saved in SyncClient */ }

                            @Override public void onFail(String reason) {
                                if (reason == null || reason.isEmpty()) return;
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                        Toast.makeText(app,
                                                "Day started locally. Server sync will retry.",
                                                Toast.LENGTH_LONG).show());
                            }
                        });
                    });
                });
            });
        });
    }

    /** After create_day completes (or fails / queued); optional toast if sync failed. */
    private void returnToMenuAfterStartDay(@Nullable String syncFailReason) {
        if (syncFailReason != null && !syncFailReason.isEmpty()) {
            Toast.makeText(this,
                    "Day started locally. Server sync: " + syncFailReason + " (will retry if queued)",
                    Toast.LENGTH_LONG).show();
        }
        startActivity(new Intent(Day.this, Menu.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    /** Updates the displayed bus and arrow‐button states */
    /** Updates the displayed bus and arrow‐button states */
    private void updateBusDisplay() {
        if (busNos.isEmpty()) {
            viewBus.setText("N/A");             // or "" / placeholder
            btnLeft.setEnabled(false);
            btnRight.setEnabled(false);
            btnSubmit.setEnabled(false);
            return;
        }
        viewBus.setText(busNos.get(currentIndex));
        btnLeft  .setEnabled(currentIndex > 0);
        btnRight .setEnabled(currentIndex < busNos.size() - 1);
        btnSubmit.setEnabled(true);
    }

}
