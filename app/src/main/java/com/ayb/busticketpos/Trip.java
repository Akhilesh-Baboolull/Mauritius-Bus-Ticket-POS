package com.ayb.busticketpos;

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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class Trip extends AppCompatActivity {

    // Layout containers
    private LinearLayout layoutRoute, layoutDirection, layoutEndTrip;

    // 1) Route selection
    private TextView viewRoute;
    private Button   btnLeft, btnRight, btnSubmitRoute, btnBackRoute;
    private List<RouteEntity> routeList = new ArrayList<>();
    private int currentRouteIndex = 0;

    // 2) Direction selection
    private Button btnDir1, btnDir2, btnSubmitDirection, btnBackDirection;
    private boolean directionSelected = false;
    private String  selectedDirection = null;

    // 3) End‐trip
    private Button btnEndTrip, btnBackEnd;

    private int directionValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip);

        // 1️⃣ Find all views
        layoutRoute       = findViewById(R.id.layout_route);
        layoutDirection   = findViewById(R.id.layout_direction);
        layoutEndTrip     = findViewById(R.id.layout_end_trip);

        viewRoute         = findViewById(R.id.view_route);
        btnLeft           = findViewById(R.id.button_left);
        btnRight          = findViewById(R.id.button_right);
        btnSubmitRoute    = findViewById(R.id.button_submit);
        btnBackRoute      = findViewById(R.id.button_back);

        btnDir1           = findViewById(R.id.direction1);
        btnDir2           = findViewById(R.id.direction2);
        btnSubmitDirection= findViewById(R.id.button_submit_direction);
        btnBackDirection  = findViewById(R.id.button_back3);

        btnEndTrip        = findViewById(R.id.button_end_trip);
        btnBackEnd        = findViewById(R.id.button_back2);

        // 2️⃣ Wire all “Back” buttons to finish()
        View.OnClickListener finishListener = v -> {
            startActivity(new Intent(Trip.this, Menu.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();

        };
        btnBackRoute     .setOnClickListener(finishListener);
        btnBackDirection .setOnClickListener(v -> {
            layoutRoute.setVisibility(View.VISIBLE);
            layoutDirection.setVisibility(View.GONE);
            layoutEndTrip.setVisibility(View.GONE);
        });
        btnBackEnd       .setOnClickListener(finishListener);

        // 3️⃣ Check existing trip status
        if (Prefs.getTripStatus(this) == 1) {
            // mid‐trip → show end‐trip screen
            layoutRoute    .setVisibility(View.GONE);
            layoutDirection.setVisibility(View.GONE);
            layoutEndTrip  .setVisibility(View.VISIBLE);
        } else {
            // start‐trip → show route chooser
            layoutRoute    .setVisibility(View.VISIBLE);
            layoutDirection.setVisibility(View.GONE);
            layoutEndTrip  .setVisibility(View.GONE);
            initializeRouteSelection();
        }

        // 4️⃣ End‐trip button clears status
        btnEndTrip.setOnClickListener(v -> {
            String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(this);

                try {
                    // 🔹 Get current trip
                    TripEntity currentTrip = db.ticketDao().getLastTrip();
                    if (currentTrip != null) {
                        // 🔹 Mark trip as ended
                        db.ticketDao().endTrip(currentTime, currentTrip.tripId);
                    }

                    // 🔹 Get pref values
                    int tripId = Prefs.getCurrentServerTripID(this);
                    int dayId  = Prefs.getCurrentServerDayID(this);
                    int tripNo = Prefs.getTripCount(this);

                    // 🔹 Call SyncClient in background (no main thread blocking)
                    SyncClient.endTrip(
                            this,
                            tripId,
                            dayId,
                            tripNo,
                            new SyncClient.Callback() {
                                @Override
                                public void onSuccess(org.json.JSONObject resp) {
                                    Log.d("Trip", "Trip sync success: " + resp);
                                }

                                @Override
                                public void onFail(String reason) {
                                    Log.e("Trip", "Trip sync failed: " + reason);
                                }
                            }
                    );

                    // 🔹 Send location update
                    LocationForegroundService.sendNow(this);

                    // 🔹 Clear trip prefs
                    Prefs.clearTrip(this);

                    // 🔹 Return to Menu activity (on UI thread)
                    runOnUiThread(() -> {
                        Intent i = new Intent(Trip.this, Menu.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(i);
                        finish();
                    });

                } catch (Exception e) {
                    Log.e("Trip", "Error ending trip", e);
                }
            });
        });

    }

    /** Step A: Load routes and wire the route-chooser UI (no Prefs writes here) */
    private void initializeRouteSelection() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<RouteEntity> routes = AppDatabase
                    .getInstance(getApplicationContext())
                    .routeDao()
                    .getAllRoutesSync();

            routeList.clear();
            routeList.addAll(routes);

            // restore last‐chosen route if any
            String saved = Prefs.getSelectedRoute(this);
            if (saved != null) {
                for (int i = 0; i < routeList.size(); i++) {
                    if (routeList.get(i).routeId.equals(saved)) {
                        currentRouteIndex = i;
                        break;
                    }
                }
            } else {
                currentRouteIndex = 0;
            }

            runOnUiThread(() -> {
                updateRouteDisplay();
                btnLeft .setOnClickListener(v -> {
                    if (currentRouteIndex > 0) {
                        currentRouteIndex--;
                        updateRouteDisplay();
                    }
                });
                btnRight.setOnClickListener(v -> {
                    if (currentRouteIndex < routeList.size() - 1) {
                        currentRouteIndex++;
                        updateRouteDisplay();
                    }
                });
                btnSubmitRoute.setOnClickListener(v -> {
                    // Move to direction screen—no PREFS writes yet

                    String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());

                    if(!Prefs.getDayDate(this).equals(today)){
                        String msg = "Day Expired! End Current Day and Start a New One";
                        SpannableString sizedMsg = new SpannableString(msg);
                        // the second parameter "true" means "treat the 18 as SP, not pixels"
                        sizedMsg.setSpan(
                                new AbsoluteSizeSpan(28, true),  // 18sp text size
                                0,                                // start
                                msg.length(),                     // end
                                Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        );
                        sizedMsg.setSpan(
                                new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                                0, msg.length(),
                                Spanned.SPAN_INCLUSIVE_INCLUSIVE
                        );

                        AlertDialog dialog = new AlertDialog.Builder(Trip.this)
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

                        // ✅ Add click listener for OK button
                        okButton.setOnClickListener(view -> {
                            // Example: Redirect to Menu Activity
                             Intent i = new Intent(Trip.this, Menu.class);
                             startActivity(i);
                             finish();
                        });


                    }

                    layoutRoute   .setVisibility(View.GONE);
                    layoutDirection.setVisibility(View.VISIBLE);
                    setupDirectionSelection(routeList.get(currentRouteIndex).routeId);
                });
            });
        });
    }

    /** Step B: After route chosen, configure the two direction buttons */
    private void setupDirectionSelection(String routeId) {
        directionSelected = false;
        selectedDirection = null;
        directionValue = 0;
        String SelectedDirection = "";

        Executors.newSingleThreadExecutor().execute(() -> {
            List<StageEntity> stages = AppDatabase
                    .getInstance(getApplicationContext())
                    .stageDao()
                    .getStagesForRouteSync(routeId);

            if (stages == null || stages.isEmpty()) {
                runOnUiThread(() -> {
                    // show the previous screen again
                    layoutRoute.setVisibility(View.VISIBLE);
                    layoutDirection.setVisibility(View.GONE);
                    layoutEndTrip.setVisibility(View.GONE);
                    Toast.makeText(Trip.this, "No stages for this route", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            String first = stages.get(0).stageName;
            String last  = stages.get(stages.size()-1).stageName;

            runOnUiThread(() -> {
                // Reset alphas
                btnDir1.setAlpha(1f);
                btnDir2.setAlpha(1f);

                btnDir1.setText(first + "  TO  " + last);
                btnDir2.setText(last  + "  TO  " + first);

                btnDir1.setOnClickListener(v -> {
                    directionSelected = true;
                    selectedDirection = btnDir1.getText().toString();
                    directionValue    = 1;       // ← forward
                    btnDir1.setAlpha(1f);
                    btnDir2.setAlpha(0.5f);
                });
                btnDir2.setOnClickListener(v -> {
                    directionSelected = true;
                    selectedDirection = btnDir2.getText().toString();
                    directionValue    = -1;      // ← backward
                    btnDir2.setAlpha(1f);
                    btnDir1.setAlpha(0.5f);
                });

                btnSubmitDirection.setOnClickListener(v -> {
                    if (!directionSelected) {
                        Toast.makeText(this, "Please select a direction", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 1️⃣ Save selected route & direction into Prefs
                    RouteEntity chosenRoute = routeList.get(currentRouteIndex);
                    Prefs.saveSelectedRoute(this, chosenRoute.routeId);
                    Prefs.saveSelectedRouteName(this, chosenRoute.routeName);
                    Prefs.saveRouteDirection(this, directionValue);
                    Prefs.saveDirectionName(this, selectedDirection);
                    Prefs.saveCurrentStageID(this, 0);  // reset to stage 0

                    // 2️⃣ Prepare database access
                    AppDatabase db = AppDatabase.getInstance(this);

                    String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

                    Executors.newSingleThreadExecutor().execute(() -> {
                        TicketDayEntity day = db.ticketDao().getLastDay();

                        if (day == null) {
                            runOnUiThread(() ->
                                    Toast.makeText(this, "⚠️ Cannot start trip – No matching Day record found.", Toast.LENGTH_LONG).show());
                            return;
                        }

                        // 3️⃣ Increment & save trip number in Prefs
                        int count = Prefs.getTripCount(this) + 1;
                        Prefs.saveTripCount(this, count);
                        Prefs.saveTripStatus(this, 1);  // mark trip "in progress"

                        // 4️⃣ Create and insert new TripEntity
                        TripEntity trip = new TripEntity();
                        trip.dayId = day.id;
                        trip.tripNo = count;
                        trip.route = chosenRoute.routeName;
                        trip.direction = selectedDirection;
                        trip.start_time = currentTime;


                        db.ticketDao().insertTrip(trip);

                        // send to server
                        int serverDayId = Prefs.getCurrentServerDayID(this); // must be set from create_day success
                        SyncClient.startTrip(
                                this,
                                serverDayId,
                                count,
                                chosenRoute.routeName,
                                selectedDirection,
                                new SyncClient.Callback() {
                                    @Override public void onSuccess(org.json.JSONObject resp) {
                                        // Optional: Log.d("SYNC_TRIP", "startTrip OK: " + resp);
                                    }
                                    @Override public void onFail(String reason) {
                                        // Optional: Log.e("SYNC_TRIP", "startTrip FAIL: " + reason);
                                    }
                                }
                        );

                        // 5️⃣ Launch Menu Activity
                        runOnUiThread(() -> {
                            LocationForegroundService.sendNow(this);
                            startActivity(new Intent(Trip.this, Menu.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                            finish();
                        });
                    });
                });


            });
        });
    }

    /** Updates the route text & arrow buttons */
    private void updateRouteDisplay() {
        if (routeList.isEmpty()) {
            viewRoute       .setText("No routes");
            btnLeft         .setEnabled(false);
            btnRight        .setEnabled(false);
            btnSubmitRoute  .setEnabled(false);
        } else {
            String name = routeList.get(currentRouteIndex).routeName;
            viewRoute       .setText(name);
            btnLeft         .setEnabled(currentRouteIndex > 0);
            btnRight        .setEnabled(currentRouteIndex < routeList.size() - 1);
            btnSubmitRoute  .setEnabled(true);
        }
    }
}
