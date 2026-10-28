package com.ayb.busticketpos;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;

import com.google.android.material.button.MaterialButton;

import java.util.function.Consumer;

/**
 * App-wide card dialog: rounded white card, coloured icon badge, title, message and
 * side-by-side buttons. Replaces the stock AlertDialogs so every popup looks the same.
 */
public final class AppDialog {

    public enum Type {
        INFO(R.drawable.ic_info_24, "#031FB8", "#E6E9FB"),
        QUESTION(R.drawable.ic_help_24, "#031FB8", "#E6E9FB"),
        SUCCESS(R.drawable.ic_check_circle_24, "#2B9B06", "#E5F5E0"),
        WARNING(R.drawable.ic_warning_24, "#E65100", "#FFF0E0"),
        ERROR(R.drawable.ic_error_24, "#D32F2F", "#FDE8E7");

        @DrawableRes final int icon;
        final int accent;
        final int tint;

        Type(@DrawableRes int icon, String accent, String tint) {
            this.icon = icon;
            this.accent = Color.parseColor(accent);
            this.tint = Color.parseColor(tint);
        }
    }

    private final Context ctx;
    private Type type = Type.INFO;
    private CharSequence title;
    @Nullable private CharSequence message;
    private String positiveText = "OK";
    @Nullable private Runnable positiveAction;
    @Nullable private Consumer<AppCompatDialog> positiveKeepOpen;
    @Nullable private String negativeText;
    @Nullable private Runnable negativeAction;
    @Nullable private Integer positiveColor;
    private boolean cancelable = true;

    private AppDialog(Context ctx) {
        this.ctx = ctx;
    }

    public static AppDialog with(Context ctx) {
        return new AppDialog(ctx);
    }

    public AppDialog type(Type type) {
        this.type = type;
        return this;
    }

    public AppDialog title(CharSequence title) {
        this.title = title;
        return this;
    }

    public AppDialog message(@Nullable CharSequence message) {
        this.message = message;
        return this;
    }

    /** Positive button: runs the action (if any), then closes the dialog. */
    public AppDialog positive(String text, @Nullable Runnable action) {
        this.positiveText = text;
        this.positiveAction = action;
        this.positiveKeepOpen = null;
        return this;
    }

    /** Positive button that leaves closing to the action (e.g. validation). */
    public AppDialog positiveKeepOpen(String text, Consumer<AppCompatDialog> action) {
        this.positiveText = text;
        this.positiveKeepOpen = action;
        this.positiveAction = null;
        return this;
    }

    public AppDialog positiveColor(int color) {
        this.positiveColor = color;
        return this;
    }

    /** Negative button: runs the action (if any), then closes the dialog. */
    public AppDialog negative(String text, @Nullable Runnable action) {
        this.negativeText = text;
        this.negativeAction = action;
        return this;
    }

    /** False = Back key and taps outside do not close it (same as AlertDialog setCancelable(false)). */
    public AppDialog cancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public AppCompatDialog show() {
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_app, null);
        AppCompatDialog dialog = wrap(ctx, root, 0.9f);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);

        View iconBg = root.findViewById(R.id.app_dialog_icon_bg);
        ImageView icon = root.findViewById(R.id.app_dialog_icon);
        iconBg.setBackgroundTintList(ColorStateList.valueOf(type.tint));
        icon.setImageResource(type.icon);
        icon.setImageTintList(ColorStateList.valueOf(type.accent));

        ((TextView) root.findViewById(R.id.app_dialog_title)).setText(title);
        TextView tvMessage = root.findViewById(R.id.app_dialog_message);
        if (message != null && message.length() > 0) {
            tvMessage.setText(message);
            tvMessage.setVisibility(View.VISIBLE);
        }

        MaterialButton btnPositive = root.findViewById(R.id.app_dialog_positive);
        btnPositive.setText(positiveText);
        btnPositive.setBackgroundTintList(ColorStateList.valueOf(
                positiveColor != null ? positiveColor : type.accent));
        btnPositive.setOnClickListener(v -> {
            if (positiveKeepOpen != null) {
                positiveKeepOpen.accept(dialog);
                return;
            }
            dialog.dismiss();
            if (positiveAction != null) positiveAction.run();
        });

        if (negativeText != null) {
            MaterialButton btnNegative = root.findViewById(R.id.app_dialog_negative);
            btnNegative.setText(negativeText);
            btnNegative.setVisibility(View.VISIBLE);
            root.findViewById(R.id.app_dialog_button_gap).setVisibility(View.VISIBLE);
            btnNegative.setOnClickListener(v -> {
                dialog.dismiss();
                if (negativeAction != null) negativeAction.run();
            });
        }

        showSafely(ctx, dialog);
        return dialog;
    }

    /**
     * Shows a dialog unless its activity is finishing / destroyed (showing then throws
     * BadTokenException and crashes the app).
     */
    public static void showSafely(Context ctx, @Nullable AppCompatDialog dialog) {
        if (dialog == null) return;
        if (ctx instanceof Activity) {
            Activity a = (Activity) ctx;
            if (a.isFinishing() || a.isDestroyed()) {
                AppLog.w("AppDialog", "Skipped dialog: activity is closing");
                return;
            }
        }
        try {
            dialog.show();
        } catch (Throwable t) {
            AppLog.e("AppDialog", "Could not show dialog", t);
        }
    }

    /** Dismiss that never throws (e.g. activity already gone). */
    public static void dismissSafely(@Nullable AppCompatDialog dialog) {
        if (dialog == null) return;
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (Throwable t) {
            AppLog.w("AppDialog", "Could not dismiss dialog", t);
        }
    }

    /** Non-cancelable spinner card, e.g. "Printing...". Caller dismisses it. */
    public static AppCompatDialog showProgress(Context ctx, CharSequence text) {
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_app, null);
        AppCompatDialog dialog = wrap(ctx, root, 0.8f);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        root.findViewById(R.id.app_dialog_icon_bg)
                .setBackgroundTintList(ColorStateList.valueOf(Type.INFO.tint));
        root.findViewById(R.id.app_dialog_icon).setVisibility(View.GONE);
        ProgressBar pb = root.findViewById(R.id.app_dialog_progress);
        pb.setVisibility(View.VISIBLE);
        ((TextView) root.findViewById(R.id.app_dialog_title)).setText(text);
        root.findViewById(R.id.app_dialog_buttons).setVisibility(View.GONE);

        showSafely(ctx, dialog);
        return dialog;
    }

    /** Wraps a custom card layout (which draws its own rounded background) in a dialog. Not shown yet. */
    public static AppCompatDialog wrap(Context ctx, View content, float widthFraction) {
        AppCompatDialog dialog = new AppCompatDialog(ctx);
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown == null) return;
            int width = (int) (ctx.getResources().getDisplayMetrics().widthPixels * widthFraction);
            shown.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        });
        return dialog;
    }
}
