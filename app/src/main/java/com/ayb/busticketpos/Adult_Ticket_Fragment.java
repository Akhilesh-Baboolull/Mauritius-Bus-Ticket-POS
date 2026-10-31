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
import androidx.appcompat.app.AppCompatDialog;
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

public class Adult_Ticket_Fragment extends Fragment {
    // ─── Stage data ─────────────────────────────────
    private final List<Integer> stageNos   = new ArrayList<>();
    private final List<String>  stageNames = new ArrayList<>();
    private final List<Integer> stageOrder = new ArrayList<>();
    private final List<Integer> loopIndices = new ArrayList<>();
    private int currentStageIndex = 0;
    private String selectedStation = "";

    // ─── Tariffs ────────────────────────────────────
    private final List<String> adultTariffs = new ArrayList<>();

    // ─── UI state ───────────────────────────────────
    private TextView     viewStage;
    private Button       btnLeft;
    private Button btnRight;
    private TextView total_amount;
    private LinearLayout fareRow1, fareRow2, fareRow3;
//    private final List<Button> allFareButtons = new ArrayList<>();
    private Button             selectedButton  = null;
    private String             fareValue       = "";


    // ─── Colors ────────────────────────────────────
    private int defaultButtonColor;
    private int selectedButtonColor;

    private Printer       mPrinter;
    /** Application context for background work (safe after the fragment detaches). */
    private Context       appCtx;
    private Context       mContext;

    private int          selectedTariffIndex  = -1;
    private String       selectedTariffStage;
    private int          selectedTariffAmount;

    private List<TariffRange> allTariffRanges = new ArrayList<>();


    // ─── Shared ViewModel ───────────────────────────
    private StageViewModel stageVm;

    public Adult_Ticket_Fragment() {
        // Required empty constructor
    }

    /** Factory injection of the shared ViewModel */
    public static Adult_Ticket_Fragment newInstance(StageViewModel vm) {
        Adult_Ticket_Fragment f = new Adult_Ticket_Fragment();
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
                R.layout.fragment_adult_ticket, container, false
        );

        stageVm = new ViewModelProvider(requireActivity()).get(StageViewModel.class);


        // 1️⃣ Find views
        viewStage = root.findViewById(R.id.view_stage);
        btnLeft   = root.findViewById(R.id.button_left);
        btnRight  = root.findViewById(R.id.button_right);
        Button btn_print = root.findViewById(R.id.btn_print);
        fareRow1  = root.findViewById(R.id.fare_row1);
        fareRow2  = root.findViewById(R.id.fare_row2);
        fareRow3  = root.findViewById(R.id.fare_row3);
        //--------Pop up-------------
        // at top of class, alongside your other fields:
        Button btnShowTariffs = root.findViewById(R.id.btn_see_tariffs);
        Button btn_back = root.findViewById(R.id.btn_menu);
        ImageButton btn_viewAmt = root.findViewById(R.id.btn_view_amount);
        total_amount = root.findViewById(R.id.total_amount);
        Button btn_calculator = root.findViewById(R.id.btn_calculator);

        // 2️⃣ Colors
        defaultButtonColor  = Color.parseColor("#1bff00");
        selectedButtonColor = Color.parseColor("#FF0101");

        // 3️⃣ Printer setup
        mContext       = getActivity();
        // ─── Printer ────────────────────────────────────
        appCtx = requireContext().getApplicationContext();
        try {
            mPrinter = DriverManager.getInstance().getPrinter();
        } catch (Throwable t) {
            mPrinter = null;
            AppLog.e("Printer", "Printer SDK unavailable", t);
        }
//        boolean isSupportCutter = mPrinter.isSuppoerCutter();

        AppExecutors.runBg(() -> {
            List<TariffRange> list = AppDatabase
                    .getInstance(appCtx)
                    .tariffDao()
                    .getAllSync(); // make sure this returns List<TariffRange>

            runOnUiSafe(() -> {
                allTariffRanges.clear();
                allTariffRanges.addAll(list);
            });
        });


