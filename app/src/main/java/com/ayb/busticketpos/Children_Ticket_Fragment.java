package com.ayb.busticketpos;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.zcs.sdk.DriverManager;
import com.zcs.sdk.Printer;
import com.zcs.sdk.SdkResult;
import com.zcs.sdk.print.PrnStrFormat;
import com.zcs.sdk.print.PrnTextFont;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.Executors;

public class Children_Ticket_Fragment extends Fragment {
    // ─── Shared ViewModel ───────────────────────────
    private StageViewModel stageVm;

    // ─── Stage data (populated via ViewModel) ──────
    private final List<Integer> stageNos   = new ArrayList<>();
    private final List<String>  stageNames = new ArrayList<>();
    private int currentStageIndex = 0;

    // ─── Child tariffs (from DB) ────────────────────
    private final List<String> childTariffs = new ArrayList<>();

    // ─── UI references ──────────────────────────────
    private TextView     viewStage;
    private Button       btnLeft, btnRight;
    private LinearLayout fareRow1, fareRow2, fareRow3;
//    private ImageButton btn_viewAmt;
    private TextView total_amount;

    // fare‐button state
    private final List<Button> allFareButtons = new ArrayList<>();
    private Button             selectedButton  = null;
    private String             fareValue       = "";

    // colors
    private int defaultButtonColor;
    private int selectedButtonColor;

    // printer (if needed)
    private Context       mContext;
    private Printer       mPrinter;

    private int          selectedTariffIndex  = -1;
    private String       selectedTariffStage;
    private int          selectedTariffAmount;

    private List<TariffRange> allTariffRanges = new ArrayList<>();

    private String selectedStation = "";

    public Children_Ticket_Fragment() {
        // Required empty constructor
    }

    /** Factory: inject the shared StageViewModel */
    public static Children_Ticket_Fragment newInstance(StageViewModel vm) {
        Children_Ticket_Fragment f = new Children_Ticket_Fragment();
        f.stageVm = vm;
        return f;
    }

