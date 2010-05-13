package org.redbus;

public class BusTime 
{	
	public String service;
	public String destination;
	public boolean lowFloorBus;
	public boolean arrivalEstimated;
	public boolean arrivalIsDue;
	public int arrivalMinutesLeft;
	public String arrivalAbsoluteTime;
	
	public BusTime(String service, String destination, boolean lowFloorBus, boolean arrivalEstimated, boolean arrivalIsDue, int arrivalMinutesLeft, String arrivalAbsoluteTime) 
	{
		this.service = service;
		this.destination = destination;
		this.lowFloorBus = lowFloorBus;
		this.arrivalEstimated = arrivalEstimated;
		this.arrivalIsDue = arrivalIsDue;
		this.arrivalMinutesLeft = arrivalMinutesLeft;
		this.arrivalAbsoluteTime = arrivalAbsoluteTime;
	}
}