        // 4️⃣ Observe shared ViewModel → stages list
        stageVm.getStages().observe(
                getViewLifecycleOwner(),
                stages -> {
                    // rebuild our simple lists
                    stageNos.clear();
                    stageNames.clear();
                    stageOrder.clear();
                    loopIndices.clear();
                    for (StageEntity s : stages) {
                        stageNos.add(s.stageNo);
                        stageNames.add(s.stageName);
                        stageOrder.add(s.stageOrder);
                        loopIndices.add(s.loopIndex);
                    }
                    // recalc tariffs & refresh UI
                    computeAndShowAdultTariffs();
                    updateStageDisplay();
                }
        );

        // 5️⃣ Observe shared ViewModel → current index
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

        // 6️⃣ Wire nav buttons to the ViewModel
        btnLeft.setOnClickListener(v -> stageVm.prev());
        btnRight.setOnClickListener(v -> stageVm.next());

        // 7️⃣ Load tariffs and render buttons
        //    (will be triggered once stages arrive via the first observer)
        // computeAndShowAdultTariffs();

        // 8️⃣ Print button
        btn_print.setOnClickListener(v -> printTicket());

        btnShowTariffs.setOnClickListener(v -> showTariffsDialog());

        btn_back.setOnClickListener(v -> {

            startActivity(new Intent(mContext, Menu.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            getActivity().finish();
        });

        btn_calculator.setOnClickListener(v -> startActivity(new Intent(mContext, Calculator.class)));

        btn_viewAmt.setOnTouchListener((v, event) -> switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                // 👇 Show the total in the text view
                showTotalAmountForToday(total_amount);
                yield true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 👇 Mask the amount when released
                total_amount.setText("Rs XXXX");
                yield true;
            }
            default -> false;
        });



