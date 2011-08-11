/*
 * Copyright 2010, 2011 Andrew De Quincey -  adq@lidskialf.net
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
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.apache.http.protocol.HTTP;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Xml;

public class ArrivalTimeHelper {
	
	public static final int BUSSTATUS_HTTPERROR = -1;
	public static final int BUSSTATUS_BADSTOPCODE = -2;
	public static final int BUSSTATUS_BADDATA = -3;

	private static final SimpleDateFormat advanceTimeFormat = new SimpleDateFormat("HH:mm");
	
	private static final String url = "http://www.mybustracker.co.uk/?module=BTTimeConsult&mode=busStopQuickSearch";

	
	

	private static Integer RequestId = new Integer(0);

	public static int getBusTimesAsync(long stopCode, int daysInAdvance, Date timeInAdvance, IArrivalTimeResponseListener callback)
	{
		int requestId = RequestId++;
		
		new AsyncHttpRequestTask().execute(new BusDataRequest(requestId, stopCode, 
				buildPOSTData(stopCode, daysInAdvance, timeInAdvance, 4), 
				BusDataRequest.REQ_BUSTIMES, 
				callback));		
		
		return requestId;
	}
	
	
	
	
	
	private static String buildPOSTData(long stopCode, int daysInAdvance, Object timeInAdvance, int departureCount)
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
		
		StringBuilder sb = new StringBuilder();
		sb.append("googleMapMode=0");
		sb.append("&busStopCode=").append(stopCode);
		sb.append("&busStopDay=").append(daysInAdvance);
		sb.append("&busStopTime=").append(time);
		sb.append("&nbDeparture=").append(departureCount);
		
		return sb.toString();
	}
	
	private static void getBusTimesResponse(BusDataRequest request)
	{
		if (request.content == null) {
			try {
				request.callback.onAsyncGetBusTimesError(request.requestId, BUSSTATUS_HTTPERROR, "A network error occurred");
			} catch (Throwable t) {
			}
			return;
		}
		if (request.content.toLowerCase().contains("doesn't exist")) {
			try {
				request.callback.onAsyncGetBusTimesError(request.requestId, BUSSTATUS_BADSTOPCODE, "The BusStop code was invalid");
			} catch (Throwable t) {
			}
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
					if (tagName == "tr") {
						String classAttr = parser.getAttributeValue(null, "class");
						if (classAttr == null)
							continue;
						classAttr = classAttr.toLowerCase();
						if ((!classAttr.contains("tblanc")) && (!classAttr.contains("tgris")))
							continue;
						
						String styleAttr = parser.getAttributeValue(null, "style");
						if (styleAttr != null) {
							styleAttr = styleAttr.toLowerCase();
							if (styleAttr.contains("display") && styleAttr.contains("none"))
								continue;
						}
						
						ArrivalTime bt = parseStopTime(parser, request.stopCode);
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
			try {
				request.callback.onAsyncGetBusTimesError(request.requestId, BUSSTATUS_BADDATA, "Invalid data was received from the bus website");
			} catch (Throwable t2) {
			}
			return;
		}
		
		try {
			request.callback.onAsyncGetBusTimesSuccess(request.requestId, busTimes);
		} catch (Throwable t) {
		}
	}
	
	private static ArrivalTime parseStopTime(XmlPullParser parser, long stopCode) 
		throws XmlPullParserException, IOException
	{
		boolean grabService = false;
		boolean grabDestination = false;
		boolean grabTime = false;
		boolean grabFlag = false;
		
		String service = null;
		String destination = null;
		String rawTime = null;
		String flag = null;

		boolean isDiverted = false;
		boolean arrivalEstimated = false;
		boolean arrivalIsDue= false;
		int arrivalMinutesLeft = 0;
		String arrivalAbsoluteTime = null;
		
		boolean done = false;
		int exitDepth = parser.getDepth();
		while(!done) {
			switch(parser.next()) {
			case XmlPullParser.START_TAG:
				String classAttr = parser.getAttributeValue(null, "class");
				if (classAttr == null)
					continue;
				classAttr = classAttr.toLowerCase();
				
				if (classAttr.contains("service"))
					grabService = true;
				else if (classAttr.contains("dest"))
					grabDestination = true;
				else if (classAttr.contains("mins"))
					grabTime = true;
				else if (classAttr.contains("flag"))
					grabFlag = true;
				break;
				
			case XmlPullParser.END_TAG:
				if (parser.getDepth() == exitDepth)
					done = true;
				break;
				
			case XmlPullParser.END_DOCUMENT:
				done = true;
				break;
				
			case XmlPullParser.TEXT:
				if (grabService) {
					service = parser.getText().trim();
					grabService = false;
				}
				if (grabDestination) {
					destination = parser.getText().trim();
					grabDestination = false;
				}
				if (grabTime) {
					rawTime = parser.getText().trim();
					grabTime = false;
				}
				if (grabFlag) {
					flag = parser.getText().trim();
					grabFlag = false;
				}
				break;
			}
		}
		
		if (destination.toLowerCase().contains("diverted")) {
			isDiverted = true;
			destination = null;
		} else {
			// parse the rawTime
			if (flag.contains("*"))
				arrivalEstimated = true;
			if (rawTime.equalsIgnoreCase("due"))
				arrivalIsDue = true;
			else if (rawTime.contains(":"))
				arrivalAbsoluteTime = rawTime;
			else
				arrivalMinutesLeft = Integer.parseInt(rawTime);
		}

		return new ArrivalTime(service, stopCode, destination, isDiverted, arrivalEstimated, arrivalIsDue, arrivalMinutesLeft, arrivalAbsoluteTime);
	}
	
	private static class AsyncHttpRequestTask extends AsyncTask<BusDataRequest, Integer, BusDataRequest> {
		
		protected BusDataRequest doInBackground(BusDataRequest... params) {
			BusDataRequest bdr = params[0];
			bdr.content = null;

			for(int retries = 0; retries < 2; retries++) {
				HttpURLConnection connection = null;
				InputStreamReader reader = null;
				try {
					byte[] postDataBytes = bdr.postData.getBytes();

					// make the request and check the response code
					connection = (HttpURLConnection) new URL(url + "&randomThing=" + new Date().getTime()).openConnection();
					connection.setReadTimeout(30 * 1000);
					connection.setConnectTimeout(30 * 1000);
					connection.setRequestMethod("POST");
				    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			        connection.setRequestProperty("Content-Length", "" + postDataBytes.length);
				    connection.setUseCaches(false);
				    connection.setDoInput(true);
				    connection.setDoOutput(true);
					
					OutputStream out = connection.getOutputStream();
					out.write(postDataBytes);
					out.flush();
					out.close();
					
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
				ArrivalTimeHelper.getBusTimesResponse(request);			
				break;
			}
		}
	}
	
	private static class BusDataRequest {
		
		public static final int REQ_BUSTIMES = 0;
		
		public BusDataRequest(int requestId, long stopCode, String postData, int requestType, IArrivalTimeResponseListener callback)
		{
			this.requestId = requestId;
			this.postData = postData;
			this.requestType = requestType;
			this.callback = callback;
			this.stopCode = stopCode;
		}
		
		public int requestId;
		public String postData;
		public int requestType;
		public IArrivalTimeResponseListener callback;
		public String content = null;
		public long stopCode;
	}
}
