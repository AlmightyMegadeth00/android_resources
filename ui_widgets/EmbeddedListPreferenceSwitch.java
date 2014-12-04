/*
 * Copyright (C) 2014 VanirAOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.content.Context;
import android.preference.Preference;
import android.provider.Settings;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;

import com.android.settings.R;

/* This is a generic list preference switch/toggle that accepts some settings constant and can also be 
   used to enter a new preference fragment.  Call from XML resource:
  
   <com.android.settings.EmbeddedListPreferenceSwitch
		android:key="some_setting"
		android:fragment="com.android.settings.somefragment"
		android:title="@string/some_title"
		android:widgetLayout="@layout/listview_embedded_switchpreference" />

 */

public class EmbeddedListPreferenceSwitch extends Preference implements OnCheckedChangeListener {

    private static final String TAG = "ClickablePreferenceSwitch";

    private Switch mSwitch;
    private String mSetting = null;
    private int mDefault = 0;

    public EmbeddedListPreferenceSwitch(Context context) {
        super(context);
    }

    public EmbeddedListPreferenceSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EmbeddedListPreferenceSwitch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setSettingToWatch(String setting) {
        setSettingToWatch(setting, 0);
    }

    public void setSettingToWatch(String setting, int defaultVal) {
        mDefault = defaultVal;
        if (mSwitch != null && mSetting != null)
            mSwitch.setOnCheckedChangeListener(null);
        mSetting = setting;
        if (mSwitch != null && mSetting != null) {
            mSwitch.setVisibility(View.VISIBLE);
            mSwitch.setChecked(Settings.System.getIntForUser(getContext().getContentResolver(),
                    mSetting, defaultVal, UserHandle.USER_CURRENT) == 1);
            mSwitch.setOnCheckedChangeListener(this);
        } else if (mSwitch != null) {
            mSwitch.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mSwitch = (Switch) view.findViewById(R.id.mswitch);
        if (mSetting != null) {
            mSwitch.setChecked(Settings.System.getIntForUser(getContext().getContentResolver(),
                    mSetting, mDefault, UserHandle.USER_CURRENT) == 1);
            mSwitch.setOnCheckedChangeListener(this);
        } else {
            mSwitch.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (mSetting != null)
            Settings.System.putInt(getContext().getContentResolver(),
                    mSetting, isChecked ? 1 : 0);
    }
}
