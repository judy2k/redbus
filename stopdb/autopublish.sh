#!/bin/bash

# Copyright 2010 Andrew De Quincey -  adq@lidskialf.net
# This file is part of rEdBus.
#
#  rEdBus is free software: you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  rEdBus is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with rEdBus.  If not, see <http://www.gnu.org/licenses/>.

# Download stops from the website
# ./getstops.py || exit 1

# Generate new stops database
./makestopsdat.py || exit 1
newsum=`md5sum bus1.dat | cut -f1 -d ' '` || exit 1

# Deal with old database
if [ ! -f bus1.dat.old ]; then
	echo "Old data file was missing!"
	exit 1
fi

# Get the sum of the old file and copy ourselves for next time
oldsum=`md5sum bus1.dat.old | cut -f1 -d ' '` || exit 1
rm -f bus1.dat.old
cp -f bus1.dat bus1.dat.old

# Publish if something has changed!
if [ x$oldsum != x$newsum ]; then
	echo "Would have published!"
fi
