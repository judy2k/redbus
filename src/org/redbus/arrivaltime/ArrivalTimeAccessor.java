/*
 * Copyright 2010 Andrew De Quincey -  adq@lidskialf.net
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

package org.redbus.arrivaltime;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.protocol.HTTP;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Xml;

public class ArrivalTimeAccessor {
	
	public static final int BUSSTATUS_HTTPERROR = -1;
	public static final int BUSSTATUS_BADSTOPCODE = -2;
	public static final int BUSSTATUS_BADDATA = -3;

	private static final Pattern destinationRegex = Pattern.compile("(\\S+)\\s+(.*)");
	private static final Pattern destinationAndTimeRegex = Pattern.compile("(\\S+)\\s+(.*)\\s+(\\S+)");
	private static final SimpleDateFormat advanceTimeFormat = new SimpleDateFormat("HH:mm");

	private static Integer RequestId = new Integer(0);

	public static int getBusTimesAsync(long stopCode, int daysInAdvance, Date timeInAdvance, IArrivalTimeResponseListener callback)
	{
		int requestId = RequestId++;
		
		new AsyncHttpRequestTask().execute(new BusDataRequest(requestId, 
				buildURL(stopCode, daysInAdvance, timeInAdvance, 4), 
				BusDataRequest.REQ_BUSTIMES, 
				callback));		
		
		return requestId;
	}
	
	
	
	
	
	private static URL buildURL(long stopCode, int daysInAdvance, Object timeInAdvance, int departureCount)
	{
		String time = "";
		if (timeInAdvance != null) {
			if (timeInAdvance instanceof Date)
				time = advanceTimeFormat.format(timeInAdvance);
			else
				time = timeInAdvance.toString();
		} else {
			daysInAdvance = 0;
		}

		StringBuilder result = new StringBuilder("http://old.mybustracker.co.uk/getBusStopDepartures.php");
		result.append("?refreshCount=0");
		result.append("&clientType=b");
		result.append("&busStopDay=").append(daysInAdvance);
		result.append("&busStopService=0");
		result.append("&numberOfPassage=").append(departureCount);
		result.append("&busStopTime=").append(time);
		result.append("&busStopDestination=0");
		result.append("&busStopCode=").append(stopCode);
		result.append("&randomThing=").append(new Date().getTime());

		try {
			return new URL(result.toString());
		} catch (MalformedURLException e) {
			Log.e("BusDataHelper", "Malformed URL reported: " + result.toString());
		}
		
		return null;
	}
	
	private static void getBusTimesResponse(BusDataRequest request)
	{
		if (request.content == null) {
			request.callback.getBusTimesError(request.requestId, BUSSTATUS_HTTPERROR, "A network error occurred");
			return;
		}
		if (request.content.toLowerCase().contains("doesn't exist")) {
			request.callback.getBusTimesError(request.requestId, BUSSTATUS_BADSTOPCODE, "The BusStop code was invalid");
			return;
		}
		
		HashMap<String, ArrivalTime> wasDiverted = new HashMap<String, ArrivalTime>();
		HashMap<String, ArrivalTime> hasTime = new HashMap<String, ArrivalTime>();
		ArrayList<ArrivalTime> busTimes = new ArrayList<ArrivalTime>();
		try {
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(new StringReader(request.content));
			
			while(parser.next() != XmlPullParser.END_DOCUMENT) {
				switch(parser.getEventType()) {
				case XmlPullParser.START_TAG:
					String tagName = parser.getName();
					if (tagName == "pre") {
						ArrivalTime bt = parseStopTime(parser);
						if (bt.isDiverted) {
							if (wasDiverted.containsKey(bt.service))
								continue;
							wasDiverted.put(bt.service, bt);
						} else {
							hasTime.put(bt.service, bt);
							busTimes.add(bt);
						}
					}
				}
			}
			
			for(ArrivalTime bt: wasDiverted.values())
				if (!hasTime.containsKey(bt.service))
					busTimes.add(bt);
			
		} catch (Throwable t) {
			Log.e("BusDataHelper.GetBusTimesResponse", request.content, t);
			request.callback.getBusTimesError(request.requestId, BUSSTATUS_BADDATA, "Invalid data was received from the bus website");
			return;
		}
		
		request.callback.getBusTimesSuccess(request.requestId, busTimes);
	}
	
	private static ArrivalTime parseStopTime(XmlPullParser parser) 
		throws XmlPullParserException, IOException
	{
		String rawDestination = parser.nextText();
		String rawTime = null;
		
		String service =  null;
		String destination = null;
		boolean isDiverted = false;
		boolean lowFloorBus =  false;
		boolean arrivalEstimated = false;
		boolean arrivalIsDue= false;
		int arrivalMinutesLeft = 0;
		String arrivalAbsoluteTime = null;
		
		boolean done = false;
		int tagDepth = 0;
		while(!done) {
			switch(parser.next()) {
			case XmlPullParser.END_TAG:
				if (tagDepth == 0)
					done = true;
				tagDepth--;
				break;
			case XmlPullParser.START_TAG:
				tagDepth++;
				if (parser.getName().equalsIgnoreCase("span")) {
					String classAttr = parser.getAttributeValue(null, "class");
					if (classAttr.equalsIgnoreCase("handicap"))
						lowFloorBus = true;
				}
				break;
			case XmlPullParser.TEXT:
				if (tagDepth == 0)
					rawTime = parser.getText().trim();
				break;
			}
		}
		
		if (rawDestination.toLowerCase().contains("diverted")) {
			isDiverted = true;
			Matcher m = destinationRegex.matcher(rawDestination.trim());
			if (m.matches()) {
				service = m.group(1).trim();
				destination = null;
			} else {
				throw new RuntimeException("Failed to parse destination");
			}
		} else {
			// parse the rawDestination
			if (rawTime == null) {
				Matcher m = destinationAndTimeRegex.matcher(rawDestination.trim());
				if (m.matches()) {
					service = m.group(1).trim();
					destination = m.group(2).trim();
					rawTime = m.group(3).trim();
				} else {
					throw new RuntimeException("Failed to parse rawTime");
				}
			} else {
				Matcher m = destinationRegex.matcher(rawDestination.trim());
				if (m.matches()) {
					service = m.group(1).trim();
					destination = m.group(2).trim();
				} else {
					throw new RuntimeException("Failed to parse destination");
				}
			}
	
			// parse the rawTime
			if (rawTime.startsWith("*")) {
				arrivalEstimated = true;
				rawTime = rawTime.substring(1).trim();
			}
			if (rawTime.equalsIgnoreCase("due"))
				arrivalIsDue = true;
			else if (rawTime.contains(":"))
				arrivalAbsoluteTime = rawTime;
			else
				arrivalMinutesLeft = Integer.parseInt(rawTime);
		}

		return new ArrivalTime(service, destination, isDiverted, lowFloorBus, arrivalEstimated, arrivalIsDue, arrivalMinutesLeft, arrivalAbsoluteTime);
	}
	
	private static class AsyncHttpRequestTask extends AsyncTask<BusDataRequest, Integer, BusDataRequest> {
		
		protected BusDataRequest doInBackground(BusDataRequest... params) {
			BusDataRequest bdr = params[0];
			bdr.content = null;

			for(int retries = 0; retries < 2; retries++) {
				HttpURLConnection connection = null;
				InputStreamReader reader = null;
				try {
					// make the request and check the response code
					connection = (HttpURLConnection) bdr.url.openConnection();
					connection.setReadTimeout(30 * 1000);
					connection.setConnectTimeout(30 * 1000);
					if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
						Log.e("AsyncHttpRequestTask.doInBackGround", "HttpError: " + connection.getResponseMessage());
						continue;
					}
					
					// figure out the content encoding
					String charset = connection.getContentEncoding();
					if (charset == null)
						charset = HTTP.DEFAULT_CONTENT_CHARSET;
					
					// read the request data
					reader = new InputStreamReader(connection.getInputStream(), charset);
					StringBuilder result = new StringBuilder();
					char[] buf= new char[1024];
					while(true) {
						int len = reader.read(buf);
						if (len < 0)
							break;
						result.append(buf, 0, len);
					}
					bdr.content = result.toString();
					break;
				} catch (Throwable t) {
					Log.e("AsyncHttpRequestTask.doInBackGround", "Throwable", t);
				} finally {
					try {
						if (reader != null)
							reader.close();
					} catch (Throwable t) {
					}
					try {
						if (connection != null)
							connection.disconnect();
					} catch (Throwable t) {
					}
				}
			}
			
			return bdr;
		}

		protected void onPostExecute(BusDataRequest request) {
			switch(request.requestType) {
			case BusDataRequest.REQ_BUSTIMES:
				ArrivalTimeAccessor.getBusTimesResponse(request);			
				break;
			}
		}
	}
	
	private static class BusDataRequest {
		
		public static final int REQ_BUSTIMES = 0;
		
		public BusDataRequest(int requestId, URL url, int requestType, IArrivalTimeResponseListener callback)
		{
			this.requestId = requestId;
			this.url = url;
			this.requestType = requestType;
			this.callback = callback;
		}
		
		public int requestId;
		public URL url;
		public int requestType;
		public IArrivalTimeResponseListener callback;
		public String content = null;
	}
}
