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

INSTDIR=/usr/local/redbus

# Download stops from the website
# $INSTDIR/getstops.py || exit 1

# Generate new stops database
$INSTDIR/makestopsdat.py $INSTDIR/bus1.dat || exit 1
newsum=`/usr/bin/md5sum $INSTDIR/bus1.dat | /bin/cut -f1 -d ' '` || exit 1

# Deal with old database
if [ ! -f $INSTDIR/stopdata/bus1.dat ]; then
	echo "Old data file was missing!"
	exit 1
fi

# Get the sum of the old file and copy ourselves for next time
oldsum=`/usr/bin/md5sum $INSTDIR/stopdata/bus1.dat | /bin/cut -f1 -d ' '` || exit 1

# Publish if something has changed!
if [ x$oldsum != x$newsum ]; then
	cp -f $INSTDIR/bus1.dat $INSTDIR/stopdata/bus1.dat
	svn commit $INSTDIR/stopdata
fi
