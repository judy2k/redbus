/*
 * Copyright 2012 Colin Paton - cozzarp@gmail.com
 * This file is part of rEdBus.
 *
 *  rEdBus is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  rEdBus is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with rEdBus.  If not, see <http://www.gnu.org/licenses/>.
 */


/**
 * Use GPS to find out where the user is standing - look up the nearest stop
 * and then read out the next services from that stop.
 */

package org.redbus.ui;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.redbus.arrivaltime.ArrivalTime;
import org.redbus.arrivaltime.ArrivalTimeHelper;
import org.redbus.arrivaltime.IArrivalTimeResponseListener;
import org.redbus.stopdb.StopDbHelper;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.widget.TextView;

public class SpeakWhereIamActivity extends Activity implements
		TextToSpeech.OnInitListener {
	private TextToSpeech ttsEngine;
	private StopDbHelper pt;
	private TextView display;
	private LocationListener locationListener;

	private static final int TTS_CHECK_RESULT_ID = 0;

	/** Speak a string
	 * 
	 * @param text
	 * @param flush - clear the text buffer
	 * @param closeOnFinish - close the activity when speaking has finished
	 */
	
	private void say(String text, boolean flush, boolean closeOnFinish) {
		display.setText(text);
		HashMap<String, String> hashAlarm = new HashMap<String, String>();

		if (closeOnFinish) {
			// We need this to be notified of end of speech
			hashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "finish");
		}
		
		if (flush)
			ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, hashAlarm);
		else
			ttsEngine.speak(text, TextToSpeech.QUEUE_ADD, hashAlarm);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Fire off an intent to check if a TTS engine is installed
		Intent checkIntent = new Intent();
		checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
		startActivityForResult(checkIntent, TTS_CHECK_RESULT_ID);

		pt = StopDbHelper.Load(this);

		// Define a listener that responds to location updates
		locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				onLocationUpdated(location);
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
				say("Sorry. I cannot find out where you are. Have you turned on GPS?",true,true);
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}
		};

		display = new TextView(this);
		setContentView(display);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();
		((LocationManager)getSystemService(LOCATION_SERVICE)).removeUpdates(locationListener);
	}

	private void onLocationUpdated(Location location) {
		// Only tell us once
		((LocationManager) getSystemService(LOCATION_SERVICE))
				.removeUpdates(locationListener);

		
		// Find the nearest stop
		int x = (int) (location.getLatitude() * 1E6);
		int y = (int) (location.getLongitude() * 1E6);
		int stopNodeIdx = pt.findNearest(x, y);

		// Get name+code for this stop
		String stopName = pt.lookupStopNameByStopNodeIdx(stopNodeIdx);
		int stopCode = pt.stopCode[stopNodeIdx];

		setTitle(stopName+" ("+stopCode+")");
		say("I think you are near " + stopName + ".", true, false);
		
		// Now look up the times
		ArrivalTimeHelper.getBusTimesAsync(stopCode, 0, new Date(),
				new ArrivalTimeListener());
	}

	// Handle arrival time response
	class ArrivalTimeListener implements IArrivalTimeResponseListener {
		public void onAsyncGetBusTimesError(int requestId, int code,
				String message) {
			say("Sorry. Cannot download the times.", false, true);
		}

		// Actually read out the times
		public void onAsyncGetBusTimesSuccess(int requestId,
				List<ArrivalTime> busTimes) {
			String text = "";
			HashSet<String> seenServices = new HashSet<String>();

			for (ArrivalTime arrival : busTimes) {
				if (seenServices.contains(arrival.service) == true)
					continue; // Don't read out services we already know about

				String destination = " to " + arrival.destination;
				String arrivalTime = " is in " + arrival.arrivalMinutesLeft
						+ " minutes";
				if (arrival.isDiverted)
					destination = " is diverted";
				if (arrival.arrivalIsDue)
					arrivalTime = "is due now";
				if (arrival.arrivalAbsoluteTime != null)
					arrivalTime = " will be at " + arrival.arrivalAbsoluteTime
							+ " hours";

				text += "The " + arrival.service + destination + " "
						+ arrivalTime + ".\n";
				seenServices.add(arrival.service);
			}

			say(text, false, true);
		}
	}

	@Override
	public void onDestroy() {
		// We don't want to speak any more.
		if (ttsEngine != null) {
			ttsEngine.stop();
			ttsEngine.shutdown();
		}
		super.onDestroy();
	}

	// Android boilerplate stuff
	private void handleSpeechInstallResult(int resultCode) {
		if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
			// success, create the TTS instance
			ttsEngine = new TextToSpeech(this, this);
		} else {
			// missing data, install it
			Intent installIntent = new Intent();
			installIntent
					.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
			startActivity(installIntent);
		}
	}

	// Called as response to speech engine check - Android boilerplate stuff
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == TTS_CHECK_RESULT_ID) {
			handleSpeechInstallResult(resultCode);
		}
	}

	// called once we have a speech engine - start requesting location updates
	public void onInit(int status) {
		ttsEngine.setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {
			public void onUtteranceCompleted(String utteranceId) {
				if (utteranceId.equals("finish"))
					finish();
			}
		});

		say("Please wait a moment while I look up to the skies to see which stop you are near. I might take a moment or two.",
				true, false);
		
		((LocationManager) getSystemService(LOCATION_SERVICE))
		.requestLocationUpdates(LocationManager.GPS_PROVIDER, 20000, // min
																		// time
				100, // min distance
				locationListener);
	}
}
