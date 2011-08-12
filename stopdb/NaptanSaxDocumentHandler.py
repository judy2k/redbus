# -*- coding: utf-8 -*-

# Copyright 2010, 2011 Andrew De Quincey -  adq@lidskialf.net
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

import sys, string
from xml.sax import handler, make_parser

class NaptanSaxDocumentHandler(handler.ContentHandler):
    def __init__(self, stops):
        self.stops = stops

        self.curElementName = ''
        self.stopName = ''
        self.stopCode = ''
        self.x = ''
        self.y = ''
        self.facing = ''
        self.stopType = ''
        self.inStopPoint = False
        self.inPlace = False
        self.inAlternativeDescriptors = False

    def startDocument(self):
        pass

    def endDocument(self):
        pass

    def startElement(self, name, attrs):
        if name == 'StopPoint':
            self.stopCode = ''
            self.stopName = ''
            self.x = ''
            self.y = ''
            self.facing = ''
            self.stopType = ''

            if attrs['Status'] == 'active':
                self.inStopPoint = True

        elif name == 'Place':
            self.inPlace = True

        elif name == 'AlternativeDescriptors':
            self.inAlternativeDescriptors = True

        self.curElementName = name

    def endElement(self, name):
        if name == 'StopPoint' and self.inStopPoint:
            if self.stopCode in self.stops:
                oldStop = self.stops[self.stopCode]

                if oldStop['StopName'] != self.stopName:
                    print("Warning: Stop %s has differing names (%s != %s) between services" % (self.stopCode, oldStop['StopName'], self.stopName), file=sys.stderr)
                if oldStop['X'] != self.x:
                    print("Warning: Stop %s has differing X coordinate (%s != %s) between services" % (self.stopCode, oldStop['X'], self.x), file=sys.stderr)
                if oldStop['Y'] != self.y:
                    print("Warning: Stop %s has differing Y coordinate (%s != %s) between services" % (self.stopCode, oldStop['Y'], self.y), file=sys.stderr)
                if oldStop['Facing'] != self.facing:
                    print("Warning: Stop %s has differing names (%s != %s) between services" % (self.stopCode, oldStop['StopName'], self.stopName), file=sys.stderr)
            else:
                if not self.facing in ('N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW', '' ):
                    print("Warning: Stop %s has invalid facing (%s)" % (self.stopCode, self.facing), file=sys.stderr)

                self.stops[self.stopCode] = {   'StopCode': self.stopCode,
                                                'StopName': self.stopName,
                                                'X': float(self.x),
                                                'Y': float(self.y),
                                                'Facing': self.facing,
                                                'StopType': self.stopType }
            self.inStopPoint = False

        elif name == 'Place':
            self.inPlace = False

        elif name == 'AlternativeDescriptors':
            self.inAlternativeDescriptors = False

    def characters(self, chrs):
        if not self.inStopPoint:
            return

        if self.curElementName == 'NaptanCode':
            self.stopCode += chrs
        elif self.curElementName == 'CompassPoint':
            self.facing += chrs
        elif self.curElementName == 'StopType':
            self.stopType += chrs

        if not self.inAlternativeDescriptors:
            if self.curElementName == 'CommonName':
                self.stopName += chrs

        if self.inPlace:
            if self.curElementName == 'Longitude':
                self.x += chrs
            elif self.curElementName == 'Latitude':
                self.y += chrs
