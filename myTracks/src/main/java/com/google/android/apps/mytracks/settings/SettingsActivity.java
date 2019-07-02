/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.apps.mytracks.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;

import com.google.android.apps.mytracks.util.DialogUtils;
import com.google.android.apps.mytracks.util.IntentUtils;
import com.google.android.apps.mytracks.util.PreferencesUtils;
import com.google.android.maps.mytracks.R;

/**
 * An activity for accessing settings.
 * 
 * @author Jimmy Shih
 */
public class SettingsActivity extends AbstractSettingsActivity {

  @Override
  protected void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    addPreferencesFromResource(R.xml.settings);

    configPreference(R.string.settings_chart_key, ChartSettingsActivity.class);
    configPreference(R.string.settings_stats_key, StatsSettingsActivity.class);
    configPreference(R.string.settings_recording_key, RecordingSettingsActivity.class);
    configPreference(R.string.settings_sensor_key, SensorSettingsActivity.class);
    configPreference(R.string.settings_advanced_key, AdvancedSettingsActivity.class);
  }

  /**
   * Configures a preference by starting a new activity when it is clicked.
   * 
   * @param key the preference key
   * @param cl the class to start the new activity
   */
  private void configPreference(int key, final Class<?> cl) {
    Preference preference = findPreference(getString(key));
    preference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
        @Override
      public boolean onPreferenceClick(Preference pref) {
        Intent intent = IntentUtils.newIntent(SettingsActivity.this, cl);
        startActivity(intent);
        return true;
      }
    });
  }
}
