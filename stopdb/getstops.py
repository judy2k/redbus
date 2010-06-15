#!/usr/bin/python

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

import sys
import os
import mechanize
import BeautifulSoup
import datetime
from xml.dom.minidom import parseString
from xml import xpath
import psycopg2
import time

nowdate = datetime.datetime.today()

browser = mechanize.Browser()
browser.set_handle_robots(False)
browser.add_headers = []

# grab the list of all bus services
services = {}
soup = BeautifulSoup.BeautifulSoup(browser.open('http://www.mybustracker.co.uk/index.php?display=Service').read())
service_select = soup.find("select", {"name":"serviceService"})
for option in service_select.findAll("option"):
    serviceName = option["value"].split('|')[0].strip();
    services[serviceName] = { 'ServiceName': serviceName }
print >>sys.stderr, "Found %i services" % len(services)

# Now grab the stop details for each
stops = {}
count = 0
for service in services:
    count += 1
    print >>sys.stderr, "Processing service \"%s\" (%i/%i)" % (service, count, len(services))
    servicedom = parseString(browser.open('http://www.mybustracker.co.uk/getServicePoints.php?serviceMnemo=%s' % service).read())
    
    for stop in xpath.Evaluate('//busStop', servicedom.documentElement):
        stopCode = xpath.Evaluate('sms/text()', stop)[0].data.strip()
        stopName = xpath.Evaluate('nom/text()', stop)[0].data.strip()
        x = xpath.Evaluate('x/text()', stop)[0].data.strip()
        y = xpath.Evaluate('y/text()', stop)[0].data.strip()
        servicesAtThisStop = [tmpsrv.data.strip() for tmpsrv in xpath.Evaluate('services/service/mnemo/text()', stop)]

        for tmpservice in servicesAtThisStop:
            if not services.has_key(tmpservice):
                print >>sys.stderr, "Warning: Stop %s has services which do not exist (%s)" % (stopCode, tmpservice)
                
        
        if stops.has_key(stopCode):
            oldStop = stops[stopCode]
            
            if oldStop['StopName'] != stopName:
                print >>sys.stderr, "Warning: Stop %s has differing names (%s != %s) between services" % (stopCode, oldStop['StopName'], stopName)
            if oldStop['X'] != x:
                print >>sys.stderr, "Warning: Stop %s has differing X coordinate (%s != %s) between services" % (stopCode, oldStop['X'], x)
            if oldStop['Y'] != y:
                print >>sys.stderr, "Warning: Stop %s has differing Y coordinate (%s != %s) between services" % (stopCode, oldStop['Y'], y)
            if ','.join(oldStop['Services']) != ','.join(servicesAtThisStop):
                print >>sys.stderr, "Warning: Stop %s has differing services list (%s != %s) between services" % (stopCode, ','.join(oldStop['Services']), ','.join(servicesAtThisStop))
        else:
            stops[stopCode] = { 'StopCode': stopCode,
                                'StopName': stopName,
                                'X': x,
                                'Y': y,
                                'Services': servicesAtThisStop }  

# Connect to database
db = psycopg2.connect("host=beyond dbname=redbus user=redbus password=password")
dbcur = db.cursor()

# Add services to the database
for service in services.values():
    dbcur.execute("INSERT INTO services (service_name, created_date) VALUES (%s, %s); SELECT last_value FROM services_service_id_seq", 
                  (service['ServiceName'], nowdate))
    service['DbServiceId'] = dbcur.fetchone()[0]
db.commit()

# Add stops to the database
for stop in stops.values():
    dbcur.execute("INSERT INTO stops (stop_code, stop_name, x, y, created_date) VALUES (%s, %s, %s, %s, %s); SELECT last_value FROM stops_stop_id_seq", 
                  (stop['StopCode'], stop['StopName'], stop['X'], stop['Y'], nowdate))
    stop['DbStopId'] = dbcur.fetchone()[0]
    db.commit()

# Finally, populate the link table between services and stops
for stop in stops.values():
    for serviceName in stop['Services']:
        if services.has_key(serviceName):
            service = services[serviceName]
            dbcur.execute("INSERT INTO stops_services (stop_id, service_id, created_date) VALUES (%s, %s, %s)", 
                          (stop['DbStopId'], service['DbServiceId'], nowdate))
    db.commit()

# DONE
dbcur.close()
db.close()
