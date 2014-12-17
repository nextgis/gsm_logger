/******************************************************************************
 * Project: NextGIS Logger
 * Purpose: Productive data logger for Android
 * Authors: Stanislav Petriakov
 ******************************************************************************
 * Copyright © 2014 NextGIS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package com.nextgis.logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import com.nextgis.logger.R;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.widget.Toast;

public class PreferencesActivity extends PreferenceActivity {
	public static final int minPeriodSec = 1;
	public static final int maxPeriodSec = 3600;

	@Override
	protected void onCreate(Bundle savedInstance) {
		super.onCreate(savedInstance);
		getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferencesFragment()).commit();
	}

	public static class PreferencesFragment extends PreferenceFragment {
		@SuppressWarnings("deprecation")
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			addPreferencesFromResource(R.xml.preferences);

			final Activity parent = getActivity();

			findPreference(C.PREF_SENSOR_MODE).setSummary(getString(R.string.settings_sensor_mode_sum) + "\r\n" + getString(R.string.settings_sensor_sum));
			
			SensorManager sm = (SensorManager) parent.getSystemService(Context.SENSOR_SERVICE);

			if (sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) == null)
				findPreference(C.PREF_SENSOR_MODE).setEnabled(false);

			if (sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null)
				findPreference(C.PREF_SENSOR_MAG).setEnabled(false);

			if (sm.getDefaultSensor(Sensor.TYPE_ORIENTATION) == null)
				findPreference(C.PREF_SENSOR_ORIENT).setEnabled(false);

			if (sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) == null)
				findPreference(C.PREF_SENSOR_GYRO).setEnabled(false);

			if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
				findPreference(C.PREF_USE_API17).setEnabled(false);

			EditTextPreference userName = (EditTextPreference) findPreference(C.PREF_USER_NAME);
			userName.setSummary(userName.getText());
			userName.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					preference.setSummary((String) newValue);

					return true;
				}
			});

			IntEditTextPreference periodPreference = (IntEditTextPreference) findPreference(C.PREF_PERIOD_SEC);
			periodPreference.setSummary(getString(R.string.settings_period_sum) + periodPreference.getPersistedString("1"));
			periodPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					int period;

					try {
						period = Integer.parseInt((String) newValue);
						boolean max = period > maxPeriodSec;
						boolean min = period < minPeriodSec;

						if (max)
							period = maxPeriodSec;

						if (min)
							period = minPeriodSec;

						((IntEditTextPreference) preference).persistString(Integer.toString(period));
						preference.setSummary(getString(R.string.settings_period_sum) + period);

						if (min || max)
							throw new IllegalArgumentException();
					} catch (Exception e) {
						Toast.makeText(parent, R.string.settings_period_toast, Toast.LENGTH_LONG).show();
					}

					return false;
				}
			});

			Preference catPathPreference = findPreference(C.PREF_CAT_PATH);

			catPathPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(final Preference preference) {
					SimpleFileChooser FileOpenDialog = new SimpleFileChooser();
                    FileOpenDialog.setOnFileChosen(new SimpleFileChooser.SimpleFileChooserListener() {
                        @Override
                        public void onFileChosen(File file) {
                            // The code in this function will be executed when the dialog OK button is pushed
                            String info = getString(R.string.error_no_file);

//                            if (new File(chosenDir).isFile()) {
                            if (file.isFile()) {
                                File fromCats = file;//new File(chosenDir);

                                String internalPath = parent.getFilesDir().getAbsolutePath();
                                File toCats = new File(internalPath + "/" + C.categoriesFile);

                                try {
                                    PrintWriter pw = new PrintWriter(new FileOutputStream(toCats, false));
                                    BufferedReader in = new BufferedReader(new FileReader(fromCats));

                                    String[] split;
                                    String line;

                                    while ((line = in.readLine()) != null) {
                                        split = line.split(",");

                                        if (split.length != 2) {
                                            in.close();
                                            pw.close();
                                            throw new ArrayIndexOutOfBoundsException("Must be two columns splitted by ','!");
                                        } else
                                            pw.println(line);
                                    }

                                    in.close();
                                    pw.close();

                                    info = getString(R.string.file_loaded) + file.getAbsolutePath();
                                } catch (IOException e) {
                                    info = getString(R.string.fs_error_msg);
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    info = getString(R.string.cat_file_structure_error);
                                }
                            }

                            Toast.makeText(parent, info, Toast.LENGTH_SHORT).show();
                        }
                    });

					FileOpenDialog.show(getFragmentManager(), "FileOpenDialog");

					return true;
				}
			});
		}
	}
}
