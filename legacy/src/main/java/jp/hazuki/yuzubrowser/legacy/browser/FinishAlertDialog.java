/*
 * Copyright (C) 2017-2019 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.legacy.browser;

import android.app.Dialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.content.res.ColorStateList;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import jp.hazuki.yuzubrowser.ui.theme.ThemeData;

import androidx.annotation.NonNull;
import jp.hazuki.yuzubrowser.legacy.R;
import jp.hazuki.yuzubrowser.ui.preference.CustomDialogPreference;
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs;

public class FinishAlertDialog extends CustomDialogPreference {
    private final boolean mShowMessage;
    private int mPositiveLabel = android.R.string.ok;
    private int mNegativeLabel = android.R.string.cancel;
    private int mNeutralLabel = 0;
    private int clearTabNo = -1;

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which, int new_value);
    }

    public FinishAlertDialog(Context context) {
        this(context, null, true);
    }

    public FinishAlertDialog(Context context, AttributeSet attrs) {
        this(context, attrs, false);
    }

    public FinishAlertDialog(Context context, boolean showMessage) {
        this(context, null, showMessage);
    }

    private FinishAlertDialog(Context context, AttributeSet attrs, boolean showMessage) {
        super(context, attrs);
        mShowMessage = showMessage;
    }

    public FinishAlertDialog setPositiveButton(int id) {
        mPositiveLabel = id;
        return this;
    }

    public FinishAlertDialog setNegativeButton(int id) {
        mNegativeLabel = id;
        return this;
    }

    public FinishAlertDialog setNeutralButton(int id) {
        mNeutralLabel = id;
        return this;
    }

    public FinishAlertDialog setClearTabNo(int clearTabNo) {
        this.clearTabNo = clearTabNo;
        return this;
    }

    @NonNull
    @Override
    protected CustomDialogFragment crateCustomDialog() {
        return FinishDialog.newInstance(mShowMessage, mPositiveLabel, mNegativeLabel, mNeutralLabel, clearTabNo);
    }

    public static class FinishDialog extends CustomDialogFragment {
        private static final String SHOW_MESSAGE = "message";
        private static final String POSITIVE = "positive";
        private static final String NEGATIVE = "negative";
        private static final String NEUTRAL = "neutral";
        private static final String CLEAR_TAB = "tab";

        private OnFinishDialogCallBack callBack;

        public static FinishDialog newInstance(boolean showMessage, int positive, int negative, int neutral, int tabNo) {
            FinishDialog dialog = new FinishDialog();
            Bundle bundle = new Bundle();
            bundle.putBoolean(SHOW_MESSAGE, showMessage);
            bundle.putInt(POSITIVE, positive);
            bundle.putInt(NEGATIVE, negative);
            bundle.putInt(NEUTRAL, neutral);
            bundle.putInt(CLEAR_TAB, tabNo);
            dialog.setArguments(bundle);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            boolean showMessage = getArguments().getBoolean(SHOW_MESSAGE);
            int positive = getArguments().getInt(POSITIVE);
            int negative = getArguments().getInt(NEGATIVE);
            int neutral = getArguments().getInt(NEUTRAL);
            int clearTabNo = getArguments().getInt(CLEAR_TAB);
            View view = LayoutInflater.from(getContext()).inflate(R.layout.finish_alert, null);
            TextView textView = view.findViewById(R.id.textView);
            final CheckBox cacheCheckBox = view.findViewById(R.id.cacheCheckBox);
            final CheckBox cookieCheckBox = view.findViewById(R.id.cookieCheckBox);
            final CheckBox databaseCheckBox = view.findViewById(R.id.databaseCheckBox);
            final CheckBox passwordCheckBox = view.findViewById(R.id.passwordCheckBox);
            final CheckBox formdataCheckBox = view.findViewById(R.id.formdataCheckBox);
            final CheckBox faviconCheckBox = view.findViewById(R.id.faviconCheckBox);
            final CheckBox closeallCheckBox = view.findViewById(R.id.closeallCheckBox);
            final CheckBox historyCheckBox = view.findViewById(R.id.deleteHistoryCheckBox);
            final CheckBox searchCheckBox = view.findViewById(R.id.deleteSearchQueryCheckBox);
            final CheckBox geoCheckBox = view.findViewById(R.id.removeAllGeoPermissions);

            if (!showMessage)
                textView.setVisibility(View.GONE);

            final int def = AppPrefs.finish_alert_default.get();
            cacheCheckBox.setChecked((def & 0x01) != 0);
            cookieCheckBox.setChecked((def & 0x02) != 0);
            databaseCheckBox.setChecked((def & 0x04) != 0);
            passwordCheckBox.setChecked((def & 0x08) != 0);
            formdataCheckBox.setChecked((def & 0x10) != 0);
            historyCheckBox.setChecked((def & 0x20) != 0);
            searchCheckBox.setChecked((def & 0x40) != 0);
            geoCheckBox.setChecked((def & 0x80) != 0);
            faviconCheckBox.setChecked((def & 0x100) != 0);

            if (!AppPrefs.save_last_tabs.get()) {
                closeallCheckBox.setVisibility(View.GONE);
                View sessionHeader = view.findViewById(R.id.sessionHeader);
                View divider = view.findViewById(R.id.divider);
                if (sessionHeader != null) {
                    sessionHeader.setVisibility(View.GONE);
                }
                if (divider != null) {
                    divider.setVisibility(View.GONE);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                formdataCheckBox.setVisibility(View.GONE);
                formdataCheckBox.setChecked(false);
            }

            ThemeData themeData = ThemeData.getInstance();
            int backgroundColor = 0;
            int textColor = 0;
            int summaryColor = 0;
            int categoryColor = 0;
            int dividerColor = 0;
            int accentColor = 0;

            if (themeData != null) {
                backgroundColor = themeData.settingsBackgroundColor != 0 ? themeData.settingsBackgroundColor :
                        (themeData.menuBackgroundColor != 0 ? themeData.menuBackgroundColor :
                        (themeData.toolbarBackgroundColor != 0 ? themeData.toolbarBackgroundColor : 0));
                
                textColor = themeData.settingsTextColor != 0 ? themeData.settingsTextColor :
                        (themeData.menuTextColor != 0 ? themeData.menuTextColor :
                        (themeData.toolbarTextColor != 0 ? themeData.toolbarTextColor : 0));

                summaryColor = themeData.settingsSummaryColor != 0 ? themeData.settingsSummaryColor :
                        (themeData.menuTextColor != 0 ? adjustAlpha(themeData.menuTextColor, 0.72f) : 0);

                categoryColor = themeData.settingsCategoryColor != 0 ? themeData.settingsCategoryColor :
                        (themeData.settingsSummaryColor != 0 ? themeData.settingsSummaryColor :
                        (themeData.tabAccentColor != 0 ? themeData.tabAccentColor : 0));

                dividerColor = themeData.settingsDividerColor != 0 ? themeData.settingsDividerColor :
                        (themeData.menuDividerColor != 0 ? themeData.menuDividerColor : 0);

                accentColor = themeData.tabAccentColor != 0 ? themeData.tabAccentColor :
                        (themeData.settingsSwitchThumbColor != 0 ? themeData.settingsSwitchThumbColor : 0);
            }

            if (themeData != null) {
                if (textColor != 0) {
                    cacheCheckBox.setTextColor(textColor);
                    cookieCheckBox.setTextColor(textColor);
                    databaseCheckBox.setTextColor(textColor);
                    passwordCheckBox.setTextColor(textColor);
                    formdataCheckBox.setTextColor(textColor);
                    faviconCheckBox.setTextColor(textColor);
                    closeallCheckBox.setTextColor(textColor);
                    historyCheckBox.setTextColor(textColor);
                    searchCheckBox.setTextColor(textColor);
                    geoCheckBox.setTextColor(textColor);
                }

                if (summaryColor != 0) {
                    textView.setTextColor(summaryColor);
                }

                if (categoryColor != 0) {
                    TextView sessionHeader = view.findViewById(R.id.sessionHeader);
                    TextView privacyHeader = view.findViewById(R.id.privacyHeader);
                    if (sessionHeader != null) {
                        sessionHeader.setTextColor(categoryColor);
                    }
                    if (privacyHeader != null) {
                        privacyHeader.setTextColor(categoryColor);
                    }
                }

                if (dividerColor != 0) {
                    View divider = view.findViewById(R.id.divider);
                    if (divider != null) {
                        divider.setBackgroundColor(dividerColor);
                    }
                }

                if (accentColor != 0) {
                    ColorStateList tintList = ColorStateList.valueOf(accentColor);
                    cacheCheckBox.setButtonTintList(tintList);
                    cookieCheckBox.setButtonTintList(tintList);
                    databaseCheckBox.setButtonTintList(tintList);
                    passwordCheckBox.setButtonTintList(tintList);
                    formdataCheckBox.setButtonTintList(tintList);
                    faviconCheckBox.setButtonTintList(tintList);
                    closeallCheckBox.setButtonTintList(tintList);
                    historyCheckBox.setButtonTintList(tintList);
                    searchCheckBox.setButtonTintList(tintList);
                    geoCheckBox.setButtonTintList(tintList);
                }
            }

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());

            builder.setTitle((showMessage) ? R.string.confirm : R.string.pref_clear_data_at_finish)
                    .setView(view)
                    .setPositiveButton(positive, (dialog, which) -> {
                        int new_settings = 0;

                        if (cacheCheckBox.isChecked())
                            new_settings |= 0x01;

                        if (cookieCheckBox.isChecked())
                            new_settings |= 0x02;

                        if (databaseCheckBox.isChecked())
                            new_settings |= 0x04;

                        if (passwordCheckBox.isChecked())
                            new_settings |= 0x08;

                        if (formdataCheckBox.isChecked())
                            new_settings |= 0x10;

                        if (historyCheckBox.isChecked())
                            new_settings |= 0x20;

                        if (searchCheckBox.isChecked())
                            new_settings |= 0x40;

                        if (geoCheckBox.isChecked())
                            new_settings |= 0x80;

                        if (faviconCheckBox.isChecked())
                            new_settings |= 0x100;

                        if (closeallCheckBox.isChecked())
                            new_settings |= 0x1000;

                        AppPrefs.finish_alert_default.set(new_settings);
                        AppPrefs.commit(getContext(), AppPrefs.finish_alert_default);

                        if (callBack != null)
                            callBack.onFinishPositiveButtonClicked(clearTabNo, new_settings);
                    })
                    .setNegativeButton(negative, null)
            ;
            if (neutral != 0)
                builder.setNeutralButton(neutral, (dialog, which) -> {
                    if (callBack != null)
                        callBack.onFinishNeutralButtonClicked(clearTabNo, def);
                });

            final AlertDialog dialog = builder.create();

            if (themeData != null && backgroundColor != 0) {
                float density = getResources().getDisplayMetrics().density;
                GradientDrawable windowBg = new GradientDrawable();
                windowBg.setShape(GradientDrawable.RECTANGLE);
                windowBg.setColor(backgroundColor);
                windowBg.setCornerRadius(density * 24f); // 24dp rounded corners
                if (dividerColor != 0) {
                    windowBg.setStroke((int) (density * 1.5f), dividerColor);
                }

                int inset = (int) (density * 16f); // 16dp margins
                InsetDrawable insetDrawable = new InsetDrawable(windowBg, inset, inset, inset, inset);

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawable(insetDrawable);
                }
            }

            final int finalAccentColor = accentColor;
            dialog.setOnShowListener(d -> {
                if (themeData != null && finalAccentColor != 0) {
                    Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    Button negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                    Button neutralButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);

                    if (positiveButton != null) positiveButton.setTextColor(finalAccentColor);
                    if (negativeButton != null) negativeButton.setTextColor(finalAccentColor);
                    if (neutralButton != null) neutralButton.setTextColor(finalAccentColor);
                }
            });

            return dialog;
        }

        private static int adjustAlpha(int color, float alphaFactor) {
            int alpha = Math.min(255, Math.max(0, (int) (Color.alpha(color) * alphaFactor)));
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);

            if (getActivity() instanceof OnFinishDialogCallBack)
                callBack = (OnFinishDialogCallBack) getActivity();
        }

        @Override
        public void onDetach() {
            super.onDetach();

            callBack = null;
        }
    }

    public interface OnFinishDialogCallBack {
        void onFinishPositiveButtonClicked(int clearTabNo, int newSetting);

        void onFinishNeutralButtonClicked(int clearTabNo, int newSetting);
    }
}
