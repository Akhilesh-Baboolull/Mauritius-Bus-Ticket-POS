package com.ayb.busticketpos;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.zcs.sdk.DriverManager;
import com.zcs.sdk.Printer;
import com.zcs.sdk.print.PrnStrFormat;
import com.zcs.sdk.print.PrnTextFont;
import com.zcs.sdk.print.PrnTextStyle;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;

public class Report extends AppCompatActivity {

    private LinearLayout main_layout, trip_report_layout, print_layout, back_layout;

    private TextView selected_trip;

    private int custom_tripNo = -1;

    RadioGroup report_type;
    String rb_tag = null;
    RadioButton rb_custom;

    private Printer       mPrinter;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        DriverManager mDriverManager = DriverManager.getInstance();
        mPrinter       = mDriverManager.getPrinter();

        report_type = findViewById(R.id.report_type);
        Button trip_report = findViewById(R.id.trip_report);
        Button summary_report = findViewById(R.id.summary_report);
        Button btn_back1 = findViewById(R.id.btn_back1);
        Button btn_back2 = findViewById(R.id.btn_back2);
        Button btn_back3 = findViewById(R.id.btn_back3);
        Button btn_print = findViewById(R.id.btn_print);
        rb_custom = findViewById(R.id.radioButton3);

        main_layout = findViewById(R.id.main_layout);
        trip_report_layout = findViewById(R.id.trip_report_layout);
        print_layout = findViewById(R.id.layout_print);
        back_layout = findViewById(R.id.layout_back);
        selected_trip = findViewById(R.id.selected_trip);

        trip_report.setOnClickListener(v -> {
            main_layout.setVisibility(View.GONE);
            trip_report_layout.setVisibility(View.VISIBLE);
            back_layout.setVisibility(View.VISIBLE);
        });

        rb_custom.setOnClickListener(v -> show_all_trips());

        report_type.setOnCheckedChangeListener((group, checkedId) -> {

            RadioButton rb = findViewById(checkedId);
            rb_tag = rb.getTag().toString();

            if(!rb_tag.equals("custom_trip")){
                selected_trip.setText("");
            }

            back_layout.setVisibility(View.GONE);
            print_layout.setVisibility(View.VISIBLE);

        });

        btn_back1.setOnClickListener(v -> back_action2());
        btn_back2.setOnClickListener(v -> back_action());
        btn_back3.setOnClickListener(v -> back_action2());

        summary_report.setOnClickListener(v -> {

            int day_status = Prefs.getDayStatus(this);

            if(day_status == 1){
                showWarningDialog();
            }
            else{
                print_collection_report();
            }

        });


