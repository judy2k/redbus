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

public class ServiceBitmap {

	public long bits0;
	public long bits1;
	public boolean areAllSet;
	
	public ServiceBitmap(long bits0, long bits1) {
		this.bits0 = bits0;
		this.bits1 = bits1;
	}
	
	public ServiceBitmap() {
		setAll();
	}

	public ServiceBitmap orWith(ServiceBitmap newValues) {
		bits0 |= newValues.bits0;
		bits1 |= newValues.bits1;

		areAllSet = (bits0 == -1L) && (bits1 == -1L);
		return this;
	}
	
	public ServiceBitmap andWith(ServiceBitmap newValues) {
		bits0 &= newValues.bits0;
		bits1 &= newValues.bits1;

		areAllSet = (bits0 == -1L) && (bits1 == -1L);
		return this;
	}
	
	public ServiceBitmap setTo(ServiceBitmap newValues) {
		bits0 = newValues.bits0;
		bits1 = newValues.bits1;

		areAllSet = (bits0 == -1L) && (bits1 == -1L);
		return this;
	}
	
	public ServiceBitmap setBit(int bit) {
		switch(bit / 64) {
		case 0:
			bits0 |= 1L << (bit % 64);
			break;
		case 1:
			bits1 |= 1L << (bit % 64);
			break;
		}
		
		areAllSet = (bits0 == -1L) && (bits1 == -1L);
		return this;
	}
	
	public boolean isBitSet(int bit) {
		switch(bit / 64) {
		case 0:
			return (bits0 & (1L << (bit % 64))) != 0;
		case 1:
			return (bits1 & (1L << (bit % 64))) != 0;
		}
		return false;
	}

	public boolean areSomeSet(ServiceBitmap testValues) {
		if ((bits0 & testValues.bits0) != 0)
			return true;
		if ((bits1 & testValues.bits1) != 0)
			return true;
		
		return false;
	}
	
	public ServiceBitmap setAll() {
		bits0 = -1L;
		bits1 = -1L;

		areAllSet = true;
		return this;
	}

	public ServiceBitmap clearAll() {
		bits0 = 0L;
		bits1 = 0L;

		areAllSet = true;
		return this;
	}
}
