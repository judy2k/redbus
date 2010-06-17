/*
 * Copyright 2010 Colin Paton - cozzarp@googlemail.com
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
// FIXME - Make into a generic KD-Tree class
// FIXME - currently a bit nasty due to way data is read from resource - e.g. needs nodes[] array
//       - Android only gives an InputStream to a resource - ideally we need Random access.
//       - Read data into memory for now.

package org.redbus;

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

import android.content.Context;
import android.util.Log;


public class PointTree {
	
	private static PointTree pointTree = null;
	
	public static PointTree getPointTree(Context ctx)
	{
		if (pointTree == null) {
			InputStream stopsStream = null;
			OutputStream outStream = null;
			File file = new File("/data/data/org.redbus/files/stops.dat");
			try {
				// first of all, if the file doesn't exist on disk, extract it from our resources and save it out to there
				if (!file.exists()) {
					stopsStream = ctx.getResources().openRawResource(R.raw.stops);
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
				pointTree = new PointTree(stopsStream, (int) file.length());

			} catch (IOException e) {
				Log.println(Log.ERROR,"redbus","Error reading stops");
				e.printStackTrace();
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
				try {
					file.delete();
				} catch (Throwable t) {
				}
			}
		}
		
		return pointTree;
	}
	
	public static final int SERVICE_MAP_LONG_COUNT = 2;
	public static final int KDTREE_RECORD_SIZE = 20;
	public static final int METADATA_RECORD_SIZE = 36;
	
	private byte[] stopMetadata;
	public short[] left;
	public short[] right;
	public double[] lat;
	public double[] lon;
	private int rootRecordNum;
	private Map<Integer, Integer> nodeIdxByStopCode;
	final HashMap<Integer, Integer> serviceBitToSortIndex = new HashMap<Integer, Integer>();
	public HashMap<String, Integer> serviceNameToServiceBit = new HashMap<String, Integer>();
	private String[] serviceBitToServiceName;
	
	private PointTree(InputStream is, int length) throws IOException
	{
		// read the entire stream into a memory buffer
		byte[] b = new byte[length];
		is.read(b);

		// get the root record number
		rootRecordNum = readInt(b, 4);

		// setup the lookup arrays
		this.left = new short[rootRecordNum+1];
		this.right = new short[rootRecordNum+1];
		this.lat = new double[rootRecordNum+1];
		this.lon = new double[rootRecordNum+1];
		this.stopMetadata = new byte[(rootRecordNum+1) * METADATA_RECORD_SIZE];
		this.nodeIdxByStopCode = new HashMap<Integer, Integer>();
		
		// read in the kdtree
		int off = 8;
		for(int i = 0; i <= rootRecordNum; ++i)
		{
			short leftNode = readShort(b, off + 0);
			short rightNode = readShort(b, off + 2);
			double x = Double.longBitsToDouble(readLong(b, off + 4));
			double y = Double.longBitsToDouble(readLong(b, off + 12));
			
			left[i] = leftNode;
			right[i] = rightNode;
			lat[i] = x;
			lon[i] = y;
			
			off += KDTREE_RECORD_SIZE;
		}
		
		// read in the service metadata
		System.arraycopy(b, off, stopMetadata, 0, stopMetadata.length);
		for(int i=0; i<= rootRecordNum; i++) {
			int stopCode = readInt(b, off + 0);
			nodeIdxByStopCode.put(new Integer(stopCode), new Integer(i));
			off += METADATA_RECORD_SIZE;
		}

		// read in the services
		int servicesCount = readInt(b, off);
		off += 4;
		this.serviceBitToServiceName = new String[servicesCount];
		int startoff = off;
		ArrayList<String> sortedServices = new ArrayList<String>();
		for(int i =0; i< servicesCount; i++) {
			while(b[off] != 0)
				off++;

			String serviceName = new String(b, startoff, off - startoff, "utf-8").toUpperCase();
			this.serviceBitToServiceName[i] = serviceName;
			this.serviceNameToServiceBit.put(serviceName, new Integer(i));
			sortedServices.add(serviceName);
			off++;
			startoff = off;
		}

		// now sort the services
		final HashMap<String, Integer> baseServices = new HashMap<String, Integer>();
		Collections.sort(sortedServices, new Comparator<String>() {
			public int compare(String arg0, String arg1) {
				Integer arg0BaseService;
				if (baseServices.containsKey(arg0)) {
					arg0BaseService = baseServices.get(arg0);
				} else {
					arg0BaseService = new Integer(Integer.parseInt(arg0.replaceAll("[^0-9]", "").trim()));
					baseServices.put(arg0, arg0BaseService);
				}
				Integer arg1BaseService;
				if (baseServices.containsKey(arg1)) {
					arg1BaseService = baseServices.get(arg1);
				} else {
					arg1BaseService = new Integer(Integer.parseInt(arg1.replaceAll("[^0-9]", "").trim()));
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
	
	private double distance(int stopIdx, double x, double y)
	{
		return Math.sqrt(Math.pow(lat[stopIdx]-x,2) + Math.pow(lon[stopIdx]-y,2));
	}
	
	// Recursive search - use 'findNearest' to start
	private int searchNearest(int here, int best, double x, double y, int depth)
	{
		if (here == -1) return best;
		if (best == -1) best = here;
		
		double herepos, wantedpos;
		
		if (depth % 2 == 0) {
		    herepos = lat[here];
		    wantedpos = x;
		}
		else {
			herepos = lon[here];
			wantedpos = y;
		}
		
		// Which is closer?
		double disthere = distance(here,x,y);
		double distbest = distance(best,x,y);
		
		if (disthere < distbest) best = here;
		
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
	
	// Public interface to this class - finds the node nearest to the supplied
	// co-ords
	
	public int findNearest(double x, double y)
	{	
		return this.searchNearest(rootRecordNum,-1,x,y,0);
	}
	
	private ArrayList<Integer> searchRect(double xtl, double ytl,
                                     double xbr, double ybr,
                                     int here,
                                     ArrayList<Integer> stops,
                                     int depth)
	{
		if (here==-1) return stops;
		
		double topleft, bottomright, herepos, herex, herey;
		
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

	// Return nodes within a certain rectangle - top-left/bottom-right
	public ArrayList<Integer> findRect(double xtl, double ytl,
	                                             double xbr, double ybr)
	{
		ArrayList<Integer> stops = new ArrayList<Integer>();		
		return searchRect(xtl,ytl,xbr,ybr,rootRecordNum,stops,0);
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
		int off = stopNodeIdx * METADATA_RECORD_SIZE;
		try {
			return new String(stopMetadata, off + 4, 16, "utf-8").trim();
		} catch (Throwable t) {
			return "???";
		}
	}
	
	public int lookupStopCodeByStopNodeIdx(int stopNodeIdx) 
	{
		int off = stopNodeIdx * METADATA_RECORD_SIZE;
		return readInt(stopMetadata, off);
	}
	
	public BusServiceMap lookupServiceMapByStopNodeIdx(int stopNodeIdx)
	{
		long[] bits = new long[SERVICE_MAP_LONG_COUNT];

		int off = (stopNodeIdx * METADATA_RECORD_SIZE) + 20;
		for(int i=0; i< SERVICE_MAP_LONG_COUNT; i++) {
			bits[i] = readLong(stopMetadata, off);
			off += 8;
		}
		
		return new BusServiceMap(bits[1], bits[0]); // reversed 'cos we read them in reversed
	}

	public ArrayList<String> getServiceNames(BusServiceMap serviceMap)
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
}