        return root;
    }

    private int calculateDistance(
            int startStageNo, int startLoopIndex, int startOrder,
            int endStageNo, int endLoopIndex, int endOrder
    ) {

        // Both inside the loop
        if (startLoopIndex != 0 && endLoopIndex != 0) {
            return Math.abs(endLoopIndex - startLoopIndex);
        }

        // Start in loop, end outside
        if (startLoopIndex != 0) {
            if (endOrder >= startOrder) {
                return Math.abs(endStageNo - startStageNo) + startLoopIndex;
            } else {
                return Math.abs(endStageNo - startStageNo) - 1 + startLoopIndex;
            }
        }

        // Start outside, end in loop
        if (endLoopIndex != 0) {
            if (startOrder >= endOrder) {
                return Math.abs(endStageNo - startStageNo) + endLoopIndex;
            } else {
                return Math.abs(endStageNo - startStageNo) - 1 + endLoopIndex;
            }
        }

        // Both outside the loop
        return Math.abs(endStageNo - startStageNo);
    }

    @SuppressLint("ResourceAsColor")
    private void showTariffsDialog() {
        AppExecutors.runBg(() -> {
            // 1️⃣ load all tariff‐ranges
            List<TariffRange> allRanges = AppDatabase
                    .getInstance(appCtx)
                    .tariffDao()
                    .getAllSync();

            // 2️⃣ build (stageName → fare) for each stage after currentStageIndex
            List<Pair<String,Integer>> rows = new ArrayList<>();
            if (!clampStageIndex()) return;
            int startNo = stageNos.get(currentStageIndex);
            int startLoopIndex = loopIndices.get(currentStageIndex);
            int startOrder = stageOrder.get(currentStageIndex);

            for (int i = currentStageIndex + 1; i < stageNos.size(); i++) {
                int destNo = stageNos.get(i);
                int endLoopIndex = loopIndices.get(i);
                int endOrder = stageOrder.get(i);

                int distance = calculateDistance(
                        startNo, startLoopIndex, startOrder,
                        destNo, endLoopIndex, endOrder
                );
//                int distance = Math.abs(destNo - startNo);


                // find the matching TariffRange
                int fare = 0;
                for (TariffRange r : allRanges) {
                    if (distance >= r.minStages && distance <= r.maxStages) {
                        fare = r.adult;
                        break;
                    }
                }
                rows.add(new Pair<>( stageNames.get(i), fare ));
            }

            // 3️⃣ back on UI → inflate dialog
            runOnUiSafe(() -> {
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

                // 5️⃣ build & show dialog
                AppCompatDialog dialog = AppDialog.wrap(requireContext(), dlgView, 0.95f);

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
                        dialog.dismiss();
                    }
                });

                AppDialog.showSafely(getActivity(), dialog);
            });
        });
    }

    /** Refresh the stage‐display TextView & arrow enabled states */
    @SuppressLint("SetTextI18n")
    private void updateStageDisplay() {
        if (stageNames.isEmpty()) {
            viewStage.setText("No stages");
            btnLeft .setEnabled(false);
            btnRight.setEnabled(false);
            selectedStation = "";
        } else {
            clampStageIndex();
            selectedStation = stageNames.get(currentStageIndex);
            viewStage.setText(selectedStation);
            btnLeft .setEnabled(currentStageIndex > 0);
            btnRight.setEnabled(currentStageIndex < stageNames.size() - 1);
        }
    }

    /** Compute valid adult fares based on stage span, then inflate buttons */
    private void computeAndShowAdultTariffs() {
        if (stageNos.isEmpty()) return;

        int diff = Math.abs(
                stageNos.get(stageNos.size() - 1)
                        - stageNos.get(0)
        );


        AppExecutors.runBg(() -> {
            List<TariffRange> all = AppDatabase
                    .getInstance(appCtx)
                    .tariffDao()
                    .getAllSync();

            TreeSet<Integer> set = new TreeSet<>();
            for (int d = 1; d <= diff; d++) {
                for (TariffRange r : all) {
                    if (d >= r.minStages && d <= r.maxStages) {
                        set.add(r.adult);
                        break;
                    }
                }
            }

            adultTariffs.clear();
            for (Integer t : set) {
                adultTariffs.add(String.valueOf(t));
            }

            runOnUiSafe(() ->
                    populateFareButtons(adultTariffs)
            );
        });
    }

    /** Build up to three rows of fare‐buttons dynamically */
    private void populateFareButtons(List<String> fares) {
        fareRow1.removeAllViews();
        fareRow2.removeAllViews();
        fareRow3.removeAllViews();
//        allFareButtons.clear();
        selectedButton = null;
        fareValue      = "";

        int maxPerRow = 3;
        int wDp       = 140;
        int wPx       = (int)(wDp * getResources().getDisplayMetrics().density);
        float fSp     = 40f;
        int mDp       = 4;
        int mPx       = (int)(mDp * getResources().getDisplayMetrics().density);

        for (int i = 0; i < fares.size(); i++) {
            LinearLayout row = (i < maxPerRow)
                    ? fareRow1
                    : (i < maxPerRow*2 ? fareRow2 : fareRow3);
            int posInRow = (i < maxPerRow)
                    ? i
                    : (i < maxPerRow*2 ? i - maxPerRow : i - maxPerRow*2);

            Button b = new Button(requireContext());
            String txt = fares.get(i);
            b.setText(txt);
            b.setBackgroundTintList(
                    ColorStateList.valueOf(defaultButtonColor)
            );
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, fSp);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    wPx, LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (posInRow > 0) lp.leftMargin = mPx;
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
//            allFareButtons.add(b);
        }
    }

    /**
     * Given a starting stage index, a selected fare, and a fare-type,
     * returns the furthest stage name reachable on the current route.
     *
     * @param startIndex zero-based index into the route’s stage list
     * @param fareAmount the user-selected fare (e.g. 30)
     * @return the stageName at destination, or null if something goes wrong
     */
    private @Nullable String calculateDestinationStage(
            int startIndex,
            int fareAmount
    ) {
        if (allTariffRanges.isEmpty() || stageNos.isEmpty() || stageNames.isEmpty())
            return null;

        // 1️⃣ Find best matching range for fareType
        TariffRange bestMatch = getTariffRange(fareAmount);

        if (bestMatch == null) return null;

        // ✅ Use index math — always forward in the list
        int destinationIndex = startIndex + bestMatch.maxStages;

        // Clamp to avoid index overflow
        if (destinationIndex >= stageNames.size()) {
            destinationIndex = stageNames.size() - 1;
        }

        return stageNames.get(destinationIndex);

    }

    @Nullable
    private TariffRange getTariffRange(int fareAmount) {
        TariffRange bestMatch = null;
        for (TariffRange r : allTariffRanges) {
            boolean match;
            match = r.adult == fareAmount;
            if (match) {
                if (bestMatch == null || r.maxStages > bestMatch.maxStages) {
                    bestMatch = r;
                }
            }
        }
        return bestMatch;
    }

    /** Day changed since the trip started: send the operator back to Menu. */
    private void showTripExpiredDialog() {
        AppDialog.with(requireActivity())
                .type(AppDialog.Type.WARNING)
                .title("Trip Expired!")
                .message("End Trip and Start a New One")
                .positive("OK", () -> {
                    startActivity(new Intent(getActivity(), Menu.class));
                    requireActivity().finish();
                })
                .cancelable(false)
                .show();
    }

    /** Send the print job to the ZCS printer */
    private void printTicket() {

        String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());

        if(!today.equals(Prefs.getDayDate(mContext))){
            showTripExpiredDialog();

        }


        if (mPrinter == null) {
            showPrinterError(null);
            return;
        }
        int printStatus;
        try {
            printStatus = mPrinter.getPrinterStatus();
        } catch (Throwable t) {
            showPrinterError(t);
            return;
        }

        if (printStatus == SdkResult.SDK_PRN_STATUS_PAPEROUT) {
            Toast.makeText(mContext, "OUT OF PAPER — Logged as Blank Ticket", Toast.LENGTH_LONG).show();

            AppExecutors.runBg(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(mContext);

                    // ⚙️ Fetch trip and insert blank ticket *off* main thread
                    TripEntity trip = db.ticketDao().getLastTrip();
                    int tripId = (trip != null) ? trip.tripId : 0; // use 0 if no trip

                    String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                    BlankTicketEntity blankTicket = new BlankTicketEntity(now, tripId);

                    long id = db.blankTicketDao().insert(blankTicket);

                    AppLog.w("Printer", "Paper-out logged as blank ticket #" + id + " (tripId=" + tripId + ")");

                    // Queued; sent in order after this trip's Start Trip
                    SyncClient.recordBlankTicket(mContext, tripId, id, now);

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

        routeName = Prefs.getSelectedRouteName(mContext);
        busNo = Prefs.getSelectedBus(mContext);
        ticketNo = Prefs.getTicketCount(mContext);
        ticketNo++;
        Prefs.saveTicketCount(mContext, ticketNo);

        if (!clampStageIndex()) {
            Toast.makeText(mContext, "No Station Selected", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentStage = stageNames.get(currentStageIndex);
        String destination = calculateDestinationStage(currentStageIndex, Integer.parseInt(fareValue));

        String currentDate = new SimpleDateFormat(
                "dd/MM/yyyy", Locale.getDefault()
        ).format(new Date());

        String currentTime = new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()
        ).format(new Date());

        int ticketAmount;
        try {
            ticketAmount = Integer.parseInt(fareValue.trim());
        } catch (NumberFormatException e) {
            AppLog.e("TicketError", "Tariff is not a valid number: " + fareValue);
            runOnUiSafe(() ->
                    Toast.makeText(mContext, "Invalid fare format", Toast.LENGTH_SHORT).show()
            );
            return;
        }


        try {
        PrnStrFormat fmt = new PrnStrFormat();

        //Company Name
        fmt.setTextSize(26);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString("* " + PrefsSecure.getTenantName(mContext) + " *", fmt);

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
        mPrinter.setPrintAppendString("ADULT" + "         " + "Rs " + fareValue, fmt);

        //Feedback
        fmt.setTextSize(24);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString(" * Feedback on " + PrefsSecure.getFeedbackNumber(getContext())  + " * ", fmt);

        //Spacer
        mPrinter.setPrintAppendString("", fmt);
        mPrinter.setPrintAppendString("", fmt);

        mPrinter.setPrintStart();
        } catch (Throwable t) {
            // Nothing is saved when printing fails (same as before, when this crashed).
            showPrinterError(t);
            return;
        }

        //Store in DB

        int finalTicketNo = ticketNo;
        AppExecutors.runBg(() -> {
            AppDatabase db = AppDatabase.getInstance(appCtx);
            TripEntity trip = db.ticketDao().getLastTrip();  // get current trip

            if (trip == null) return; // No trip in progress

            TicketEntity ticket = new TicketEntity();
            ticket.ticketNo   = toFourDigitString(finalTicketNo);
            ticket.tripId     = trip.tripId;
            ticket.time       = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            ticket.startStage = currentStage;
            ticket.endStage   = destination;
            ticket.fareType   = "Adult";
            ticket.amount     = ticketAmount;
            ticket.ticketType = "UP TO";

            long localTicketId = db.ticketDao().insertTicket(ticket);

            final String apiDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); // server expects yyyy-MM-dd

            SyncClient.recordTicket(
                    mContext,
                    trip.tripId,
                    localTicketId,
                    toFourDigitString(finalTicketNo),
                    apiDate,
                    currentTime,
                    currentStage,
                    ticket.ticketType,
                    destination,
                    ticket.fareType,
                    ticketAmount
            );
        });

        runOnUiSafe(() -> {
            if (selectedButton != null) {
                selectedButton.setBackgroundTintList(ColorStateList.valueOf(defaultButtonColor));
                fareValue = "";
                selectedButton = null;
            }
        });
    }


    private void printTicketTariff(String destination, String tariff) {

        String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());

        if(!today.equals(Prefs.getDayDate(mContext))){
            showTripExpiredDialog();

        }

        if (mPrinter == null) {
            showPrinterError(null);
            return;
        }
        int printStatus;
        try {
            printStatus = mPrinter.getPrinterStatus();
        } catch (Throwable t) {
            showPrinterError(t);
            return;
        }

        if (printStatus == SdkResult.SDK_PRN_STATUS_PAPEROUT) {
            Toast.makeText(mContext, "OUT OF PAPER — Logged as Blank Ticket", Toast.LENGTH_LONG).show();

            AppExecutors.runBg(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(mContext);

                    // ⚙️ Fetch trip and insert blank ticket *off* main thread
                    TripEntity trip = db.ticketDao().getLastTrip();
                    int tripId = (trip != null) ? trip.tripId : 0; // use 0 if no trip

                    String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                    BlankTicketEntity blankTicket = new BlankTicketEntity(now, tripId);

                    long id = db.blankTicketDao().insert(blankTicket);

                    AppLog.w("Printer", "Paper-out logged as blank ticket #" + id + " (tripId=" + tripId + ")");

                    // Queued; sent in order after this trip's Start Trip
                    SyncClient.recordBlankTicket(mContext, tripId, id, now);

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

        if (!clampStageIndex()) {
            Toast.makeText(mContext, "No Station Selected", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentStage = stageNames.get(currentStageIndex);

        String currentDate = new SimpleDateFormat(
                "dd/MM/yyyy", Locale.getDefault()
        ).format(new Date());

        String currentTime = new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()
        ).format(new Date());

        try {
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
        mPrinter.setPrintAppendString("ADULT" + "         " + "Rs " + tariff, fmt);

        //Feedback
        fmt.setTextSize(24);
        fmt.setAli(Layout.Alignment.ALIGN_CENTER);
        fmt.setFont(PrnTextFont.SANS_SERIF);
        mPrinter.setPrintAppendString(" * Feedback on " + PrefsSecure.getFeedbackNumber(getContext())  + " * ", fmt);

        //Spacer
        mPrinter.setPrintAppendString("", fmt);
        mPrinter.setPrintAppendString("", fmt);

        mPrinter.setPrintStart();
        } catch (Throwable t) {
            // Nothing is saved when printing fails (same as before, when this crashed).
            showPrinterError(t);
            return;
        }

        //Store in DB

        int finalTicketNo = ticketNo;
        AppExecutors.runBg(() -> {
            AppDatabase db = AppDatabase.getInstance(appCtx);
            TripEntity trip = db.ticketDao().getLastTrip();

            if (trip == null) return;

            final String apiDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); // server expects yyyy-MM-dd

            TicketEntity ticket = new TicketEntity();
            ticket.ticketNo   = toFourDigitString(finalTicketNo);
            ticket.tripId     = trip.tripId;
            ticket.time       = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            ticket.startStage = currentStage;
            ticket.endStage   = destination;
            ticket.fareType   = "Adult";
            ticket.amount     = Integer.parseInt(tariff);
            ticket.ticketType = "TO";

            long localTicketId = db.ticketDao().insertTicket(ticket);

            SyncClient.recordTicket(
                    mContext,
                    trip.tripId,
                    localTicketId,
                    toFourDigitString(finalTicketNo),
                    apiDate,
                    currentTime,
                    currentStage,
                    ticket.ticketType,
                    destination,
                    ticket.fareType,
                    Integer.parseInt(tariff)
            );



        });

    }


    /** Posts to the UI thread only while the fragment is attached; errors are logged, not thrown. */
    private void runOnUiSafe(Runnable r) {
        android.app.Activity a = getActivity();
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (!isAdded() || getActivity() == null) return;
            try {
                r.run();
            } catch (Throwable t) {
                AppLog.e("TicketFragment", "UI update failed", t);
            }
        });
    }

    /** Keeps currentStageIndex inside the stage list (it can be stale after a route data refresh). */
    private boolean clampStageIndex() {
        int size = Math.min(stageNames.size(), stageNos.size());
        if (size == 0) return false;
        if (currentStageIndex < 0) currentStageIndex = 0;
        if (currentStageIndex >= size) currentStageIndex = size - 1;
        return true;
    }

    private void showPrinterError(@Nullable Throwable t) {
        AppLog.e("Printer", "Ticket printing failed", t);
        android.app.Activity a = getActivity();
        if (a == null || !isAdded()) return;
        AppDialog.with(a)
                .type(AppDialog.Type.ERROR)
                .title("Printer error")
                .message("The ticket could not be printed. Check the printer and paper, then try again.")
                .positive("OK", null)
                .show();
    }

    public static String toFourDigitString(int value) {
        if (value < 0 || value > 9999) {
            return Integer.toString(value); // more than 9999 tickets in a day: print the full number
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

    //Shows the total amount for the current Shift
    @SuppressLint("SetTextI18n")
    private void showTotalAmountForToday(@Nullable TextView targetView) {
        AppExecutors.runBg(() -> {
            AppDatabase db = AppDatabase.getInstance(appCtx);
//            String today = Prefs.getDayDate(requireContext());
            String busNo = Prefs.getSelectedBus(appCtx);

//            final Integer[] total = {db.ticketDao().getTotalAmountForDate(today, busNo)};
            final Integer[] total = {db.ticketDao().getTotalAmountForLastShift(busNo)};

            runOnUiSafe(() -> {
                if (total[0] == null) total[0] = 0;

                if (targetView != null) {
                    targetView.setText("Rs " + total[0]);
                }
            });
        });
    }






    @Override
    public void onDestroyView() {
        super.onDestroyView();
//        allFareButtons.clear();
        selectedButton = null;
    }
}
