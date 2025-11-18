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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PrintTicket extends AppCompatActivity {
    private TabLayout      tabLayout;
    private ViewPager2     viewPagerContainer;
    private View           loadingLayout;
    private TicketPagerAdapter adapter;
    private StageViewModel stageVm;
    private final String[] tabTitles = {"Adult", "Student", "Child"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print_ticket);

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

            AlertDialog dialog = new AlertDialog.Builder(PrintTicket.this)
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
                Intent i = new Intent(this, Menu.class);
                startActivity(i);
                finish();
            });

        }


        // — find your views
        tabLayout          = findViewById(R.id.tabLayout);
        viewPagerContainer = findViewById(R.id.container);
        loadingLayout      = findViewById(R.id.layout_loading);

        // — edge-to-edge (optional)
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                    return insets;
                }
        );

        // — start in “loading” mode
        loadingLayout.setVisibility(View.VISIBLE);
        tabLayout.setVisibility(View.GONE);
        viewPagerContainer.setVisibility(View.GONE);

        // — get the route
        String routeId = Prefs.getSelectedRoute(this);
        if (routeId == null) {
            loadingLayout.setVisibility(View.GONE);
            return;
        }

        // — obtain + initialize the ViewModel
        stageVm = new ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(StageViewModel.class);
        stageVm.loadStagesForRoute(routeId);

        // — observe the *live* list from Room
        stageVm.getStages().observe(this, stages -> {
            // once we see a non-empty list *and* we haven’t built our pager yet:
            if (stages != null && !stages.isEmpty() && adapter == null) {
                setupViewPager();  // will hide the loader
            }
        });
    }

    private void setupViewPager() {
        if (adapter != null) return;

        // 1️⃣ hide loader, show real UI
        loadingLayout.setVisibility(View.GONE);
        tabLayout.setVisibility(View.VISIBLE);
        viewPagerContainer.setVisibility(View.VISIBLE);

        // 2️⃣ build our adapter with the shared VM
        adapter = new TicketPagerAdapter(this, stageVm);
        viewPagerContainer.setAdapter(adapter);

        // 3️⃣ wire up custom‐view tabs
        LayoutInflater inflater = LayoutInflater.from(this);
        new TabLayoutMediator(
                tabLayout,
                viewPagerContainer,
                (tab, position) -> {
                    int layoutRes = (position == 0)
                            ? R.layout.adult_tab_bg
                            : (position == 1)
                            ? R.layout.student_tab_bg
                            : R.layout.child_tab_bg;
                    View custom = inflater.inflate(layoutRes, tabLayout, false);
                    TextView tv = custom.findViewById(R.id.tab_label);
                    tv.setText(tabTitles[position]);
                    tab.setCustomView(custom);
                }
        ).attach();
    }
}