        btn_print.setOnClickListener(v -> {
            switch (rb_tag) {
                case "current_trip" -> {
                    if (Prefs.getTripStatus(this) == 1) {
                        Prefs.incrementCurrentTrip(this);
                        current_trip_report();

                    } else {
                        String msg = "No current Trip!";
                        error_dialog(msg);
                    }
                }
                case "all_trips" -> {
                    Prefs.incrementAllTrips(this);
                    all_trips_report();
                }
                case "custom_trip" -> {

                    if (custom_tripNo == -1) {
                        String msg = "No Trip Selected!";
                        error_dialog(msg);
                        return;
                    }
                    Prefs.incrementCustomTrip(this);
                    custom_trip_report(custom_tripNo);
                }
            }
        });

    }

    private void error_dialog(String msg){

        SpannableString sizedMsg = new SpannableString(msg);
        // the second parameter "true" means "treat the 18 as SP, not pixels"
        sizedMsg.setSpan(
                new AbsoluteSizeSpan(34, true),
                0,                                // start
                msg.length(),                     // end
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
        );
        sizedMsg.setSpan(
                new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                0, msg.length(),
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
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
        params.width = LinearLayout.LayoutParams.WRAP_CONTENT;
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
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(
                new ColorDrawable(Color.parseColor("#FFAA00"))
        );


    }

    private void print_collection_report() {

        Prefs.incrementSummary(this);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            TicketDayEntity thisday = db.ticketDao().getLastDay();

            if (thisday != null) {
                db.reportCountDao().recordSummaryReportCount(
                        Prefs.getSummaryTripCount(this),
                        thisday.id
                );
            } else {
                android.util.Log.w("Report", "No TicketDayEntity found — skipping recordSummaryReportCount()");
                return;
            }

//            db.reportCountDao().recordSummaryReportCount(Prefs.getSummaryTripCount(this), thisday.id);

            String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());
            List<TicketDayEntity> allDays = db.ticketDao().getDaysForDate(today);
            if (allDays == null || allDays.isEmpty()) {
                Log.w("Report", "No day entries found for today");
                return;
            }

            ReportsCountSummary reportsCount = db.reportCountDao().getReportsCountForDate(today);

            List<DaySummary> daySummaries = new ArrayList<>();
            int shiftCount = 0;

            for (TicketDayEntity day : allDays) {
                shiftCount++;

                List<TripEntity> allTrips = db.ticketDao().getTripsForDay(day.id);
                DayWaybillEntity waybill = db.dayWaybillDao().getForDay(day.id);



                List<TripSummary> allSummaries = new ArrayList<>();

                for (TripEntity trip : allTrips) {
                    int tripId = trip.tripId;
                    List<TicketEntity> tickets = db.ticketDao().getTicketsForTrip(tripId);

                    TripSummary summary = new TripSummary();
                    summary.tripNo = trip.tripNo;
                    summary.route = trip.route;
                    summary.routeDir = trip.direction;
                    summary.startTime = trip.start_time;
                    summary.endTime = (trip.end_time != null) ? trip.end_time : "-";

                    if (!tickets.isEmpty()){

                        TicketEntity firstTicket = tickets.get(0);
                        TicketEntity lastTicket = tickets.get(tickets.size() - 1);

                        summary.startTicketNo = firstTicket.ticketNo;
                        summary.endTicketNo = lastTicket.ticketNo;

                        for (TicketEntity t : tickets) {
                            switch (t.fareType.toLowerCase()) {
                                case "adult":
                                    summary.adultCount++;
                                    summary.adultTotal += t.amount;
                                    break;
                                case "student":
                                    summary.studentCount++;
                                    summary.studentTotal += t.amount;
                                    break;
                                case "child":
                                    summary.childCount++;
                                    summary.childTotal += t.amount;
                                    break;
                            }
                            summary.tripTotal += t.amount;
                        }
                    } else {
                        // 🆕 No tickets — fill with zeros & placeholders
                        summary.startTicketNo = "-";
                        summary.endTicketNo = "-";
                        summary.adultCount = 0;
                        summary.studentCount = 0;
                        summary.childCount = 0;
                        summary.tripTotal = 0;
                    }
                    // 🆕 Add blank ticket count for this trip
                    summary.blankCount = db.blankTicketDao().getCountForTrip(tripId);

                    allSummaries.add(summary);
                }

                DaySummary ds = new DaySummary();
                ds.dayId = day.id;
                ds.date = day.date;
                ds.busNo = day.busNo;
                ds.shiftNo = shiftCount;
                ds.tripSummaries = allSummaries;
                ds.waybill = waybill;

                daySummaries.add(ds);
            }

            runOnUiThread(() -> collection_report_printer(daySummaries, reportsCount));
        });
    }


    private void collection_report_printer(List<DaySummary> daySummaries, ReportsCountSummary dayReportCount) {
        // Show "Printing..." dialog
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Printing...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Run the entire print logic in background thread
        new Thread(() -> {
            try {
                String currentDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
                String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

                int total_reports;

                float grand_total = 0f;
                PrnStrFormat fmt = new PrnStrFormat();

                // 🌐 Header
                fmt.setTextSize(26);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                fmt.setFont(PrnTextFont.SANS_SERIF);
                mPrinter.setPrintAppendString("* "+ PrefsSecure.getTenantName(this) +" *", fmt);

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("=== COLLECTION REPORT ===", fmt);

                mPrinter.setPrintAppendString("BTM-" + PrefsSecure.getMachineId(this), fmt);

                fmt.setTextSize(24);
                fmt.setStyle(PrnTextStyle.NORMAL);
                fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
                mPrinter.setPrintAppendString("DT: " + currentDate + "   TM: " + currentTime, fmt);

                String dayDate = "";
                int dayAdultCount = 0, dayStudentCount = 0, dayChildCount = 0, dayBlankCount = 0;
                int dayAdultAmt = 0, dayStudentAmt = 0, dayChildAmt = 0;
                int numOfTickets;
                
                // 🔁 Each shift
                for (DaySummary daySummary : daySummaries) {
                    dayDate = daySummary.date;
                    float shiftTotal = 0f;

                    fmt.setTextSize(32);
                    fmt.setStyle(PrnTextStyle.BOLD);
                    fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                    mPrinter.setPrintAppendString("\n=== SHIFT " + daySummary.shiftNo + " ===", fmt);

                    fmt.setTextSize(22);
                    fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                    mPrinter.setPrintAppendString(daySummary.busNo, fmt);

                    fmt.setAli(Layout.Alignment.ALIGN_NORMAL);

                    for (TripSummary trip : daySummary.tripSummaries) {
                        fmt.setTextSize(30);
                        fmt.setStyle(PrnTextStyle.BOLD);
                        mPrinter.setPrintAppendString("--------------------------------", fmt);

                        fmt.setTextSize(34);
                        fmt.setStyle(PrnTextStyle.BOLD);
                        mPrinter.setPrintAppendString("TRIP No: " + trip.tripNo, fmt);

                        fmt.setTextSize(28);
                        fmt.setStyle(PrnTextStyle.BOLD);
                        mPrinter.setPrintAppendString("Route: " + trip.route, fmt);

                        fmt.setTextSize(28);
                        fmt.setStyle(PrnTextStyle.BOLD);
                        mPrinter.setPrintAppendString(trip.routeDir, fmt);

                        fmt.setTextSize(30);
                        fmt.setStyle(PrnTextStyle.NORMAL);
                        mPrinter.setPrintAppendString("DT: " + daySummary.date, fmt);
                        mPrinter.setPrintAppendString("TM: " + trip.startTime + "  -  " + trip.endTime, fmt);

                        if(trip.startTicketNo.equals("-")){
                            mPrinter.setPrintAppendString("No Tickets", fmt);
                        }
                        else {
                            mPrinter.setPrintAppendString("TICKETS          : " + trip.startTicketNo + "  TO  " + trip.endTicketNo, fmt);
                            mPrinter.setPrintAppendString("ADULT               : " + trip.adultCount + "     Rs. " + trip.adultTotal, fmt);
                            mPrinter.setPrintAppendString("CHILD                : " + trip.childCount + "     Rs. " + trip.childTotal, fmt);
                            mPrinter.setPrintAppendString("STUDENT        : " + trip.studentCount + "     Rs. " + trip.studentTotal, fmt);
                            mPrinter.setPrintAppendString("BLANK              : " + trip.blankCount, fmt);
                        }

                        fmt.setTextSize(30);
                        fmt.setStyle(PrnTextStyle.BOLD);
                        mPrinter.setPrintAppendString("--------------------------------", fmt);

                        fmt.setTextSize(30);
                        fmt.setStyle(PrnTextStyle.NORMAL);
                        mPrinter.setPrintAppendString("TOTAL     : Rs. " + trip.tripTotal, fmt);

                        shiftTotal += trip.tripTotal;

                        // ✅ Accumulate day totals
                        dayAdultCount += trip.adultCount;
                        dayAdultAmt   += trip.adultTotal;

                        dayStudentCount += trip.studentCount;
                        dayStudentAmt   += trip.studentTotal;

                        dayChildCount += trip.childCount;
                        dayChildAmt   += trip.childTotal;

                        dayBlankCount += trip.blankCount;

                    }

                    fmt.setTextSize(30);
                    fmt.setStyle(PrnTextStyle.BOLD);
                    mPrinter.setPrintAppendString("--------------------------------", fmt);
                    fmt.setTextSize(32);
                    mPrinter.setPrintAppendString("SHIFT " + daySummary.shiftNo + " TOTAL : Rs. " + shiftTotal, fmt);
                    fmt.setTextSize(30);
                    mPrinter.setPrintAppendString("--------------------------------", fmt);

                    grand_total += shiftTotal;
                }

                
                // 🌐 Footer
                fmt.setTextSize(26);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                fmt.setFont(PrnTextFont.SANS_SERIF);
                mPrinter.setPrintAppendString("* "+ PrefsSecure.getTenantName(this) +" *", fmt);

                //Summary of the day
                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                mPrinter.setPrintAppendString("=== Summary ===", fmt);

                fmt.setAli(Layout.Alignment.ALIGN_NORMAL);

                numOfTickets = dayAdultCount + dayChildCount + dayStudentCount + dayBlankCount;

                fmt.setTextSize(29);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("Total No. of Tickets", fmt);

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.NORMAL);
                mPrinter.setPrintAppendString("DT: " + dayDate, fmt);
                mPrinter.setPrintAppendString("TICKETS          : " + numOfTickets, fmt);
                mPrinter.setPrintAppendString("ADULT               : " + dayAdultCount + "     Rs. " + dayAdultAmt, fmt);
                mPrinter.setPrintAppendString("CHILD                : " + dayChildCount + "     Rs. " + dayChildAmt, fmt);
                mPrinter.setPrintAppendString("STUDENT        : " + dayStudentCount + "     Rs. " + dayStudentAmt, fmt);
                mPrinter.setPrintAppendString("BLANK              : " + dayBlankCount, fmt);

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("--------------------------------", fmt);

                fmt.setTextSize(29);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("Number of Reports Generated", fmt);

                total_reports = dayReportCount.current_trip_report + dayReportCount.all_trip_reports + dayReportCount.custom_report + dayReportCount.summary_report;

                fmt.setTextSize(28);
                fmt.setStyle(PrnTextStyle.NORMAL);
                mPrinter.setPrintAppendString("No. of Total Reports                   : " + total_reports, fmt);
                mPrinter.setPrintAppendString("No. of Current Trip Reports      : " + dayReportCount.current_trip_report, fmt);
                mPrinter.setPrintAppendString("No. of All Trips Reports             : " + dayReportCount.all_trip_reports, fmt);
                mPrinter.setPrintAppendString("No. of Custom Trips Reports  : " + dayReportCount.custom_report, fmt);
                mPrinter.setPrintAppendString("No. of Summary Reports         : " + dayReportCount.summary_report, fmt);

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("--------------------------------", fmt);

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                mPrinter.setPrintAppendString("=== COLLECTION REPORT ===", fmt);

                fmt.setTextSize(24);
                fmt.setStyle(PrnTextStyle.NORMAL);
                fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
                mPrinter.setPrintAppendString("DT: " + currentDate + "   TM: " + currentTime, fmt);

                float totalReceipt = 0f, otherReceipt = 0f, diesel = 0f, wages = 0f,
                        wellMeal = 0f, maintenance = 0f, otherExpenses = 0f;

                for (DaySummary ds : daySummaries) {
                    if (ds.waybill != null) {
                        totalReceipt += ds.waybill.totalReceipt;
                        otherReceipt += ds.waybill.otherReceipt;
                        diesel += ds.waybill.diesel;
                        wages += ds.waybill.wages;
                        wellMeal += ds.waybill.wellMeal;
                        maintenance += ds.waybill.maintenance;
                        otherExpenses += ds.waybill.otherExpenses;
                    }
                }

                if(Prefs.getDayStatus(this) == 1){
                    totalReceipt = grand_total;
                }

                float balance;

                balance = (totalReceipt + otherReceipt) - (diesel + wages + wellMeal + maintenance + otherExpenses);



                fmt.setTextSize(28);
                fmt.setStyle(PrnTextStyle.NORMAL);
                mPrinter.setPrintAppendString("TOTAL RECEIPT         : Rs. " + totalReceipt, fmt);
                mPrinter.setPrintAppendString("OTHER RECEIPT        : Rs. " + otherReceipt, fmt);
                mPrinter.setPrintAppendString("DIESEL                           : Rs. " + diesel, fmt);
                mPrinter.setPrintAppendString("WAGES                          : Rs. " + wages, fmt);
                mPrinter.setPrintAppendString("WELL & MEAL             : Rs. " + wellMeal, fmt);
                mPrinter.setPrintAppendString("MAINTENANCE         : Rs. " + maintenance, fmt);
                mPrinter.setPrintAppendString("OTHER EXPENSES   : Rs. " + otherExpenses, fmt);

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("--------------------------------", fmt);

                fmt.setTextSize(28);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("BALANCE            : Rs. " + balance, fmt);

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("--------------------------------", fmt);

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                mPrinter.setPrintAppendString("=== END OF REPORT ===", fmt);
                mPrinter.setPrintAppendString("", fmt);
                mPrinter.setPrintAppendString("", fmt);
                mPrinter.setPrintAppendString("", fmt);

                Log.d("Report", "Starting print job...");
                mPrinter.setPrintStart();

                // ✅ Done: close popup on UI thread
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Printing complete", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                CrashLogger.logError("Printer", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Printing failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }





    private void showWarningDialog() {
        // 1️⃣ Inflate your custom layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.warning_dialog, null);

        // 2️⃣ Create the AlertDialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // 3️⃣ Get button references
        Button btnNo = dialogView.findViewById(R.id.button);
        Button btnYes = dialogView.findViewById(R.id.button2);

        // 4️⃣ Button listeners
        btnNo.setOnClickListener(v -> {
            // ❌ User does not want to continue
            dialog.dismiss();
        });

        btnYes.setOnClickListener(v -> {
            // ✅ User wants to proceed
            print_collection_report();
            dialog.dismiss();
        });

        // 5️⃣ Show dialog
        dialog.show();
    }


    private void back_action(){
        startActivity(new Intent(this, Menu.class));
        finish();
    }

    private void back_action2(){
        main_layout.setVisibility(View.VISIBLE);
        trip_report_layout.setVisibility(View.GONE);
        back_layout.setVisibility(View.GONE);
        print_layout.setVisibility(View.GONE);
    }

    @SuppressLint({"ResourceAsColor", "SetTextI18n"})
    private void show_all_trips() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
//            String today = Prefs.getDayDate(this);
//            String busNo = Prefs.getSelectedBus(this);

//            TicketDayEntity day = db.ticketDao().getDay(today, busNo);
//            if (day == null) return;
            TicketDayEntity day = db.ticketDao().getLastDay();
            if (day == null) return;

            List<TripEntity> allTrips = db.ticketDao().getTripsForDay(day.id);

            runOnUiThread(() -> {
                View dlgView = LayoutInflater.from(this)
                        .inflate(R.layout.dialog_tariffs, null);

                LinearLayout listContainer = dlgView.findViewById(R.id.container);
                listContainer.removeAllViews();

                // Step 1: Create the dialog FIRST so we can access it in the loop
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setView(dlgView)
                        .create();

                // Step 2: Build the list
                for (TripEntity trip : allTrips) {
                    View row = LayoutInflater.from(this)
                            .inflate(R.layout.item_trip, listContainer, false);

                    TextView tvTrip = row.findViewById(R.id.StageName);
                    tvTrip.setText("Trip No. " + trip.tripNo);

                    row.setOnClickListener(v -> {
                        custom_tripNo = trip.tripId;
                        row.setBackgroundColor(R.color.selected_tariff_row);
                        selected_trip.setText("Trip No " + trip.tripNo + " Selected");
                        dialog.dismiss(); // ✅ Dismiss when a trip is selected
                    });
                    listContainer.addView(row);
                }

                // Step 3: Configure dialog buttons
                Button btnPrint = dlgView.findViewById(R.id.btn_tariff_print);
                Button btnClose = dlgView.findViewById(R.id.btn_close);

                btnPrint.setVisibility(View.GONE); // Not used
                btnClose.setOnClickListener(v -> dialog.dismiss());

                dialog.show();
            });
        });
    }

    private void all_trips_report() {

        String currentDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            // Get Day record
            TicketDayEntity day = db.ticketDao().getLastDay();
            if (day == null) return;

            // Get all trips for the day
            List<TripEntity> trips = db.ticketDao().getTripsForDay(day.id);
            if (trips == null || trips.isEmpty()) return;

            // Now, fetch all tickets grouped by trip
            List<TripWithTickets> tripReports = new ArrayList<>();
            for (TripEntity trip : trips) {
                List<TicketEntity> tickets = db.ticketDao().getTicketsForTrip(trip.tripId);
                tripReports.add(new TripWithTickets(trip, tickets));
            }

            db.reportCountDao().recordAllTripsCount(Prefs.getAllTripsCount(this), day.id);

            runOnUiThread(() -> {
                int total_amount;

                PrnStrFormat fmt = new PrnStrFormat();

                // Global Header
                fmt.setTextSize(26);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                fmt.setFont(PrnTextFont.SANS_SERIF);
                mPrinter.setPrintAppendString("* "+ PrefsSecure.getTenantName(this) +" *", fmt);

                fmt.setTextSize(22);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                mPrinter.setPrintAppendString(day.busNo, fmt);

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                mPrinter.setPrintAppendString("==== DAY REPORT ====", fmt);

                fmt.setTextSize(24);
                fmt.setStyle(PrnTextStyle.NORMAL);
                fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
                mPrinter.setPrintAppendString("DT: " + currentDate + "   TM: " + currentTime, fmt);

                for (TripWithTickets report : tripReports) {
                    TripEntity trip = report.trip;
                    List<TicketEntity> tickets = report.tickets;

                    fmt.setTextSize(30);
                    fmt.setStyle(PrnTextStyle.BOLD);
                    mPrinter.setPrintAppendString("--------------------------------", fmt);

                    fmt.setTextSize(26);
                    fmt.setStyle(PrnTextStyle.BOLD);
                    mPrinter.setPrintAppendString("Trip No: " + trip.tripNo, fmt);

                    fmt.setTextSize(24);
                    fmt.setStyle(PrnTextStyle.NORMAL);
                    mPrinter.setPrintAppendString("Route: " + trip.route, fmt);
                    mPrinter.setPrintAppendString("Direction: " + trip.direction, fmt);

                    total_amount = 0;

                    if (tickets.isEmpty()) {
                        fmt.setTextSize(24);
                        fmt.setStyle(PrnTextStyle.ITALIC);
                        mPrinter.setPrintAppendString("No tickets found.", fmt);
                    } else {
                        for (TicketEntity t : tickets) {
                            total_amount = total_amount + t.amount;

                            fmt.setTextSize(22);
                            fmt.setStyle(PrnTextStyle.BOLD);
                            mPrinter.setPrintAppendString("TKT" + t.ticketNo + "    TM: " + t.time + "  " + t.fareType + " - Rs" + t.amount, fmt);

                            fmt.setTextSize(22);
                            fmt.setStyle(PrnTextStyle.NORMAL);
                            mPrinter.setPrintAppendString(t.startStage + "  " + t.ticketType + "  " + t.endStage, fmt);
//                            mPrinter.setPrintAppendString(t.fareType + " - Rs" + t.amount, fmt);

                            fmt.setTextSize(4);
                            mPrinter.setPrintAppendString("", fmt); // Spacer

                        }
                    }
                    fmt.setTextSize(28);
                    fmt.setStyle(PrnTextStyle.BOLD);
                    mPrinter.setPrintAppendString("BALANCE  :  Rs. " + total_amount, fmt);
                }

                // Final Footer
                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                mPrinter.setPrintAppendString("=== END OF REPORT ===", fmt);
                mPrinter.setPrintAppendString("", fmt);
                mPrinter.setPrintAppendString("", fmt);
                mPrinter.setPrintAppendString("", fmt);
                mPrinter.setPrintStart();
            });
        });
    }


    private void current_trip_report() {
        String busNo = Prefs.getSelectedBus(this);
        int current_tripNo = Prefs.getTripCount(this);
        String trip_direction = Prefs.getSelectedDirectionName(this);
        String routeName = Prefs.getSelectedRouteName(this);

        String currentDate = new SimpleDateFormat(
                "dd/MM/yyyy", Locale.getDefault()
        ).format(new Date());

        String currentTime = new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()
        ).format(new Date());

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            int tripNo = Prefs.getTripCount(this);

            TicketDayEntity day = db.ticketDao().getLastDay();

            int tripId = db.ticketDao().getTripId(day.id, tripNo);

            // Step 3: Get tickets for this trip
            List<TicketEntity> tickets = db.ticketDao().getTicketsForTrip(tripId);

            db.reportCountDao().recordCurrentTripCount(Prefs.getCurrentTripCount(this), day.id);

            // Step 4: Switch to main thread to update printer
            runOnUiThread(() -> {

                PrnStrFormat fmt = new PrnStrFormat();

                // Header
                fmt.setTextSize(26);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                fmt.setFont(PrnTextFont.SANS_SERIF);
                mPrinter.setPrintAppendString("* "+ PrefsSecure.getTenantName(this) +" *", fmt);

                fmt.setTextSize(22);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                mPrinter.setPrintAppendString(busNo, fmt);

                mPrinter.setPrintAppendString("", fmt);
                fmt.setTextSize(24);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("== Current Trip Report ==", fmt);



                mPrinter.setPrintAppendString("", fmt);

                fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
                mPrinter.setPrintAppendString("DT: " + currentDate, fmt);
                mPrinter.setPrintAppendString("TM: " + currentTime, fmt);


                mPrinter.setPrintAppendString("TRIP NO: " + current_tripNo, fmt);
                mPrinter.setPrintAppendString("ROUTE NO: " + routeName, fmt);

                fmt.setTextSize(22);
                mPrinter.setPrintAppendString("STAGES: " + trip_direction, fmt);


                if (tickets.isEmpty()) {
                    //Seperator
                    fmt.setTextSize(30);
                    fmt.setStyle(PrnTextStyle.BOLD);
                    mPrinter.setPrintAppendString("- - - - - - - - - - - - - - - - - - - - - - - -", fmt);

                    //Message
                    fmt.setTextSize(24);
                    fmt.setStyle(PrnTextStyle.BOLD);
                    mPrinter.setPrintAppendString("No tickets found for this trip.", fmt);
                } else {
                    for (TicketEntity t : tickets) {

                        fmt.setTextSize(30);
                        fmt.setStyle(PrnTextStyle.BOLD);
                        mPrinter.setPrintAppendString("- - - - - - - - - - - - - - - - - - - - - - - -", fmt);

                        fmt.setTextSize(22);
                        fmt.setStyle(PrnTextStyle.BOLD);
                        mPrinter.setPrintAppendString("TKT" + t.ticketNo + "     TM:" + t.time + "  " + t.fareType + " - Rs" + t.amount, fmt);

                        fmt.setTextSize(22);
                        fmt.setStyle(PrnTextStyle.NORMAL);
                        mPrinter.setPrintAppendString(t.startStage + " " + t.ticketType + " " + t.endStage, fmt);

//                        fmt.setTextSize(22);
//                        fmt.setStyle(PrnTextStyle.NORMAL);
//                        mPrinter.setPrintAppendString(t.fareType + " - Rs" + t.amount, fmt);

                    }
                }

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("- - - - - - - - - - - - - - - - - - - - - - - -", fmt);

                // Final Footer
                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                mPrinter.setPrintAppendString("=== END OF REPORT ===", fmt);
                mPrinter.setPrintAppendString("", fmt);
                mPrinter.setPrintAppendString("", fmt);
                mPrinter.setPrintAppendString("", fmt);

                // Final print start
                mPrinter.setPrintStart();
            });
        });
    }

    private void custom_trip_report(int tripNo) {

        String currentDate = new SimpleDateFormat(
                "dd/MM/yyyy", Locale.getDefault()
        ).format(new Date());

        String currentTime = new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()
        ).format(new Date());

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            List<TicketEntity> tickets = db.ticketDao().getTicketsForTrip(tripNo);
            String busNo = db.ticketDao().getBusNoFromTrip(tripNo);
            String trip_direction = db.ticketDao().getDirectionFromTrip(tripNo);
            String routeName = db.ticketDao().getRouteFromTrip(tripNo);

