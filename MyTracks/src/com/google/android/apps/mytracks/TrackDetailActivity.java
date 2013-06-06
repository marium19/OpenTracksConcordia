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

package com.google.android.apps.mytracks;

import com.google.android.apps.mytracks.content.MyTracksProviderUtils;
import com.google.android.apps.mytracks.content.Track;
import com.google.android.apps.mytracks.content.TrackDataHub;
import com.google.android.apps.mytracks.content.Waypoint;
import com.google.android.apps.mytracks.content.WaypointCreationRequest;
import com.google.android.apps.mytracks.fragments.ChartFragment;
import com.google.android.apps.mytracks.fragments.DeleteTrackDialogFragment;
import com.google.android.apps.mytracks.fragments.DeleteTrackDialogFragment.DeleteTrackCaller;
import com.google.android.apps.mytracks.fragments.ExportDialogFragment;
import com.google.android.apps.mytracks.fragments.ExportDialogFragment.ExportCaller;
import com.google.android.apps.mytracks.fragments.ExportDialogFragment.ExportType;
import com.google.android.apps.mytracks.fragments.FrequencyDialogFragment;
import com.google.android.apps.mytracks.fragments.MyTracksMapFragment;
import com.google.android.apps.mytracks.fragments.StatsFragment;
import com.google.android.apps.mytracks.io.file.SaveActivity;
import com.google.android.apps.mytracks.io.file.TrackFileFormat;
import com.google.android.apps.mytracks.io.sendtogoogle.SendRequest;
import com.google.android.apps.mytracks.services.TrackRecordingServiceConnection;
import com.google.android.apps.mytracks.settings.SettingsActivity;
import com.google.android.apps.mytracks.util.AnalyticsUtils;
import com.google.android.apps.mytracks.util.ApiAdapterFactory;
import com.google.android.apps.mytracks.util.GoogleFeedbackUtils;
import com.google.android.apps.mytracks.util.IntentUtils;
import com.google.android.apps.mytracks.util.PreferencesUtils;
import com.google.android.apps.mytracks.util.TrackRecordingServiceConnectionUtils;
import com.google.android.maps.mytracks.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

import java.util.Locale;

/**
 * An activity to show the track detail.
 * 
 * @author Leif Hendrik Wilden
 * @author Rodrigo Damazio
 */
