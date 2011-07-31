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

package org.redbus.stopdb;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.protocol.HTTP;


import android.os.AsyncTask;
import android.util.Log;

public class StopDbUpdateHelper {
	
	private static final Pattern busUpdateRegex = Pattern.compile("bus2.dat-([0-9]+).gz");

	private static Integer RequestId = new Integer(0);

	public static int checkForUpdates(long lastUpdateDate, IStopDbUpdateResponseListener callback)
	{
		int requestId = RequestId++;

		new AsyncUpdateTask().execute(new UpdateRequest(requestId, UpdateRequest.REQ_CHECKUPDATE, lastUpdateDate, -1, callback));
		
		return requestId;
	}

	public static int getUpdate(long updateDate, IStopDbUpdateResponseListener callback)
	{
		int requestId = RequestId++;

		new AsyncUpdateTask().execute(new UpdateRequest(requestId, UpdateRequest.REQ_GETUPDATE, -1, updateDate, callback));
		
		return requestId;
	}

	private static class AsyncUpdateTask extends AsyncTask<UpdateRequest, Integer, UpdateRequest> {
		
		protected UpdateRequest doInBackground(UpdateRequest... params) {
			UpdateRequest ur = params[0];

			try {
				switch(ur.requestType) {
				case UpdateRequest.REQ_CHECKUPDATE:
					ur.updateDate = -1;
					checkUpdate(ur);
					break;

				case UpdateRequest.REQ_GETUPDATE:
					ur.updateData = null;
					getUpdate(ur);
					break;
				}				
			} catch (Throwable t) {
				Log.e("PointTreeUpdateHelperRequestTask.doInBackGround", "Throwable", t);
			}

			
			return ur;
		}
		
		private void checkUpdate(UpdateRequest ur) throws MalformedURLException {			
			// download the HTML for the project downloads page
			String downloadsHtml = doGetStringUrl(new URL("http://code.google.com/p/redbus/downloads/list"));
			if (downloadsHtml == null)
				return;

			// find the latest update
			long latestUpdateDate = -1;
			Matcher matcher= busUpdateRegex.matcher(downloadsHtml);
			while(matcher.find()) {
				long curUpdateDate = Long.parseLong(matcher.group(1));
				if (curUpdateDate > latestUpdateDate)
					latestUpdateDate = curUpdateDate;
			}

			// no update newer than the one we have - just return
			if (latestUpdateDate <= ur.lastUpdateDate) {
				ur.updateDate = 0;
				return;
			}
			ur.updateDate = latestUpdateDate;
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
					connection.setUseCaches(false);
					if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
						Log.e("PointTreeUpdateHelperRequestTask.doGetUrl", "HttpError: " + connection.getResponseMessage());
						continue;
					}
					
					InputStream is = connection.getInputStream();					
					byte[] result = new byte[connection.getContentLength()];
					int pos = 0;
					while(true) {
						int len = is.read(result, pos, result.length - pos);
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

		protected void onPostExecute(UpdateRequest request) {
			switch(request.requestType) {
			case UpdateRequest.REQ_CHECKUPDATE:
				if (request.updateDate == -1) {
					request.callback.checkUpdateError(request.requestId);
					return;
				}
				
				request.callback.checkUpdateSuccess(request.requestId, request.updateDate);
				break;

			case UpdateRequest.REQ_GETUPDATE:
				if (request.updateData == null) {
					request.callback.getUpdateError(request.requestId);
					return;
				}
				
				request.callback.getUpdateSuccess(request.requestId, request.updateDate, request.updateData);
				break;
			}
		}
	}
	
	public static class UpdateRequest {
		
		public static final int REQ_CHECKUPDATE = 0;
		public static final int REQ_GETUPDATE = 1;
		
		public UpdateRequest(int requestId, int requestType, long lastUpdateDate, long updateDate, IStopDbUpdateResponseListener callback)
		{
			this.requestId = requestId;
			this.requestType = requestType;
			this.lastUpdateDate = lastUpdateDate;
			this.updateDate = updateDate;
			this.callback = callback;
		}
		
		public int requestId;
		public int requestType;
		public long lastUpdateDate;
		public long updateDate;
		public byte[] updateData;
		public IStopDbUpdateResponseListener callback;
	}
}
