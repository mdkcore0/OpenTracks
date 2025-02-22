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

package de.dennisguse.opentracks.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.TrackRecordedActivity;
import de.dennisguse.opentracks.adapters.SensorsAdapter;
import de.dennisguse.opentracks.content.data.Track;
import de.dennisguse.opentracks.content.provider.ContentProviderUtils;
import de.dennisguse.opentracks.databinding.StatisticsRecordedBinding;
import de.dennisguse.opentracks.stats.SensorStatistics;
import de.dennisguse.opentracks.stats.TrackStatistics;
import de.dennisguse.opentracks.util.PreferencesUtils;
import de.dennisguse.opentracks.util.StringUtils;
import de.dennisguse.opentracks.util.TrackIconUtils;
import de.dennisguse.opentracks.viewmodels.SensorDataModel;

/**
 * A fragment to display track statistics to the user for a recorded {@link Track}.
 *
 * @author Sandor Dornbush
 * @author Rodrigo Damazio
 */
public class StatisticsRecordedFragment extends Fragment {

    private static final String TRACK_ID_KEY = "trackId";

    public static StatisticsRecordedFragment newInstance(Track.Id trackId) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(TRACK_ID_KEY, trackId);

        StatisticsRecordedFragment fragment = new StatisticsRecordedFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    private SensorStatistics sensorStatistics;
    private SensorsAdapter sensorsAdapter;

    private Track.Id trackId;
    @Nullable // Lazily loaded.
    private Track track;

    private ContentProviderUtils contentProviderUtils;

    private StatisticsRecordedBinding viewBinding;