public class TrackDetailActivity extends AbstractSendToGoogleActivity
    implements ExportCaller, DeleteTrackCaller {

  public static final String EXTRA_TRACK_ID = "track_id";
  public static final String EXTRA_MARKER_ID = "marker_id";

  private static final String CURRENT_TAB_TAG_KEY = "current_tab_tag_key";

  // The following are set in onCreate
  private MyTracksProviderUtils myTracksProviderUtils;
  private SharedPreferences sharedPreferences;
  private TrackRecordingServiceConnection trackRecordingServiceConnection;
  private TrackDataHub trackDataHub;
  private TabHost tabHost;
  private ViewPager viewPager;
  private TabsAdapter tabsAdapter;
  private TrackController trackController;

  // From intent
  private long trackId;
  private long markerId;

  // Preferences
  private long recordingTrackId = PreferencesUtils.RECORDING_TRACK_ID_DEFAULT;
  private boolean recordingTrackPaused = PreferencesUtils.RECORDING_TRACK_PAUSED_DEFAULT;

  private MenuItem insertMarkerMenuItem;
  private MenuItem playMenuItem;
  private MenuItem shareMenuItem;
  private MenuItem exportMenuItem;
  private MenuItem voiceFrequencyMenuItem;
  private MenuItem splitFrequencyMenuItem;
  private MenuItem feedbackMenuItem;

  private final Runnable bindChangedCallback = new Runnable() {
      @Override
    public void run() {
      // After binding changes (is available), update the total time in
      // trackController.
      runOnUiThread(new Runnable() {
          @Override
        public void run() {
          trackController.update(trackId == recordingTrackId, recordingTrackPaused);
        }
      });
    }
  };

  /*
   * Note that sharedPreferenceChangeListener cannot be an anonymous inner
   * class. Anonymous inner class will get garbage collected.
   */
  private final OnSharedPreferenceChangeListener
      sharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
          @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
          // Note that key can be null
          if (key == null || key.equals(
              PreferencesUtils.getKey(TrackDetailActivity.this, R.string.recording_track_id_key))) {
            recordingTrackId = PreferencesUtils.getLong(
                TrackDetailActivity.this, R.string.recording_track_id_key);
          }
          if (key == null || key.equals(PreferencesUtils.getKey(
              TrackDetailActivity.this, R.string.recording_track_paused_key))) {
            recordingTrackPaused = PreferencesUtils.getBoolean(TrackDetailActivity.this,
                R.string.recording_track_paused_key,
                PreferencesUtils.RECORDING_TRACK_PAUSED_DEFAULT);
          }
          if (key != null) {
            runOnUiThread(new Runnable() {
                @Override
              public void run() {
                boolean isRecording = trackId == recordingTrackId;
                updateMenuItems(isRecording, recordingTrackPaused);
                trackController.update(isRecording, recordingTrackPaused);
              }
            });
          }
        }
      };

  private final OnClickListener recordListener = new OnClickListener() {
      @Override
    public void onClick(View v) {
      if (recordingTrackPaused) {
        // Paused -> Resume
        AnalyticsUtils.sendPageViews(TrackDetailActivity.this, "/action/resume_track");
        updateMenuItems(true, false);
        TrackRecordingServiceConnectionUtils.resumeTrack(trackRecordingServiceConnection);
        trackController.update(true, false);
      } else {
        // Recording -> Paused
        AnalyticsUtils.sendPageViews(TrackDetailActivity.this, "/action/pause_track");
        updateMenuItems(true, true);
        TrackRecordingServiceConnectionUtils.pauseTrack(trackRecordingServiceConnection);
        trackController.update(true, true);
      }
    }
  };

  private final OnClickListener stopListener = new OnClickListener() {
      @Override
    public void onClick(View v) {
      AnalyticsUtils.sendPageViews(TrackDetailActivity.this, "/action/stop_recording");
      updateMenuItems(false, true);
      TrackRecordingServiceConnectionUtils.stopRecording(
          TrackDetailActivity.this, trackRecordingServiceConnection, true);
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    myTracksProviderUtils = MyTracksProviderUtils.Factory.get(this);
    handleIntent(getIntent());

    sharedPreferences = getSharedPreferences(Constants.SETTINGS_NAME, Context.MODE_PRIVATE);

    trackRecordingServiceConnection = new TrackRecordingServiceConnection(
        this, bindChangedCallback);
    trackDataHub = TrackDataHub.newInstance(this);

    tabHost = (TabHost) findViewById(android.R.id.tabhost);
    tabHost.setup();

    viewPager = (ViewPager) findViewById(R.id.pager);

    tabsAdapter = new TabsAdapter(this, tabHost, viewPager);

    TabSpec mapTabSpec = tabHost.newTabSpec(MyTracksMapFragment.MAP_FRAGMENT_TAG).setIndicator(
        getString(R.string.track_detail_map_tab), getResources().getDrawable(R.drawable.tab_map));
    tabsAdapter.addTab(mapTabSpec, MyTracksMapFragment.class, null);

    TabSpec chartTabSpec = tabHost.newTabSpec(ChartFragment.CHART_FRAGMENT_TAG).setIndicator(
        getString(R.string.track_detail_chart_tab),
        getResources().getDrawable(R.drawable.tab_chart));
    tabsAdapter.addTab(chartTabSpec, ChartFragment.class, null);

    TabSpec statsTabSpec = tabHost.newTabSpec(StatsFragment.STATS_FRAGMENT_TAG).setIndicator(
        getString(R.string.track_detail_stats_tab),
        getResources().getDrawable(R.drawable.tab_stats));
    tabsAdapter.addTab(statsTabSpec, StatsFragment.class, null);

    if (savedInstanceState != null) {
      tabHost.setCurrentTabByTag(savedInstanceState.getString(CURRENT_TAB_TAG_KEY));
    }
    
    // Set the background after all three tabs are added
    ApiAdapterFactory.getApiAdapter().setTabBackground(tabHost.getTabWidget());
    
    trackController = new TrackController(
        this, trackRecordingServiceConnection, false, recordListener, stopListener);
  }

  @Override
  protected void onStart() {
    super.onStart();

    sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    sharedPreferenceChangeListener.onSharedPreferenceChanged(null, null);

    TrackRecordingServiceConnectionUtils.startConnection(this, trackRecordingServiceConnection);
    trackDataHub.start();
    AnalyticsUtils.sendPageViews(this, "/page/track_detail");
  }

  @Override
  protected void onResume() {
    super.onResume();
    trackDataHub.loadTrack(trackId);

    // Update UI
    boolean isRecording = trackId == recordingTrackId;
    updateMenuItems(isRecording, recordingTrackPaused);
    trackController.onResume(isRecording, recordingTrackPaused);
  }

  @Override
  protected void onPause() {
    super.onPause();
    trackController.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    trackRecordingServiceConnection.unbind();
    trackDataHub.stop();
    AnalyticsUtils.dispatch();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(CURRENT_TAB_TAG_KEY, tabHost.getCurrentTabTag());
  }

  @Override
  protected int getLayoutResId() {
    return R.layout.track_detail;
  }

  @Override
  protected boolean hideTitle() {
    return true;
  }

  @Override
  protected void onHomeSelected() {
    /*
     * According to
     * http://developer.android.com/training/implementing-navigation
     * /ancestral.html, we should use NavUtils.shouldUpRecreateTask instead of
     * always creating a new back stack. However, NavUtils.shouldUpRecreateTask
     * seems to always return false.
     */
    TaskStackBuilder.create(this).addParentStack(TrackDetailActivity.class).startActivities();
    finish();
  }

  @Override
  public void onNewIntent(Intent intent) {
    setIntent(intent);
    handleIntent(intent);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.track_detail, menu);

    Track track = myTracksProviderUtils.getTrack(trackId);
    boolean sharedWithMe = track != null ? track.isSharedWithMe() : true;
    menu.findItem(R.id.track_detail_edit).setVisible(!sharedWithMe);
    shareMenuItem = menu.findItem(R.id.track_detail_share);
    shareMenuItem.setEnabled(!sharedWithMe);
    shareMenuItem.setVisible(!sharedWithMe);

    insertMarkerMenuItem = menu.findItem(R.id.track_detail_insert_marker);
    playMenuItem = menu.findItem(R.id.track_detail_play);
    exportMenuItem = menu.findItem(R.id.track_detail_export);
    voiceFrequencyMenuItem = menu.findItem(R.id.track_detail_voice_frequency);
    splitFrequencyMenuItem = menu.findItem(R.id.track_detail_split_frequency);
    feedbackMenuItem = menu.findItem(R.id.track_detail_split_frequency);
    feedbackMenuItem.setVisible(GoogleFeedbackUtils.isAvailable(this));

    updateMenuItems(trackId == recordingTrackId, recordingTrackPaused);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    boolean showSensorState = !PreferencesUtils.SENSOR_TYPE_DEFAULT.equals(
        PreferencesUtils.getString(
            this, R.string.sensor_type_key, PreferencesUtils.SENSOR_TYPE_DEFAULT));
    menu.findItem(R.id.track_detail_sensor_state).setVisible(showSensorState);
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent intent;
    switch (item.getItemId()) {
      case R.id.track_detail_insert_marker:
        AnalyticsUtils.sendPageViews(this, "/action/insert_marker");
        intent = IntentUtils.newIntent(this, MarkerEditActivity.class)
            .putExtra(MarkerEditActivity.EXTRA_TRACK_ID, trackId);
        startActivity(intent);
        return true;
      case R.id.track_detail_play:
        confirmPlay(new long[] {trackId});
        return true;
      case R.id.track_detail_share:
        confirmShare(trackId);
        return true;
      case R.id.track_detail_markers:
        intent = IntentUtils.newIntent(this, MarkerListActivity.class)
            .putExtra(MarkerListActivity.EXTRA_TRACK_ID, trackId);
        startActivity(intent);
        return true;
      case R.id.track_detail_voice_frequency:
        FrequencyDialogFragment.newInstance(R.string.voice_frequency_key,
            PreferencesUtils.VOICE_FREQUENCY_DEFAULT, R.string.menu_voice_frequency)
            .show(getSupportFragmentManager(), FrequencyDialogFragment.FREQUENCY_DIALOG_TAG);
        return true;
      case R.id.track_detail_split_frequency:
        FrequencyDialogFragment.newInstance(R.string.split_frequency_key,
            PreferencesUtils.SPLIT_FREQUENCY_DEFAULT, R.string.menu_split_frequency)
            .show(getSupportFragmentManager(), FrequencyDialogFragment.FREQUENCY_DIALOG_TAG);
        return true;
      case R.id.track_detail_export:
        AnalyticsUtils.sendPageViews(this, "/export");
        new ExportDialogFragment().show(getSupportFragmentManager(),
            ExportDialogFragment.EXPORT_DIALOG_TAG);
        return true;
      case R.id.track_detail_edit:
        intent = IntentUtils.newIntent(this, TrackEditActivity.class)
            .putExtra(TrackEditActivity.EXTRA_TRACK_ID, trackId);
        startActivity(intent);
        return true;
      case R.id.track_detail_delete:
        DeleteTrackDialogFragment.newInstance(false, new long[] { trackId })
            .show(getSupportFragmentManager(), DeleteTrackDialogFragment.DELETE_TRACK_DIALOG_TAG);
        return true;
      case R.id.track_detail_sensor_state:
        intent = IntentUtils.newIntent(this, SensorStateActivity.class);
        startActivity(intent);
        return true;
      case R.id.track_detail_settings:
        intent = IntentUtils.newIntent(this, SettingsActivity.class);
        startActivity(intent);
        return true;
      case R.id.track_detail_feedback:
        GoogleFeedbackUtils.bindFeedback(this);
        return true;
      case R.id.track_detail_help:
        intent = IntentUtils.newIntent(this, HelpActivity.class);
        startActivity(intent);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      if (trackId == recordingTrackId && !recordingTrackPaused) {
        TrackRecordingServiceConnectionUtils.addMarker(
            this, trackRecordingServiceConnection, WaypointCreationRequest.DEFAULT_WAYPOINT);
        return true;
      }
    }
    return super.onTrackballEvent(event);
  }

  @Override
  public void onExportDone(ExportType exportType, TrackFileFormat trackFileFormat) {
    String pageView;
    if (exportType == ExportType.EXTERNAL_STORAGE) {
      pageView = "/export/external_storage_" + trackFileFormat.name().toLowerCase(Locale.US);
      AnalyticsUtils.sendPageViews(this, pageView);
      Intent intent = IntentUtils.newIntent(this, SaveActivity.class)
          .putExtra(SaveActivity.EXTRA_TRACK_IDS, new long[] { trackId })
          .putExtra(SaveActivity.EXTRA_TRACK_FILE_FORMAT, (Parcelable) trackFileFormat);
      startActivity(intent);
    } else {
      SendRequest sendRequest = new SendRequest(trackId);
      switch (exportType) {
        case GOOGLE_MAPS:
          pageView = "/export/maps";
          sendRequest.setSendMaps(true);
          break;
        case GOOGLE_FUSION_TABLES:
          pageView = "/export/fusion_tables";
          sendRequest.setSendFusionTables(true);
          break;
        default:
          pageView = "/export/spreadsheets";
          sendRequest.setSendSpreadsheets(true);
      }
      AnalyticsUtils.sendPageViews(this, pageView);
      sendToGoogle(sendRequest);
    }
  }

  @Override
  public TrackRecordingServiceConnection getTrackRecordingServiceConnection() {
    return trackRecordingServiceConnection;
  }

  @Override
  public void onDeleteTrackDone() {
    runOnUiThread(new Runnable() {
        @Override
      public void run() {
        finish();
      }
    });
  }

  /**
   * Gets the {@link TrackDataHub}.
   */
  public TrackDataHub getTrackDataHub() {
    return trackDataHub;
  }

  /**
   * Gets the track id.
   */
  public long getTrackId() {
    return trackId;
  }
  
  /**
   * Gets the marker id.
   */
  public long getMarkerId() {
    return markerId;
  }
  
  /**
   * Handles the data in the intent.
   */
  private void handleIntent(Intent intent) {
    trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1L);
    markerId = intent.getLongExtra(EXTRA_MARKER_ID, -1L);
    if (markerId != -1L) {
      // Use the trackId from the marker
      Waypoint waypoint = myTracksProviderUtils.getWaypoint(markerId);
      if (waypoint == null) {
        finish();
        return;
      }
      trackId = waypoint.getTrackId();
    }
    if (trackId == -1L) {
      finish();
      return;
    }
    Track track = myTracksProviderUtils.getTrack(trackId);
    if (track == null) {
      // Use the last track if markerId is not set
      if (markerId == -1L) {
        track = myTracksProviderUtils.getLastTrack();
        if (track != null) {
          trackId = track.getId();
          return;
        }
      }
      finish();
      return;
    }
  }

  /**
   * Updates the menu items.
   * 
   * @param isRecording true if recording
   */
  private void updateMenuItems(boolean isRecording, boolean isPaused) {
    if (insertMarkerMenuItem != null) {
      insertMarkerMenuItem.setVisible(isRecording && !isPaused);
    }
    if (playMenuItem != null) {
      playMenuItem.setVisible(!isRecording);
    }
    if (shareMenuItem != null && shareMenuItem.isEnabled()) {
      shareMenuItem.setVisible(!isRecording);
    }
    if (exportMenuItem != null) {
      exportMenuItem.setVisible(!isRecording);
    }
    if (voiceFrequencyMenuItem != null) {
      voiceFrequencyMenuItem.setVisible(isRecording);
    }
    if (splitFrequencyMenuItem != null) {
      splitFrequencyMenuItem.setVisible(isRecording);
    }

    String title;
    if (isRecording) {
      title = getString(isPaused ? R.string.generic_paused : R.string.generic_recording);
    } else {
      Track track = myTracksProviderUtils.getTrack(trackId);
      title = track != null ? track.getName() : "";
    }
    setTitle(title);
  }
}