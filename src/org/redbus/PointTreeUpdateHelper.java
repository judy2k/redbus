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

package org.redbus;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.protocol.HTTP;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public class PointTreeUpdateHelper {
	
	private static Integer RequestId = new Integer(0);
	private static final Pattern busUpdateRegex = Pattern.compile("bus2.dat-([0-9]+).gz");

	public static int checkForUpdates(Context ctx)
	{
		int requestId = RequestId++;

		new AsyncUpdateTask().execute(new UpdateRequest(requestId, UpdateRequest.REQ_CHECKUPDATES, ctx));

		return requestId;
	}

	public static int getUpdate(long updateDate)
	{
		int requestId = RequestId++;

		new AsyncUpdateTask().execute(new UpdateRequest(requestId, UpdateRequest.REQ_GETUPDATE, updateDate));

		return requestId;
	}

	private static class AsyncUpdateTask extends AsyncTask<UpdateRequest, Integer, UpdateRequest> {
		
		protected UpdateRequest doInBackground(UpdateRequest... params) {
			UpdateRequest ur = params[0];
			ur.updateDate = -1;
			ur.updateLength = -1;
			ur.updateData = null;

			try {
				switch(ur.requestType) {
				case UpdateRequest.REQ_CHECKUPDATES:
					checkUpdates(ur);
					break;

				case UpdateRequest.REQ_GETUPDATE:
					getUpdate(ur);
					break;
				}				
			} catch (Throwable t) {
				Log.e("PointTreeUpdateHelperRequestTask.doInBackGround", "Throwable", t);
			}

			
			return ur;
		}
		
		private void checkUpdates(UpdateRequest ur) throws MalformedURLException {			
			// download the HTML for the project downloads page
			String downloadsHtml = doGetStringUrl(new URL("http://code.google.com/p/redbus/downloads/list"));
			if (downloadsHtml == null)
				return;

			// get the date of the previous update
			long lastUpdateDate = -1;
			LocalDBHelper db = new LocalDBHelper(ur.ctx);
			try {
				lastUpdateDate = Long.parseLong(db.getGlobalSetting("lastupdate", "-1"));
			} finally {
				db.close();
			}

			// find the latest update
			long latestUpdateDate = -1;
			Matcher matcher= busUpdateRegex.matcher(downloadsHtml);
			while(matcher.find()) {
				long curUpdateDate = Long.parseLong(matcher.group(1));
				if (curUpdateDate > latestUpdateDate)
					latestUpdateDate = curUpdateDate;
			}

			// no update newer than the one we have - just return
			if (latestUpdateDate <= lastUpdateDate) 
				return;
			ur.updateDate = latestUpdateDate;

			// Now, get the size of the update
			ur.updateLength = doGetContentLength(new URL("http://redbus.googlecode.com/files/bus2.dat-" + latestUpdateDate + ".gz"));
		}

		private void getUpdate(UpdateRequest ur) throws MalformedURLException {
			ur.updateData = doGetBinaryUrl(new URL("http://redbus.googlecode.com/files/bus2.dat-" + ur.updateDate + ".gz"));
		}

		private String doGetStringUrl(URL url) {
			for(int retries = 0; retries < 2; retries++) {
				HttpURLConnection connection = null;
				InputStreamReader reader = null;
				try {
					// make the request and check the response code
					connection = (HttpURLConnection) url.openConnection();
					connection.setReadTimeout(30 * 1000);
					connection.setConnectTimeout(30 * 1000);
					if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
						Log.e("PointTreeUpdateHelperRequestTask.doGetUrl", "HttpError: " + connection.getResponseMessage());
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
					return result.toString();
				} catch (Throwable t) {
					Log.e("PointTreeUpdateHelperRequestTask.doGetUrl", "Throwable", t);
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
			
			return null;
		}
		
		private byte[] doGetBinaryUrl(URL url) {
			for(int retries = 0; retries < 2; retries++) {
				HttpURLConnection connection = null;
				InputStreamReader reader = null;
				try {
					// make the request and check the response code
					connection = (HttpURLConnection) url.openConnection();
					connection.setReadTimeout(30 * 1000);
					connection.setConnectTimeout(30 * 1000);
					if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
						Log.e("PointTreeUpdateHelperRequestTask.doGetUrl", "HttpError: " + connection.getResponseMessage());
						continue;
					}
					
					InputStream is = connection.getInputStream();					
					byte[] result = new byte[connection.getContentLength()];
					int pos = 0;
					while(true) {
						int len = is.read(result, pos, 1024);
						if (len < 0)
							break;
						pos += len;
					}
					return result;
				} catch (Throwable t) {
					Log.e("PointTreeUpdateHelperRequestTask.doGetUrl", "Throwable", t);
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
			
			return null;
		}
		
		private int doGetContentLength(URL url) {
			for(int retries = 0; retries < 2; retries++) {
				HttpURLConnection connection = null;
				try {
					// make the request and check the response code
					connection = (HttpURLConnection) url.openConnection();
					connection.setRequestMethod("HEAD");
					connection.setReadTimeout(30 * 1000);
					connection.setConnectTimeout(30 * 1000);
					if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
						Log.e("PointTreeUpdateHelperRequestTask.doHeadUrl", "HttpError: " + connection.getResponseMessage());
						continue;
					}
					
					return connection.getContentLength();
				} catch (Throwable t) {
					Log.e("PointTreeUpdateHelperRequestTask.doHeadUrl", "Throwable", t);
				} finally {
					try {
						if (connection != null)
							connection.disconnect();
					} catch (Throwable t) {
					}
				}
			}
			
			return -1;
		}

		protected void onPostExecute(UpdateRequest request) {
			/*
			if ((request.addresses == null) || (request.addresses.size() == 0)) {
				request.callback.geocodeResponseError(request.requestId, "Could not find address...");
			} else {
				request.callback.geocodeResponseSucccess(request.requestId, request.addresses);
			}
			*/
		}
	}
	
	private static class UpdateRequest {
		
		public static final int REQ_CHECKUPDATES = 0;
		public static final int REQ_GETUPDATE = 1;
		
		public UpdateRequest(int requestId, int requestType, Context ctx)
		{
			this.requestId = requestId;
			this.requestType = requestType;
			this.ctx = ctx;
		}
		
		public UpdateRequest(int requestId, int requestType, long updateDate)
		{
			this.requestId = requestId;
			this.requestType = requestType;
			this.updateDate = updateDate;
		}
		
		public int requestId;
		public int requestType;
		public Context ctx;
		public long updateDate;
		public int updateLength;
		public byte[] updateData;
	}
}
