package org.redbus;

import java.util.List;

public interface BusDataResponseListener {
	public void getBusTimesError(int code, String message);
	public void getBusTimesSuccess(List<BusTime> busTimes);
}
