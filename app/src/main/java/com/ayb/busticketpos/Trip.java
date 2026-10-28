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

            AppExecutors.runBg(() -> {
                AppDatabase db = AppDatabase.getInstance(this);

                try {
                    TripEntity currentTrip = db.ticketDao().getLastTrip();
                    if (currentTrip != null) {
                        db.ticketDao().endTrip(currentTime, currentTrip.tripId);
                    }

                    final int tripId = Prefs.getCurrentServerTripID(this);
                    final int dayId  = Prefs.getCurrentServerDayID(this);
                    final int tripNo = Prefs.getTripCount(this);
                    final String busNo = Prefs.getSelectedBus(this);

                    Prefs.clearTrip(this);

                    runOnUiThread(() -> {
                        LocationForegroundService.sendNow(
                                getApplicationContext(),
                                PositioningSnapshot.tripEnded(busNo));
                        startActivity(new Intent(Trip.this, Menu.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        finish();
                    });

                    SyncClient.endTrip(
                            getApplicationContext(),
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
                                    AppLog.e("Trip", "Trip sync failed: " + reason);
                                }
                            }
                    );

                } catch (Exception e) {
                    AppLog.e("Trip", "Error ending trip", e);
                    runOnUiThread(() -> {
                        startActivity(new Intent(Trip.this, Menu.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        finish();
                    });
                }
            });
        });

    }

    /** Step A: Load routes and wire the route-chooser UI (no Prefs writes here) */
    private void initializeRouteSelection() {
        AppExecutors.runBg(() -> {
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
                    if (saved.equals(routeList.get(i).routeId)) {
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

                    if(!today.equals(Prefs.getDayDate(this))){
                        AppDialog.with(Trip.this)
                                .type(AppDialog.Type.WARNING)
                                .title("Day Expired!")
                                .message("End Current Day and Start a New One")
                                .positive("OK", () -> {
                                    startActivity(new Intent(Trip.this, Menu.class));
                                    finish();
                                })
                                .cancelable(false)
                                .show();


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

        AppExecutors.runBg(() -> {
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

                    AppExecutors.runBg(() -> {
                        TicketDayEntity day = db.ticketDao().getLastDay();

                        if (day == null) {
                            runOnUiThread(() ->
                                    Toast.makeText(Trip.this, "⚠️ Cannot start trip – No matching Day record found.", Toast.LENGTH_LONG).show());
                            return;
                        }

                        int count = Prefs.getTripCount(Trip.this) + 1;
                        Prefs.saveTripCount(Trip.this, count);
                        Prefs.saveTripStatus(Trip.this, 1);

                        TripEntity trip = new TripEntity();
                        trip.dayId = day.id;
                        trip.tripNo = count;
                        trip.route = chosenRoute.routeName;
                        trip.direction = selectedDirection;
                        trip.start_time = currentTime;
                        db.ticketDao().insertTrip(trip);

                        final int serverDayId = Prefs.getCurrentServerDayID(Trip.this);
                        final String routeName = chosenRoute.routeName;
                        final String direction = selectedDirection;

                        runOnUiThread(() -> {
                            String busNo = Prefs.getSelectedBus(Trip.this);
                            LocationForegroundService.sendNow(
                                    getApplicationContext(),
                                    PositioningSnapshot.tripStart(busNo, routeName, count));
                            startActivity(new Intent(Trip.this, Menu.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                            finish();
                        });

                        SyncClient.startTrip(
                                getApplicationContext(),
                                serverDayId,
                                count,
                                routeName,
                                direction,
                                null
                        );
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
