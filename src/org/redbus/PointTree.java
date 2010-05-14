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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Math;

public class PointTree {

	// Represents a node in the tree
	public class BusStopTreeNode {
		private double x;
		private double y;
		private int leftNode;
		private int rightNode;
		private int stopCode;
		private String stopName;
		
		public BusStopTreeNode(DataInputStream inputStream) throws IOException
		{
			this.leftNode = inputStream.readInt();
			this.rightNode = inputStream.readInt();
			this.x = inputStream.readDouble();
			this.y = inputStream.readDouble();
			this.stopCode = inputStream.readInt();	
			
			byte[] b = new byte[16];
			inputStream.read(b,0,16); // Read name
			this.stopName = new String(b);
		}
		
		public String getStopName() { return this.stopName; }
		public int getStopCode() { return this.stopCode; }
		public double getX() { return this.x; }
		public double getY() { return this.y; }
	}
	
	private BusStopTreeNode[] nodes;
	private int rootRecordNum;

	// Read Data from the Android resource 'stops.dat' into memory
	
	public PointTree(InputStream file) throws IOException
	{
		DataInputStream is = new DataInputStream(file);
		
		byte[] b = new byte[4];
        
		is.read(b,0,4); // Header version
		this.rootRecordNum = is.readInt();
			
		// Root is always the last record in the file
		this.nodes = new BusStopTreeNode[rootRecordNum+1];
				
		for(int i = 0; i <= rootRecordNum; ++i)
		{
			nodes[i] = new BusStopTreeNode(is);
		}
	}
	
	// Could use Android location class to do this, but in here to prove to my
	// school maths teacher that I did learn something...
	// sqrt isn't technically needed, but in here to do distance conversions later.
	
	private double distance(BusStopTreeNode node, double x, double y)
	{
		return Math.sqrt(Math.pow(node.getX()-x,2) + Math.pow(node.getY()-y,2));
	}
	
	private BusStopTreeNode lookupNode(int number)
	{
		if (number == -1) return null; // Child is a leaf
		
		return this.nodes[number];
	}
	
	// Recursive search - use 'findNearest' to start
	private BusStopTreeNode searchNearest(BusStopTreeNode here, BusStopTreeNode best, double x, double y, int depth)
	{
		if (here == null) return best;
		if (best == null) best = here;
		
		double herepos, wantedpos;
		
		if (depth % 2 == 0) {
		    herepos = here.getX();
		    wantedpos = x;
		}
		else {
			herepos = here.getY();
			wantedpos = y;
		}
		
		// Which is closer?
		double disthere = distance(here,x,y);
		double distbest = distance(best,x,y);
		
		if (disthere < distbest) best = here;
		
		// Which branch is nearer?
		BusStopTreeNode nearest, furthest;
		
		if (wantedpos < herepos) {
			nearest = lookupNode(here.leftNode);
			furthest = lookupNode(here.rightNode);
		}
		else{
			furthest = lookupNode(here.leftNode);
			nearest = lookupNode(here.rightNode);
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
	
	public BusStopTreeNode findNearest(double x, double y)
	{
		BusStopTreeNode root = this.nodes[this.rootRecordNum];
		
		return this.searchNearest(root,null,x,y,0);
	}
}
