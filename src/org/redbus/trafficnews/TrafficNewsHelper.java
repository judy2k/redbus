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

package org.redbus.trafficnews;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

import org.apache.http.protocol.HTTP;
import org.xmlpull.v1.XmlPullParser;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Xml;

public class TrafficNewsHelper {
	
	public static final int BUSSTATUS_HTTPERROR = -1;
	public static final int BUSSTATUS_BADSTOPCODE = -2;
	public static final int BUSSTATUS_BADDATA = -3;

	private static Integer RequestId = new Integer(0);

	public static int getTrafficNewsAsync(String lastTweetId, int count, ITrafficNewsResponseListener callback)
	{
		int requestId = RequestId++;
		new AsyncHttpRequestTask().execute(new TrafficNewsRequest(requestId, buildURL(lastTweetId, count), TrafficNewsRequest.REQ_TRAFFICNEWS, callback));
		return requestId;
	}
	
	
	
	
	
	private static URL buildURL(String lastTweetId, int count)
	{
		try {
			String url = "http://twitter.com/statuses/user_timeline.rss?user_id=141165868&count=" + count;
			if (lastTweetId != null)
				url += "&since_id=" + lastTweetId;				
			return new URL(url);
		} catch (MalformedURLException e) {
			Log.e("BusDataHelper", "Malformed URL reported");
		}
		
		return null;
	}
	
	private static void getTrafficNewsResponse(TrafficNewsRequest request)
	{
		if (request.notModified) {
			try {
				request.callback.onAsyncGetTrafficNewsSuccess(request.requestId, null);
			} catch (Throwable t) {
			}
		}
		
		if (request.content == null) {
			try {
				request.callback.onAsyncGetTrafficNewsError(request.requestId, BUSSTATUS_HTTPERROR, "A network error occurred");
			} catch (Throwable t) {
			}
			return;
		}

		ArrayList<NewsItem> newsItems = new ArrayList<NewsItem>();
		try {
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(new StringReader(request.content));
			
			StringBuilder titleSb = null;
			StringBuilder dateSb = null;
			StringBuilder guidSb = null;
			boolean inTitle = false;
			boolean inDate = false;
			boolean inGuid = false;
			while(parser.next() != XmlPullParser.END_DOCUMENT) {
				String tagName = parser.getName();
				
				switch(parser.getEventType()) {
				case XmlPullParser.START_TAG:
					if (tagName == "item") {
					} else if (tagName == "title") {
						titleSb = new StringBuilder();
						inTitle = true;
					} else if (tagName == "pubDate") {
						dateSb = new StringBuilder();
						inDate = true;
					} else if (tagName == "guid") {
						guidSb = new StringBuilder();
						inGuid = true;
					}
					break;
					
				case XmlPullParser.TEXT:
					if (inTitle)
						titleSb.append(" " + parser.getText().replaceAll("\\s+", " ").trim());
					else if (inDate)
						dateSb.append(" " + parser.getText().replaceAll("\\s+", " ").trim());
					else if (inGuid)
						guidSb.append(parser.getText());
					break;
					
				case XmlPullParser.END_TAG:
					if (tagName == "item") {
						try {
							Date date = new Date(Date.parse(dateSb.toString().trim()));
							String title = titleSb.toString().trim();
							String guid = guidSb.toString().trim();
							
							newsItems.add(new NewsItem(guid, date, title));
						} catch (Throwable t) {
						}
					} else if (tagName == "title") {
						inTitle = false;
					} else if (tagName == "pubDate") {
						inDate = false;
					} else if (tagName == "guid") {
						inGuid = false;
					}
				}
			}
		
		} catch (Throwable t) {
			Log.e("TrafficNewsHelper.GetTrafficNewsResponse", request.content, t);
			try {
				request.callback.onAsyncGetTrafficNewsError(request.requestId, BUSSTATUS_BADDATA, "Invalid data was received from the bus website");
			} catch (Throwable t2) {
			}
			return;
		}
		
		try {
			request.callback.onAsyncGetTrafficNewsSuccess(request.requestId, newsItems);
		} catch (Throwable t) {
		}
	}
	
	private static class AsyncHttpRequestTask extends AsyncTask<TrafficNewsRequest, Integer, TrafficNewsRequest> {
		
		protected TrafficNewsRequest doInBackground(TrafficNewsRequest... params) {
			TrafficNewsRequest bdr = params[0];
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

		protected void onPostExecute(TrafficNewsRequest request) {
			switch(request.requestType) {
			case TrafficNewsRequest.REQ_TRAFFICNEWS:
				TrafficNewsHelper.getTrafficNewsResponse(request);			
				break;
			}
		}
	}
	
	private static class TrafficNewsRequest {
		
		public static final int REQ_TRAFFICNEWS = 0;
		
		public TrafficNewsRequest(int requestId, URL url, int requestType, ITrafficNewsResponseListener callback)
		{
			this.requestId = requestId;
			this.url = url;
			this.requestType = requestType;
			this.callback = callback;
		}
		
		public int requestId;
		public URL url;
		public int requestType;
		public ITrafficNewsResponseListener callback;
		public boolean notModified;
		public String content = null;
	}
}
