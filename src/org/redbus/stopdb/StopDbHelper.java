/*
 * Copyright 2010, 2011 Colin Paton - cozzarp@googlemail.com
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

// KD-Tree implementation

package org.redbus.stopdb;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.redbus.R;
import org.redbus.settings.SettingsHelper;

import android.content.Context;
import android.util.Log;


public class StopDbHelper {

	private static StopDbHelper pointTree = null;
	private static Integer syncObj = new Integer(0);
	private static final String filesPath = "/data/data/org.redbus/files";
	public static final String DatabaseVersion = "bus3";

	public static final int SERVICE_MAP_LONG_COUNT = 2;
	public static final int HEADER_SIZE = 16;
	public static final int TREE_NODE_SIZE = 37;
	
	public static final int STOP_FACING_N = 0x08;
	public static final int STOP_FACING_NE = 0x09;
	public static final int STOP_FACING_E = 0x0a;
	public static final int STOP_FACING_SE = 0x0b;
	public static final int STOP_FACING_S = 0x0c;
	public static final int STOP_FACING_SW = 0x0d;
	public static final int STOP_FACING_W = 0x0e;
	public static final int STOP_FACING_NW = 0x0f;

	public short[] left;
	public short[] right;
	private Map<Integer, Integer> nodeIdxByStopCode;
	public int[] stopCode;
	public int[] lat;
	public int[] lon;
	public long[] serviceMap0;
	public long[] serviceMap1;
	public byte[] facing;
	public int[] stopNameOffset;
	public byte[] rawStopNames;

	public int rootRecordNum;
	final HashMap<Integer, Integer> serviceBitToSortIndex = new HashMap<Integer, Integer>();
	public HashMap<String, Integer> serviceNameToServiceBit = new HashMap<String, Integer>();
	private String[] serviceBitToServiceName;
	private byte[] serviceBitToServiceProviderId;

	public int lowerLeftLat;
	public int lowerLeftLon;
	public int upperRightLat;
	public int upperRightLon;
	
	public int defaultMapLocationLat;
	public int defaultMapLocationLon;

	public static StopDbHelper Load(Context ctx)
	{
		synchronized (syncObj) {
			if (pointTree == null) {
				InputStream stopsStream = null;
				OutputStream outStream = null;
				File dir = new File(filesPath);
				try {
					if (!dir.exists())
						dir.mkdir();
				} catch (Exception ex) {
				}

				File file = new File(dir, DatabaseVersion + ".dat");
				try {
					// first of all, if the file doesn't exist on disk, extract it from our resources and save it out to there
					if (!file.exists()) {
						stopsStream = ctx.getResources().openRawResource(R.raw.bus3);
						outStream = new FileOutputStream(file);
						byte b[] = new byte[4096];
						int len = 0;
						while((len = stopsStream.read(b)) != -1) {
							if (len != 0)
								outStream.write(b, 0, len);
						}
						outStream.close();
						outStream = null;
						stopsStream.close();
						stopsStream = null;
					}

					// now, load the on-disk file
					stopsStream = new FileInputStream(file);
					pointTree = new StopDbHelper(stopsStream, (int) file.length());

				} catch (IOException e) {
					Log.println(Log.ERROR,"redbus","Error reading stops");
					e.printStackTrace();

					// always delete the file if there was an error
					try {
						file.delete();
					} catch (Throwable t) {
					}

					// zap the LASTUPDATE from the db so we redownload it
					SettingsHelper db = new SettingsHelper(ctx);
					try {
						db.deleteGlobalSetting("LASTUPDATE");
					} catch (Throwable t) {
						// ignore
					} finally {
						db.close();
					}


				} finally {
					try {
						if (stopsStream != null)
							stopsStream.close();
					} catch (Throwable t) {
					}
					try {
						if (outStream != null)
							outStream.close();
					} catch (Throwable t) {
					}
				}
				
				// delete old file to save space
				try {
					File oldFile = new File(dir, "bus2.dat");
					if (oldFile.exists())
						oldFile.delete();
				} catch (Throwable t) {
				}
			}

			return pointTree;
		}
	}

	public static void saveRawDb(byte[] gzippedDatabase) throws IOException
	{
		synchronized (syncObj) {
			FileOutputStream outStream = null;
			GZIPInputStream inStream = null;
			File dir = new File(filesPath);
			try {
				if (!dir.exists())
					dir.mkdir();
			} catch (Exception ex) {
			}

			File outFile = new File(dir, DatabaseVersion + ".dat.new");
			File dbFile = new File(dir, DatabaseVersion + ".dat");
			try {
				outStream = new FileOutputStream(outFile);

				// save the data out
				inStream = new GZIPInputStream(new ByteArrayInputStream(gzippedDatabase));
				byte[] buf = new byte[1024];
				int len;
				while((len = inStream.read(buf)) >= 0)
					outStream.write(buf, 0, len);
				outStream.flush();

				// now delete the old database and rename the new file to the old filename
				try {
					dbFile.delete();
				} catch (Throwable t) {
				}
				outFile.renameTo(dbFile);
				pointTree = null;
			} catch (Throwable t) {
				try {
					outFile.delete();
				} catch (Throwable t2) {
				}
				try {
					dbFile.delete();				
				} catch (Throwable t2) {
				}
			} finally {
				try {
					if (outStream != null)
						outStream.close();
				} catch (Throwable t) {
				}
				try {
					if (inStream != null)
						inStream.close();
				} catch (Throwable t) {
				}
			}			
		}
	}

	// Return nodes within a certain rectangle - top-left/bottom-right
	public ArrayList<Integer> findRect(int xtl, int ytl,
			int xbr, int ybr)
	{
		ArrayList<Integer> stops = new ArrayList<Integer>();		
		return searchRect(xtl,ytl,xbr,ybr,rootRecordNum,stops,0);
	}
	
    // Given a location and a list of stop codes return ones
    // within radius. Uses a linear search, but as wanted
    // stops list will be small this doesn't matter.
    public ArrayList<Integer> getStopsWithinRadius(int x, int y,
                                                   ArrayList<Integer> stops,
                                                   double radius)
    {
            ArrayList<Integer> stopsWithinRange = new ArrayList<Integer>();
            
            for(Integer stop : stops)
            {
            	int stopNodeIdx = lookupStopNodeIdxByStopCode(stop);
            	if (stopNodeIdx == -1)
            		continue;
                double distance = distance(stopNodeIdx, x, y);
                if (distance < radius)
                        stopsWithinRange.add(stop);
            }
            
            return stopsWithinRange;
    }


	public int lookupStopNodeIdxByStopCode(int stopCode)
	{
		Integer node = nodeIdxByStopCode.get(new Integer(stopCode));
		if (node == null)
			return -1;
		if (node.intValue() >= lat.length)
			return -1;
		return node.intValue();
	}

	public String lookupStopNameByStopNodeIdx(int stopNodeIdx)
	{
		int startoff = stopNameOffset[stopNodeIdx];
		int off = startoff;
		while(rawStopNames[off] != 0)
			off++;

		try {
			return new String(rawStopNames, startoff, off - startoff, "utf-8");
		} catch (Throwable t) {
			return "???";
		}
	}

	public ServiceBitmap lookupServiceBitmapByStopNodeIdx(int stopNodeIdx)
	{
		return new ServiceBitmap(serviceMap0[stopNodeIdx], serviceMap1[stopNodeIdx]);
	}

	public ArrayList<String> getServiceNames(ServiceBitmap serviceMap)
	{
		int maxEntries = serviceBitToServiceName.length;
		String[] tmp = new String[maxEntries];
		for(int i=0; i< maxEntries; i++)
			if (serviceMap.isBitSet(i))
				tmp[serviceBitToSortIndex.get(i)] = serviceBitToServiceName[i];

		ArrayList<String> result = new ArrayList<String>();
		for(String cur: tmp) {
			if (cur == null)
				continue;
			result.add(cur);
		}
		return result;
	}

	public int findNearest(int x, int y)
	{	
		return this.searchNearest(rootRecordNum,-1,x,y,0);
	}
	
	public String formatServices(ServiceBitmap servicesMap, int maxServices)
	{
		ArrayList<String> services = this.getServiceNames(servicesMap);

		// Where is string.join()?
		StringBuilder sb = new StringBuilder();
		for(int j = 0; j < services.size(); j++) {
			if ((maxServices != -1) && (j >= maxServices)) {
				sb.append("...");
				break;
			}
			sb.append(services.get(j));
			sb.append(" ");
		}	
		
		return sb.toString();
	}
	




	private StopDbHelper(InputStream is, int length) throws IOException
	{
		// read the entire stream into a memory buffer
		byte[] b = new byte[length];
		is.read(b);

		// check it is valid
		if ((b[0] != DatabaseVersion.charAt(0)) || (b[1] != DatabaseVersion.charAt(1)) || (b[2] != DatabaseVersion.charAt(2)) || (b[3] != DatabaseVersion.charAt(3)))
			throw new RuntimeException("Invalid file format");

		// get the header information
		rootRecordNum = readInt(b, 4);
		this.defaultMapLocationLat = readInt(b, 8);
		this.defaultMapLocationLon = readInt(b, 12);

		// setup the lookup arrays
		this.left = new short[rootRecordNum+1];
		this.right = new short[rootRecordNum+1];
		this.stopCode = new int[rootRecordNum+1];
		this.nodeIdxByStopCode = new HashMap<Integer, Integer>();
		this.lat = new int[rootRecordNum+1];
		this.lon = new int[rootRecordNum+1];
		this.serviceMap0 = new long[rootRecordNum+1];
		this.serviceMap1 = new long[rootRecordNum+1];
		this.facing = new byte[rootRecordNum+1];
		this.stopNameOffset = new int[rootRecordNum+1];
		this.lowerLeftLat = Integer.MAX_VALUE;
		this.lowerLeftLon = Integer.MAX_VALUE;
		this.upperRightLat = Integer.MIN_VALUE;
		this.upperRightLon = Integer.MIN_VALUE;

		// read in the kdtree
		int off = HEADER_SIZE;
		for(int i = 0; i <= rootRecordNum; ++i)
		{
			left[i] = readShort(b, off + 0);
			right[i] = readShort(b, off + 2);
			stopCode[i] = readInt(b, off + 4);
			nodeIdxByStopCode.put(new Integer(stopCode[i]), new Integer(i));
			lat[i] = readInt(b, off + 8);
			lon[i] = readInt(b, off + 12);
			serviceMap1[i] = readLong(b, off + 16);
			serviceMap0[i] = readLong(b, off + 24);
			facing[i] = b[off + 32];
			stopNameOffset[i] = readInt(b, off + 33);

			if (lat[i] < this.lowerLeftLat)
				this.lowerLeftLat = lat[i];
			if (lon[i] < this.lowerLeftLon)
				this.lowerLeftLon = lon[i];
			if (lat[i] > this.upperRightLat)
				this.upperRightLat = lat[i];
			if (lon[i] > this.upperRightLon)
				this.upperRightLon = lon[i];

			off += TREE_NODE_SIZE;
		}

		// read in the services
		int servicesCount = readInt(b, off);
		off += 4;
		this.serviceBitToServiceName = new String[servicesCount];
		this.serviceBitToServiceProviderId = new byte[servicesCount];
		ArrayList<String> sortedServices = new ArrayList<String>();
		for(int i =0; i< servicesCount; i++) {
			serviceBitToServiceProviderId[i] = b[off++];
			
			int startoff = off;
			while(b[off] != 0)
				off++;

			String serviceName = new String(b, startoff, off - startoff, "utf-8").toUpperCase();
			this.serviceBitToServiceName[i] = serviceName;
			this.serviceNameToServiceBit.put(serviceName, new Integer(i));
			sortedServices.add(serviceName);
			off++;
		}

		// read in the raw stop name strings 
		rawStopNames = new byte[b.length - off];
		System.arraycopy(b, off, rawStopNames, 0, rawStopNames.length);

		// now sort the services
		final HashMap<String, Integer> baseServices = new HashMap<String, Integer>();
		Collections.sort(sortedServices, new Comparator<String>() {
			public int compare(String arg0, String arg1) {
				Integer arg0BaseService;
				if (baseServices.containsKey(arg0)) {
					arg0BaseService = baseServices.get(arg0);
				} else {
					try {
						arg0BaseService = new Integer(Integer.parseInt(arg0.replaceAll("[^0-9]", "").trim()));
					} catch (NumberFormatException ex) {
						arg0BaseService = new Integer(999);
					}
					baseServices.put(arg0, arg0BaseService);
				}
				Integer arg1BaseService;
				if (baseServices.containsKey(arg1)) {
					arg1BaseService = baseServices.get(arg1);
				} else {
					try  {
						arg1BaseService = new Integer(Integer.parseInt(arg1.replaceAll("[^0-9]", "").trim()));
					} catch (NumberFormatException ex) {
						arg1BaseService = new Integer(999);						
					}
					baseServices.put(arg1, arg1BaseService);
				}

				if (arg0BaseService.intValue() != arg1BaseService.intValue())
					return arg0BaseService.intValue() - arg1BaseService.intValue();
				return arg0.compareTo(arg1);
			}
		});

		// now, generate the servicebit -> idx lookup table
		for(int idx=0; idx < sortedServices.size(); idx++)
			serviceBitToSortIndex.put(serviceNameToServiceBit.get(sortedServices.get(idx)), new Integer(idx));
	}

	private short readShort(byte[] b, int off)
	{
		return (short) (((((int) b[off+0]) & 0xff) <<  8) | (((int) b[off+1]) & 0xff));
	}

	private int readInt(byte[] b, int off)
	{
		return ((((int) b[off+0]) & 0xff) << 24) |
				((((int) b[off+1]) & 0xff) << 16) |
				((((int) b[off+2]) & 0xff) <<  8) |
				(((int) b[off+3]) & 0xff);
	}

	private long readLong(byte[] b, int off)
	{
		return ((((long) b[off+0]) & 0xff) << 56) |
				((((long) b[off+1]) & 0xff) << 48) |
				((((long) b[off+2]) & 0xff) << 40) |
				((((long) b[off+3]) & 0xff) << 32) |
				((((long) b[off+4]) & 0xff) << 24) |
				((((long) b[off+5]) & 0xff) << 16) |
				((((long) b[off+6]) & 0xff) <<  8) |
				(((long) b[off+7]) & 0xff);
	}

	// Could use Android location class to do this, but kept in here from prototype.
	// sqrt isn't technically needed, but in here to possibly do distance conversions later.

	private double distance(int stopIdx, int x, int y)
	{
		double deltax = (lat[stopIdx]-x) / 1E6;
		double deltay = (lon[stopIdx]-y) / 1E6;
		return Math.sqrt((deltax * deltax) + (deltay * deltay));
	}

	// Recursive search - use 'findNearest' to start
	private int searchNearest(int here, int best, int x, int y, int depth)
	{
		if (here == -1) return best;
		if (best == -1) best = here;

		double herepos, wantedpos;

		if (depth % 2 == 0) {
			herepos = lat[here] / 1E6;
			wantedpos = x / 1E6;
		}
		else {
			herepos = lon[here] / 1E6;
			wantedpos = y / 1E6;
		}

		// Which is closer?
		double disthere = distance(here,x,y);
		double distbest = distance(best,x,y);

		if (disthere < distbest) 
			best = here;

		// Which branch is nearer?
		int nearest, furthest;

		if (wantedpos < herepos) {
			nearest = left[here];
			furthest = right[here];
		}
		else{
			furthest = left[here];
			nearest = right[here];
		}

		best = searchNearest(nearest,best,x,y,depth+1);

		// Do we still need to search the away branch?
		distbest = distance(best,x,y);

		double distaxis = Math.abs(wantedpos - herepos);

		if (distaxis < distbest)
			best = searchNearest(furthest,best,x,y,depth+1);

		return best;
	}

	private ArrayList<Integer> searchRect(int xtl, int ytl,
			int xbr, int ybr,
			int here,
			ArrayList<Integer> stops,
			int depth)
			{
		if (here==-1) return stops;

		int topleft, bottomright, herepos, herex, herey;

		herex=lat[here];
		herey=lon[here];

		if (depth % 2 == 0) {
			herepos = herex;
			topleft = xtl;
			bottomright = xbr;
		}
		else {
			herepos = herey;
			topleft = ytl;
			bottomright = ybr;
		}

		if (topleft > bottomright) {
			Log.println(Log.ERROR,"redbus", "co-ord error!");
		}

		if (bottomright > herepos) {
			stops = searchRect(xtl,ytl,xbr,ybr,right[here],stops, depth+1);
		}

		if (topleft < herepos) {
			stops = searchRect(xtl,ytl,xbr,ybr,left[here],stops, depth+1);
		}

		// If this node falls within range, add it
		if (xtl <= herex && xbr >= herex && ytl <= herey && ybr >= herey) {
			stops.add(here);
		}

		return stops;
	}
}