    private SharedPreferences sharedPreferences;
    private boolean preferenceMetricUnits;
    private boolean preferenceReportSpeed;

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener = (sharedPreferences, key) -> {
        boolean updateUInecessary = false;

        if (PreferencesUtils.isKey(getContext(), R.string.stats_units_key, key)) {
            updateUInecessary = true;
            preferenceMetricUnits = PreferencesUtils.isMetricUnits(sharedPreferences, getContext());
        }

        if (PreferencesUtils.isKey(getContext(), R.string.stats_rate_key, key) && track != null) {
            updateUInecessary = true;
            preferenceReportSpeed = PreferencesUtils.isReportSpeed(sharedPreferences, getContext(), track.getCategory());
        }

        if (key != null && updateUInecessary && isResumed()) {
            getActivity().runOnUiThread(() -> {
                if (isResumed()) {
                    updateUI();
                }
            });
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        trackId = getArguments().getParcelable(TRACK_ID_KEY);
        contentProviderUtils = new ContentProviderUtils(getContext());

        sharedPreferences = PreferencesUtils.getSharedPreferences(getContext());

        sensorsAdapter = new SensorsAdapter(getContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewBinding = StatisticsRecordedBinding.inflate(inflater, container, false);

        RecyclerView sensorsRecyclerView = viewBinding.statsSensorsRecyclerView;
        sensorsRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        sensorsRecyclerView.setAdapter(sensorsAdapter);

        return viewBinding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();

        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        sharedPreferenceChangeListener.onSharedPreferenceChanged(sharedPreferences, null);

        loadStatistics();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sharedPreferences = null;
    }

    public void loadStatistics() {
        if (isResumed()) {
            getActivity().runOnUiThread(() -> {
                if (isResumed()) {
                    Track track = contentProviderUtils.getTrack(trackId);
                    sensorStatistics = contentProviderUtils.getSensorStats(trackId);

                    if ((this.track == null && track != null) || (this.track != null && track != null && !this.track.getCategory().equals(track.getCategory()))) {
                        sharedPreferenceChangeListener.onSharedPreferenceChanged(sharedPreferences, getString(R.string.stats_rate_key));
                    }

                    this.track = track;

                    loadTrackDescription(track);
                    updateUI();
                    updateSensorUI();

                    ((TrackRecordedActivity) getActivity()).startPostponedEnterTransitionWith(viewBinding.statsActivityTypeIcon, viewBinding.statsNameValue);
                }
            });
        }
    }

    private void loadTrackDescription(@NonNull Track track) {
        viewBinding.statsNameValue.setText(track.getName());
        viewBinding.statsDescriptionValue.setText(track.getDescription());
        viewBinding.statsStartDatetimeValue.setText(StringUtils.formatDateTime(getContext(), track.getTrackStatistics().getStartTime()));
    }

    private void updateUI() {
        TrackStatistics trackStatistics = track.getTrackStatistics();
        // Set total distance
        {
            Pair<String, String> parts = StringUtils.getDistanceParts(getContext(), trackStatistics.getTotalDistance(), preferenceMetricUnits);

            viewBinding.statsDistanceValue.setText(parts.first);
            viewBinding.statsDistanceUnit.setText(parts.second);
        }

        // Set activity type
        {
            String trackIconValue = TrackIconUtils.getIconValue(getContext(), track.getCategory());
            viewBinding.statsActivityTypeIcon.setImageDrawable(ContextCompat.getDrawable(getContext(), TrackIconUtils.getIconDrawable(trackIconValue)));
        }

        // Set time and start datetime
        {
            viewBinding.statsMovingTimeValue.setText(StringUtils.formatElapsedTime(trackStatistics.getMovingTime()));
            viewBinding.statsTotalTimeValue.setText(StringUtils.formatElapsedTime(trackStatistics.getTotalTime()));
        }

        // Set average speed/pace
        {
            viewBinding.statsAverageSpeedLabel.setText(preferenceReportSpeed ? R.string.stats_average_speed : R.string.stats_average_pace);

            Pair<String, String> parts = StringUtils.getSpeedParts(getContext(), trackStatistics.getAverageSpeed(), preferenceMetricUnits, preferenceReportSpeed);
            viewBinding.statsAverageSpeedValue.setText(parts.first);
            viewBinding.statsAverageSpeedUnit.setText(parts.second);
        }

        // Set max speed/pace
        {
            viewBinding.statsMaxSpeedLabel.setText(preferenceReportSpeed ? R.string.stats_max_speed : R.string.stats_fastest_pace);

            Pair<String, String> parts = StringUtils.getSpeedParts(getContext(), trackStatistics.getMaxSpeed(), preferenceMetricUnits, preferenceReportSpeed);
            viewBinding.statsMaxSpeedValue.setText(parts.first);
            viewBinding.statsMaxSpeedUnit.setText(parts.second);
        }

        // Set moving speed/pace
        {
            viewBinding.statsMovingSpeedLabel.setText(preferenceReportSpeed ? R.string.stats_average_moving_speed : R.string.stats_average_moving_pace);

            Pair<String, String> parts = StringUtils.getSpeedParts(getContext(), trackStatistics.getAverageMovingSpeed(), preferenceMetricUnits, preferenceReportSpeed);
            viewBinding.statsMovingSpeedValue.setText(parts.first);
            viewBinding.statsMovingSpeedUnit.setText(parts.second);
        }

        // Set altitude gain and loss
        {
            // Make altitude visible?
            boolean show = PreferencesUtils.isShowStatsAltitude(sharedPreferences, getContext());
            viewBinding.statsAltitudeGroup.setVisibility(show ? View.VISIBLE : View.GONE);

            Float altitudeGain_m = trackStatistics.getTotalAltitudeGain();
            Float altitudeLoss_m = trackStatistics.getTotalAltitudeLoss();

            Pair<String, String> parts;

            parts = StringUtils.formatAltitude(getContext(), altitudeGain_m, preferenceMetricUnits);
            viewBinding.statsAltitudeGainValue.setText(parts.first);
            viewBinding.statsAltitudeGainUnit.setText(parts.second);

            parts = StringUtils.formatAltitude(getContext(), altitudeLoss_m, preferenceMetricUnits);
            viewBinding.statsAltitudeLossValue.setText(parts.first);
            viewBinding.statsAltitudeLossUnit.setText(parts.second);
        }
    }

    private void updateSensorUI() {
        if (sensorStatistics == null) {
            return;
        }

        List<SensorDataModel> sensorDataList = new ArrayList<>();
        if (sensorStatistics.hasHeartRate()) {
            sensorDataList.add(new SensorDataModel(R.string.sensor_state_heart_rate_max, R.string.sensor_unit_beats_per_minute, sensorStatistics.getMaxHeartRate()));
            sensorDataList.add(new SensorDataModel(R.string.sensor_state_heart_rate_avg, R.string.sensor_unit_beats_per_minute, sensorStatistics.getAvgHeartRate()));
        }
        if (sensorStatistics.hasCadence()) {
            sensorDataList.add(new SensorDataModel(R.string.sensor_state_cadence_max, R.string.sensor_unit_rounds_per_minute, sensorStatistics.getMaxCadence()));
            sensorDataList.add(new SensorDataModel(R.string.sensor_state_cadence_avg, R.string.sensor_unit_rounds_per_minute, sensorStatistics.getAvgCadence()));
        }
        if (sensorStatistics.hasPower()) {
            sensorDataList.add(new SensorDataModel(R.string.sensor_state_power_avg, R.string.sensor_unit_power, sensorStatistics.getAvgPower()));
        }
        if (sensorDataList.size() > 0) {
            sensorsAdapter.swapData(sensorDataList);
        }
    }
}