//            Log.d("TEST11", "TripNo: " + tripNo);

            TicketDayEntity day = db.ticketDao().getLastDay();

            db.reportCountDao().recordCustomTripCount(Prefs.getCustomTripCount(this), day.id);

                    // Step 4: Switch to main thread to update printer
            runOnUiThread(() -> {

                PrnStrFormat fmt = new PrnStrFormat();

                // Header
                fmt.setTextSize(26);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                fmt.setFont(PrnTextFont.SANS_SERIF);
                mPrinter.setPrintAppendString("* "+ PrefsSecure.getTenantName(this) +" *", fmt);

                fmt.setTextSize(22);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                mPrinter.setPrintAppendString(busNo, fmt);

                mPrinter.setPrintAppendString("", fmt);
                fmt.setTextSize(30);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("== Custom Trip Report ==", fmt);


                fmt.setTextSize(24);
                mPrinter.setPrintAppendString("", fmt);

                fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
                mPrinter.setPrintAppendString("DT: " + currentDate, fmt);
                mPrinter.setPrintAppendString("TM: " + currentTime, fmt);


                mPrinter.setPrintAppendString("TRIP NO: " + tripNo, fmt);
                mPrinter.setPrintAppendString("ROUTE NO: " + routeName, fmt);

                fmt.setTextSize(22);
                mPrinter.setPrintAppendString("STAGES: " + trip_direction, fmt);

                int total_amount = 0;

                if (tickets.isEmpty()) {
                    //Seperator
                    fmt.setTextSize(30);
                    fmt.setStyle(PrnTextStyle.BOLD);
                    mPrinter.setPrintAppendString("- - - - - - - - - - - - - - - - - - - - - - - -", fmt);

                    //Message
                    fmt.setTextSize(24);
                    fmt.setStyle(PrnTextStyle.BOLD);
                    mPrinter.setPrintAppendString("No tickets found for this trip.", fmt);
                } else {
                    for (TicketEntity t : tickets) {
                        total_amount = total_amount + t.amount;
                        fmt.setTextSize(30);
                        fmt.setStyle(PrnTextStyle.BOLD);
                        mPrinter.setPrintAppendString("- - - - - - - - - - - - - - - - - - - - - - - -", fmt);

                        fmt.setTextSize(22);
                        fmt.setStyle(PrnTextStyle.BOLD);
                        mPrinter.setPrintAppendString("TKT" + t.ticketNo + "     TM:" + t.time + "  " + t.fareType + " - Rs" + t.amount, fmt);

                        fmt.setTextSize(22);
                        fmt.setStyle(PrnTextStyle.NORMAL);
                        mPrinter.setPrintAppendString(t.startStage + " " + t.ticketType + " " + t.endStage, fmt);

                    }
                }

                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("- - - - - - - - - - - - - - - - - - - - - - - -", fmt);

                fmt.setTextSize(28);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("TOTAL  :  Rs. " + total_amount, fmt);


                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                mPrinter.setPrintAppendString("- - - - - - - - - - - - - - - - - - - - - - - -", fmt);

                // Final Footer
                fmt.setTextSize(30);
                fmt.setStyle(PrnTextStyle.BOLD);
                fmt.setAli(Layout.Alignment.ALIGN_CENTER);
                mPrinter.setPrintAppendString("=== END OF REPORT ===", fmt);
                mPrinter.setPrintAppendString("", fmt);
                mPrinter.setPrintAppendString("", fmt);
                mPrinter.setPrintAppendString("", fmt);

                // Final print start
                mPrinter.setPrintStart();
            });
        });
    }

    private static class TripWithTickets {
        TripEntity trip;
        List<TicketEntity> tickets;

        TripWithTickets(TripEntity trip, List<TicketEntity> tickets) {
            this.trip = trip;
            this.tickets = tickets;
        }
    }


}