    @SuppressLint({"ClickableViewAccessibility", "SetTextI18n"})
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup    container,
            @Nullable Bundle       savedInstanceState
    ) {
        View root = inflater.inflate(
                R.layout.fragment_children_ticket, container, false
        );

        stageVm = new ViewModelProvider(requireActivity()).get(StageViewModel.class);


        // 1️⃣ find views
        viewStage = root.findViewById(R.id.view_stage);
        btnLeft   = root.findViewById(R.id.button_left);
        btnRight  = root.findViewById(R.id.button_right);
        fareRow1  = root.findViewById(R.id.fare_row1);
        fareRow2  = root.findViewById(R.id.fare_row2);
        fareRow3  = root.findViewById(R.id.fare_row3);
        // at top of class, alongside your other fields:
        Button btn_print = root.findViewById(R.id.btn_print);
        Button btn_back = root.findViewById(R.id.btn_menu);
        ImageButton btn_viewAmt = root.findViewById(R.id.btn_view_amount);
        total_amount = root.findViewById(R.id.total_amount);
        Button btn_calculator = root.findViewById(R.id.btn_calculator);

        // 2️⃣ optional printer setup
        mContext       = getActivity();
        DriverManager mDriverManager = DriverManager.getInstance();
        mPrinter       = mDriverManager.getPrinter();

        Button btnShowTariffs = root.findViewById(R.id.btn_see_tariffs);
        btnShowTariffs.setOnClickListener(v -> showTariffsDialog());

        // 3️⃣ colors
        defaultButtonColor  = Color.parseColor("#1bff00");
        selectedButtonColor = Color.parseColor("#FF0101");

        Executors.newSingleThreadExecutor().execute(() -> {
            List<TariffRange> list = AppDatabase
                    .getInstance(requireContext())
                    .tariffDao()
                    .getAllSync(); // make sure this returns List<TariffRange>

            requireActivity().runOnUiThread(() -> {
                allTariffRanges.clear();
                allTariffRanges.addAll(list);
            });
        });

        // 4️⃣ Observe stages list
        stageVm.getStages().observe(
                getViewLifecycleOwner(),
                stages -> {
                    stageNos.clear();
                    stageNames.clear();
                    for (StageEntity s : stages) {
                        stageNos.add(s.stageNo);
                        stageNames.add(s.stageName);
                    }
                    computeAndShowChildTariffs();
                    updateStageDisplay();
                }
        );

        // 5️⃣ Observe current index
        stageVm.getCurrentIndex().observe(
                getViewLifecycleOwner(),
                idx -> {
                    if (idx != null) {
                        currentStageIndex = idx;
                        Prefs.saveCurrentStageID(requireContext(), idx);
                        updateStageDisplay();
                    }
                }
        );

        // 6️⃣ hook nav buttons to ViewModel
        btnLeft.setOnClickListener(v -> stageVm.prev());
        btnRight.setOnClickListener(v -> stageVm.next());
        btn_print.setOnClickListener(v -> printTicket());

        btn_back.setOnClickListener(v -> {

            startActivity(new Intent(mContext, Menu.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            getActivity().finish();
        });

        btn_calculator.setOnClickListener(v -> startActivity(new Intent(mContext, Calculator.class)));

        btn_viewAmt.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 👇 Show the total in the text view
                    showTotalAmountForToday(total_amount);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 👇 Mask the amount when released
                    total_amount.setText("Rs XXXX");
                    return true;
            }
            return false;
        });

        return root;
    }

    @SuppressLint("ResourceAsColor")
    private void showTariffsDialog() {
        Executors.newSingleThreadExecutor().execute(() -> {
            // 1️⃣ load all tariff‐ranges
            List<TariffRange> allRanges = AppDatabase
                    .getInstance(requireContext())
                    .tariffDao()
                    .getAllSync();

            // 2️⃣ build (stageName → fare) for each stage after currentStageIndex
            List<Pair<String,Integer>> rows = new ArrayList<>();
            int startNo = stageNos.get(currentStageIndex);
            for (int i = currentStageIndex + 1; i < stageNos.size(); i++) {
                int destNo = stageNos.get(i);
                int distance = Math.abs(destNo - startNo);
                // find the matching TariffRange
                int fare = 0;
                for (TariffRange r : allRanges) {
                    if (distance >= r.minStages && distance <= r.maxStages) {
                        fare = r.child;
                        break;
                    }
                }
                rows.add(new Pair<>( stageNames.get(i), fare ));
            }

            // 3️⃣ back on UI → inflate dialog
            requireActivity().runOnUiThread(() -> {
                View dlgView = LayoutInflater.from(getContext())
                        .inflate(R.layout.dialog_tariffs, null);

                LinearLayout listContainer = dlgView.findViewById(R.id.container);
                // reset selection
                selectedTariffIndex  = -1;
                selectedTariffStage  = null;
                selectedTariffAmount = 0;
                listContainer.removeAllViews();

                // 4️⃣ inflate one line per remaining stage
                for (int idx = 0; idx < rows.size(); idx++) {
                    Pair<String,Integer> p = rows.get(idx);
                    View row = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_tariff, listContainer, false);

                    TextView tvStage = row.findViewById(R.id.StageName);
                    TextView tvFare  = row.findViewById(R.id.Fare);
                    tvStage.setText(p.first);
                    tvFare .setText(String.valueOf(p.second));

                    // click = highlight + remember
                    int finalIdx = idx;
                    row.setOnClickListener(v -> {
                        // clear old
                        if (selectedTariffIndex >= 0
                                && selectedTariffIndex < listContainer.getChildCount()) {
                            listContainer.getChildAt(selectedTariffIndex)
                                    .setBackgroundColor(Color.TRANSPARENT);
                        }
                        // highlight new
                        row.setBackgroundColor(R.color.selected_tariff_row);
                        selectedTariffIndex  = finalIdx;
                        selectedTariffStage  = p.first;
                        selectedTariffAmount = p.second;
                    });

                    listContainer.addView(row);
                }

                // 5️⃣ build & show AlertDialog
                AlertDialog dialog = new AlertDialog.Builder(getContext())
                        .setView(dlgView)
                        .create();

                // wire the dialog buttons:
                Button btnPrint = dlgView.findViewById(R.id.btn_tariff_print);
                Button btnClose = dlgView.findViewById(R.id.btn_close);

                btnClose.setOnClickListener(v -> dialog.dismiss());

                btnPrint.setOnClickListener(v -> {
                    if (selectedTariffIndex < 0) {
                        Toast.makeText(getContext(),
                                "Please select a destination first",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        printTicketTariff(selectedTariffStage, Integer.toString(selectedTariffAmount));
//                        Toast.makeText(getContext(),
//                                "Destination: " + selectedTariffStage +
//                                        "\nFare: Rs " + selectedTariffAmount,
//                                Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    }
                });

                dialog.show();
            });
        });
    }

    /** Refresh displayed stage name & arrow enable/disable */
    @SuppressLint("SetTextI18n")
    private void updateStageDisplay() {
        if (stageNames.isEmpty()) {
            viewStage.setText("No stages");
            btnLeft .setEnabled(false);
            btnRight.setEnabled(false);
            selectedStation = "";
        } else {
            selectedStation = stageNames.get(currentStageIndex);
            viewStage.setText(selectedStation);
            btnLeft .setEnabled(currentStageIndex > 0);
            btnRight.setEnabled(currentStageIndex < stageNames.size() - 1);
        }
    }

    /** Compute valid child fares by stage-difference, then build buttons */
    private void computeAndShowChildTariffs() {
        if (stageNos.isEmpty()) return;

        int diff = Math.abs(
                stageNos.get(stageNos.size() - 1)
                        - stageNos.get(0)
        );


        Executors.newSingleThreadExecutor().execute(() -> {
            List<TariffRange> all = AppDatabase
                    .getInstance(requireContext())
                    .tariffDao()
                    .getAllSync();

            TreeSet<Integer> set = new TreeSet<>();
            for (int d = 1; d <= diff; d++) {
                for (TariffRange r : all) {
                    if (d >= r.minStages && d <= r.maxStages) {
                        set.add(r.child);
                        break;
                    }
                }
            }

            childTariffs.clear();
            for (Integer t : set) {
                childTariffs.add(String.valueOf(t));
            }

            requireActivity().runOnUiThread(() ->
                    populateFareButtons(childTariffs)
            );
        });
    }

    /** Dynamically populate up to three rows of fare buttons */
    private void populateFareButtons(List<String> fares) {
        fareRow1.removeAllViews();
        fareRow2.removeAllViews();
        fareRow3.removeAllViews();
        allFareButtons.clear();
        selectedButton = null;
        fareValue      = "";

        int maxPerRow = 3;
        int widthDp   = 140;
        int widthPx   = (int)(widthDp * getResources()
                .getDisplayMetrics().density);
        float fontSp  = 40f;
        int marginDp  = 4;
        int marginPx  = (int)(marginDp * getResources()
                .getDisplayMetrics().density);

        for (int i = 0; i < fares.size(); i++) {
            LinearLayout row = (i < maxPerRow)
                    ? fareRow1
                    : (i < maxPerRow*2 ? fareRow2 : fareRow3);
            int posInRow = (i < maxPerRow)
                    ? i
                    : (i < maxPerRow*2 ? i - maxPerRow : i - maxPerRow*2);

            String txt = fares.get(i);
            Button b = new Button(requireContext());
            b.setText(txt);
            b.setBackgroundTintList(
                    ColorStateList.valueOf(defaultButtonColor)
            );
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    widthPx, LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (posInRow > 0) lp.leftMargin = marginPx;
            b.setLayoutParams(lp);

            b.setOnClickListener(v -> {
                if (selectedButton != null) {
                    selectedButton.setBackgroundTintList(
                            ColorStateList.valueOf(defaultButtonColor)
                    );
                }
                if (selectedButton == b) {
                    selectedButton = null;
                    fareValue = "";
                } else {
                    b.setBackgroundTintList(
                            ColorStateList.valueOf(selectedButtonColor)
                    );
                    selectedButton = b;
                    fareValue = txt;
                }
            });

            row.addView(b);
            allFareButtons.add(b);
        }
    }

    private @Nullable String calculateDestinationStage(
            int startIndex,
            int fareAmount
    ) {
        if (allTariffRanges.isEmpty() || stageNos.isEmpty() || stageNames.isEmpty())
            return null;

        // 1️⃣ Find best matching range for fareType
        TariffRange bestMatch = null;
        for (TariffRange r : allTariffRanges) {
            boolean match;
            match = r.child == fareAmount;
            if (match) {
                if (bestMatch == null || r.maxStages > bestMatch.maxStages) {
                    bestMatch = r;
                }
            }
        }

        if (bestMatch == null) return null;

        // ✅ Use index math — always forward in the list
        int destinationIndex = startIndex + bestMatch.maxStages;

        // Clamp to avoid index overflow
        if (destinationIndex >= stageNames.size()) {
            destinationIndex = stageNames.size() - 1;
        }

        return stageNames.get(destinationIndex);

    }

    public static String toFourDigitString(int value) {
        if (value < 0 || value > 9999) {
            throw new IllegalArgumentException("Value must be between 0 and 9999");
        }
        String s = Integer.toString(value);
        int len = s.length();
        if (len < 4) {
            // build a small char[] of zeros + digits
            char[] buf = new char[4];
            for (int i = 0; i < 4 - len; i++) {
                buf[i] = '0';
            }
            System.arraycopy(s.toCharArray(), 0, buf, 4 - len, len);
            return new String(buf);
        }
        return s; // already 4 chars
    }

    /** Send the print job to the ZCS printer */
    private void printTicket() {

        String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());

        if(!Prefs.getDayDate(mContext).equals(today)){
            String msg = "Trip Expired! End Trip and Start a New One";
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

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
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
                Intent i = new Intent(getActivity(), Menu.class);
                startActivity(i);
                getActivity().finish();
            });

        }

        int printStatus = mPrinter.getPrinterStatus();

        if (printStatus == SdkResult.SDK_PRN_STATUS_PAPEROUT) {
            Toast.makeText(mContext, "OUT OF PAPER — Logged as Blank Ticket", Toast.LENGTH_LONG).show();

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(mContext);

                    // ⚙️ Fetch trip and insert blank ticket *off* main thread
                    TripEntity trip = db.ticketDao().getLastTrip();
                    int tripId = (trip != null) ? trip.tripId : 0; // use 0 if no trip

                    String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                    BlankTicketEntity blankTicket = new BlankTicketEntity(now, tripId);

                    long id = db.blankTicketDao().insert(blankTicket);

                    Log.w("Printer", "Paper-out logged as blank ticket #" + id + " (tripId=" + tripId + ")");

                    // ✅ Push online immediately (optional)
                    SyncClient.recordBlankTicket(
                            mContext,
                            now,
                            new SyncClient.Callback() {
                                @Override
                                public void onSuccess(org.json.JSONObject resp) {
                                    Log.i("BLANK_SYNC", "Blank ticket uploaded: " + resp);
                                }

                                @Override
                                public void onFail(String reason) {
                                    Log.w("BLANK_SYNC", "Blank ticket upload failed: " + reason);
                                }
                            }
                    );

                    // ✅ Show toast safely on main thread
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(mContext, "Blank Ticket Logged", Toast.LENGTH_SHORT).show()
                    );

                } catch (Exception e) {
                    CrashLogger.logError("BlankTicketInsert", e);
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(mContext, "Failed to log blank ticket: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            });

            return; // prevent normal printing
        }

        if (fareValue.isEmpty()) {
            Toast.makeText(mContext,
                    "No Fare Amount Selected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedStation.isEmpty()) {
            Toast.makeText(mContext,
                    "No Station Selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String busNo;
        int ticketNo;
        String routeName;

        busNo = Prefs.getSelectedBus(mContext);
        ticketNo = Prefs.getTicketCount(mContext);
        ticketNo++;
        Prefs.saveTicketCount(mContext, ticketNo);

        routeName = Prefs.getSelectedRouteName(mContext);
        String currentStage = stageNames.get(currentStageIndex);
        String destination = calculateDestinationStage(currentStageIndex, Integer.valueOf(fareValue));

        String currentDate = new SimpleDateFormat(
                "dd/MM/yyyy", Locale.getDefault()
        ).format(new Date());

        String currentTime = new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()
        ).format(new Date());

        PrnStrFormat fmt = new PrnStrFormat();

        int ticketAmount;
        try {
            ticketAmount = Integer.parseInt(fareValue.trim());
        } catch (NumberFormatException e) {
            Log.e("TicketError", "Tariff is not a valid number: " + fareValue);
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(mContext, "Invalid fare format", Toast.LENGTH_SHORT).show()
            );
            return;
        }

        //Company Name
        fmt.setTextSize(26);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("* "+ PrefsSecure.getTenantName(mContext) +" *", fmt);

        //Bus No
        fmt.setTextSize(22);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString(busNo, fmt);

        //Ticket No
        fmt.setTextSize(28);
        fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("TKT No: " + toFourDigitString(ticketNo), fmt);

        //Spacer
        fmt.setTextSize(4);
        mPrinter.setPrintAppendString(" ", fmt);

        //Date Time
        fmt.setTextSize(26);
        fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("DT: " + currentDate + "               " + "TM: " + currentTime, fmt);

        //Spacer
        fmt.setTextSize(4);
        mPrinter.setPrintAppendString(" ", fmt);

        //Route
        fmt.setTextSize(24);
        fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("ROUTE No:  " + routeName, fmt);

        //Spacer
        fmt.setTextSize(8);
        mPrinter.setPrintAppendString("", fmt);

        //Stages
        fmt.setTextSize(26);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString(currentStage + "     UP TO     " + destination, fmt);

        //Spacer
        fmt.setTextSize(8);
        mPrinter.setPrintAppendString("", fmt);

        //Fare
        fmt.setTextSize(34);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("CHILD" + "         " + "Rs " + fareValue, fmt);

        //Feedback
        fmt.setTextSize(24);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString(" * Feedback on " + PrefsSecure.getFeedbackNumber(getContext())  + " * ", fmt);

        //Spacer
        mPrinter.setPrintAppendString("", fmt);
        mPrinter.setPrintAppendString("", fmt);

        mPrinter.setPrintStart();

        //Store in DB

        int finalTicketNo = ticketNo;
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            TripEntity trip = db.ticketDao().getLastTrip();  // get current trip

            if (trip == null) return; // No trip in progress

            TicketEntity ticket = new TicketEntity();
            ticket.ticketNo   = toFourDigitString(finalTicketNo);
            ticket.tripId     = trip.tripId;
            ticket.time       = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            ticket.startStage = currentStage;
            ticket.endStage   = destination;
            ticket.fareType   = "Child";
            ticket.amount     = ticketAmount;
            ticket.ticketType = "UP TO";

            db.ticketDao().insertTicket(ticket);

            final String apiDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); // server expects yyyy-MM-dd

            SyncClient.recordTicket(
                    mContext,
                    toFourDigitString(finalTicketNo),
                    apiDate,
                    currentTime,
                    currentStage,
                    ticket.ticketType,
                    destination,
                    ticket.fareType,
                    ticketAmount,
                    new SyncClient.Callback() {
                        @Override public void onSuccess(org.json.JSONObject resp) {
                            Log.d("TICKET_SYNC", "Ticket sent: " + resp);
                        }
                        @Override public void onFail(String reason) {
                            Log.e("TICKET_SYNC", "Ticket send failed: " + reason);

                        }
                    }
            );

        });

        requireActivity().runOnUiThread(() -> {
            if (selectedButton != null) {
                selectedButton.setBackgroundTintList(ColorStateList.valueOf(defaultButtonColor));
                selectedButton = null;
                fareValue = "";
            }
        });

    }

    private void printTicketTariff(String destination, String tariff) {

        String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());

        if(!Prefs.getDayDate(mContext).equals(today)){
            String msg = "Trip Expired! End Trip and Start a New One";
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

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
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
                Intent i = new Intent(getActivity(), Menu.class);
                startActivity(i);
                getActivity().finish();
            });

        }

        int printStatus = mPrinter.getPrinterStatus();

        if (printStatus == SdkResult.SDK_PRN_STATUS_PAPEROUT) {
            Toast.makeText(mContext, "OUT OF PAPER — Logged as Blank Ticket", Toast.LENGTH_LONG).show();

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(mContext);

                    // ⚙️ Fetch trip and insert blank ticket *off* main thread
                    TripEntity trip = db.ticketDao().getLastTrip();
                    int tripId = (trip != null) ? trip.tripId : 0; // use 0 if no trip

                    String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                    BlankTicketEntity blankTicket = new BlankTicketEntity(now, tripId);

                    long id = db.blankTicketDao().insert(blankTicket);

                    Log.w("Printer", "Paper-out logged as blank ticket #" + id + " (tripId=" + tripId + ")");

                    // ✅ Push online immediately (optional)
                    SyncClient.recordBlankTicket(
                            mContext,
                            now,
                            new SyncClient.Callback() {
                                @Override
                                public void onSuccess(org.json.JSONObject resp) {
                                    Log.i("BLANK_SYNC", "Blank ticket uploaded: " + resp);
                                }

                                @Override
                                public void onFail(String reason) {
                                    Log.w("BLANK_SYNC", "Blank ticket upload failed: " + reason);
                                }
                            }
                    );

                    // ✅ Show toast safely on main thread
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(mContext, "Blank Ticket Logged", Toast.LENGTH_SHORT).show()
                    );

                } catch (Exception e) {
                    CrashLogger.logError("BlankTicketInsert", e);
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(mContext, "Failed to log blank ticket: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            });

            return; // prevent normal printing
        }

        if (selectedStation.isEmpty()) {
            Toast.makeText(mContext,
                    "No Station Selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String busNo;
        int ticketNo;
        String routeName;

        busNo = Prefs.getSelectedBus(mContext);
        ticketNo = Prefs.getTicketCount(mContext);
        ticketNo++;
        Prefs.saveTicketCount(mContext, ticketNo);

        routeName = Prefs.getSelectedRouteName(mContext);
        String currentStage = stageNames.get(currentStageIndex);

        String currentDate = new SimpleDateFormat(
                "dd/MM/yyyy", Locale.getDefault()
        ).format(new Date());

        String currentTime = new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()
        ).format(new Date());

        PrnStrFormat fmt = new PrnStrFormat();

        //Company Name
        fmt.setTextSize(26);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("* "+ PrefsSecure.getTenantName(mContext) +" *", fmt);

        //Bus No
        fmt.setTextSize(22);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString(busNo, fmt);

        //Ticket No
        fmt.setTextSize(28);
        fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("TKT No: " + toFourDigitString(ticketNo), fmt);

        //Spacer
        fmt.setTextSize(4);
        mPrinter.setPrintAppendString(" ", fmt);

        //Date Time
        fmt.setTextSize(26);
        fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("DT: " + currentDate + "               " + "TM: " + currentTime, fmt);

        //Spacer
        fmt.setTextSize(4);
        mPrinter.setPrintAppendString(" ", fmt);

        //Route
        fmt.setTextSize(24);
        fmt.setAli(Layout.Alignment.ALIGN_NORMAL);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("ROUTE No:  " + routeName, fmt);

        //Spacer
        fmt.setTextSize(8);
        mPrinter.setPrintAppendString("", fmt);

        //Stages
        fmt.setTextSize(26);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString(currentStage + "     TO     " + destination, fmt);

        //Spacer
        fmt.setTextSize(8);
        mPrinter.setPrintAppendString("", fmt);

        //Fare
        fmt.setTextSize(34);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("CHILD" + "         " + "Rs " + tariff, fmt);

        //Feedback
        fmt.setTextSize(24);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString(" * Feedback on " + PrefsSecure.getFeedbackNumber(getContext())  + " * ", fmt);

        //Spacer
        mPrinter.setPrintAppendString("", fmt);
        mPrinter.setPrintAppendString("", fmt);

        mPrinter.setPrintStart();

        //Store in DB

        int finalTicketNo = ticketNo;
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            TripEntity trip = db.ticketDao().getLastTrip();

            if (trip == null) return;

            TicketEntity ticket = new TicketEntity();
            ticket.ticketNo   = toFourDigitString(finalTicketNo);
            ticket.tripId     = trip.tripId;
            ticket.time       = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            ticket.startStage = currentStage;
            ticket.endStage   = destination;
            ticket.fareType   = "Child";
            ticket.amount     = Integer.parseInt(tariff);
            ticket.ticketType = "TO";

            db.ticketDao().insertTicket(ticket);

            final String apiDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); // server expects yyyy-MM-dd

            SyncClient.recordTicket(
                    mContext,
                    toFourDigitString(finalTicketNo),
                    apiDate,
                    currentTime,
                    currentStage,
                    ticket.ticketType,
                    destination,
                    ticket.fareType,
                    Integer.parseInt(tariff),
                    new SyncClient.Callback() {
                        @Override public void onSuccess(org.json.JSONObject resp) {
                            Log.d("TICKET_SYNC", "Ticket sent: " + resp);
                        }
                        @Override public void onFail(String reason) {
                            Log.e("TICKET_SYNC", "Ticket send failed: " + reason);

                        }
                    }
            );


        });
    }

    //Shows the total amount for the current Shift
    @SuppressLint("SetTextI18n")
    private void showTotalAmountForToday(@Nullable TextView targetView) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            String busNo = Prefs.getSelectedBus(requireContext());

//            final Integer[] total = {db.ticketDao().getTotalAmountForDate(today, busNo)};
            final Integer[] total = {db.ticketDao().getTotalAmountForLastShift(busNo)};

            requireActivity().runOnUiThread(() -> {
                if (total[0] == null) total[0] = 0;

                if (targetView != null) {
                    targetView.setText("Rs " + total[0]);
                }
            });
        });
    }

}
