#!/usr/bin/python

# Copyright 2010 Colin Paton - cozzarp@googlemail.com
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

import psycopg2
from kdtree import *
import struct,os

# Connect to database
db = psycopg2.connect("host=beyond dbname=redbus user=redbus password=password")
curs = db.cursor()
mapcurs = db.cursor()

# Use only the latest data
curs.execute("SELECT max(created_date) FROM services")
latestdate = curs.fetchone()[0]

# Get the list of services from the database
servicesById = {}
servicesList = []
curs.execute("SELECT service_id, service_name FROM services WHERE created_date = %s ORDER BY service_name", (latestdate, ))
for row in curs:
    dbserviceid = row[0]
    service_name  = row[1]
    service = { 'DbServiceId' : dbserviceid, 
                'ServiceName' : service_name,
                'ServiceIdx'  : len(servicesList) 
              };             
    servicesById[dbserviceid] = service
    servicesList.append(service);
if len(servicesList) > 64:
    print >>sys.stderr, "Error: more than 64 services found - need to fix file format!"
    sys.exit(1)

# Get the list of stops from the database
stops=[]
curs.execute("SELECT stop_id, stop_code, stop_name, x, y FROM stops WHERE created_date = %s", (latestdate, ))
for row in curs:
    # stop data
    dbstopid = row[0]
    code = row[1]
    name = row[2]
    x = float(row[3])
    y = float(row[4])

    stopmap = 0
    mapcurs.execute("SELECT service_id FROM stops_services WHERE created_date = %s AND stop_id = %s", (latestdate, dbstopid))
    for maprow in mapcurs:
        service = servicesById[maprow[0]]
        stopmap |= 1 << service['ServiceIdx']

    stops.append(((x,y),code,name,stopmap))

tree=kdtree(stops)

def recordnumgenerator():
    num=0
    while 1:
        yield num
        num+=1

# Header - 8 bytes - 'bus1', integer root pos

f=file("stops.dat","wb")
f.seek(8,os.SEEK_SET)
recordnumgen=recordnumgenerator()
rootpos=tree.write(f,recordnumgen)
f.seek(0,os.SEEK_SET)
print rootpos ," root"

f.write(struct.pack('>4si','bus1',rootpos))
f.close()

f=file("services.dat", "wb")
for service in servicesList:
    print >>f, service['ServiceName']
f.close()
