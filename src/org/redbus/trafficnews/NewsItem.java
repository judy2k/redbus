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

import java.util.Date;

public class NewsItem 
{	
	public String tweetId;
	public String location;
	public String description;
	public Date date;
	
	public NewsItem(String guid, Date date, String text) 
	{
		try {
			this.tweetId = guid.substring(guid.lastIndexOf('/') + 1);
		} catch (Throwable t) {
		}
		this.date = date;

		text = text.replaceAll("^[^:]+:", "");
		text = text.replaceAll("#\\S+", "");
		text = text.replaceAll("@\\S+", "");
		text = text.trim();
		
		int dashPos = text.indexOf(8211);
		if (dashPos == -1)
			dashPos = text.indexOf('-');
		if (dashPos != -1) {
			location = text.substring(0, dashPos).trim();
			description = text.substring(dashPos+1).trim();
		} else {
			description = text;
		}
		
		if ((location != null) && (location.length() == 0))
			location = null;
		
		if ((description != null) && (description.length() == 0))
			description = null;
	}
}
