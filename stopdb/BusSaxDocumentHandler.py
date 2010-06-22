# -*- coding: utf-8 -*-

import sys, string
from xml.sax import handler, make_parser

class BusSaxDocumentHandler(handler.ContentHandler):
    def __init__(self, services, stops):
	self.services = services
	self.stops = stops

	self.inBusStop = False
	self.curElementName = ''
	self.servicesAtThisStop = []
	self.x = ''
	self.y = ''
	self.stopCode = ''
	self.stopName = ''
	self.curService = ''

    def startDocument(self):
	pass

    def endDocument(self):
	pass

    def startElement(self, name, attrs):
	if name == 'busStop':
       	    self.servicesAtThisStop = []
       	    self.x = ''
            self.y = ''
            self.stopCode = ''
            self.stopName = ''		
            self.curService = ''
            self.inBusStop = True
        elif name == 'mnemo':
            self.curService = ''

	self.curElementName = name

    def endElement(self, name):
	if name == 'busStop':
	    for tmpservice in self.servicesAtThisStop:
	        if not self.services.has_key(tmpservice):
	            print >>sys.stderr, "Warning: Stop %s has services which do not exist (%s)" % (self.stopCode, tmpservice)

            if self.stops.has_key(self.stopCode):
                oldStop = self.stops[self.stopCode]

                if oldStop['StopName'] != self.stopName:
                    print >>sys.stderr, "Warning: Stop %s has differing names (%s != %s) between services" % (self.stopCode, oldStop['StopName'], self.stopName)
                if oldStop['X'] != self.x:
                    print >>sys.stderr, "Warning: Stop %s has differing X coordinate (%s != %s) between services" % (self.stopCode, oldStop['X'], self.x)
                if oldStop['Y'] != self.y:
                    print >>sys.stderr, "Warning: Stop %s has differing Y coordinate (%s != %s) between services" % (self.stopCode, oldStop['Y'], self.y)
                if ','.join(oldStop['Services']) != ','.join(self.servicesAtThisStop):
                    print >>sys.stderr, "Warning: Stop %s has differing services list (%s != %s) between services" % (self.stopCode, ','.join(oldStop['Services']), ','.join(self.servicesAtThisStop))
            else:
                self.stops[self.stopCode] = {   'StopCode': self.stopCode,
                                                'StopName': self.stopName,
                                                'X': self.x,
                                                'Y': self.y,
                                                'Services': self.servicesAtThisStop }  
            self.inBusStop = False
        
        elif name == 'mnemo':
            self.servicesAtThisStop += ( self.curService.strip(), )

    def characters(self, chrs):
	if not self.inBusStop:
            return
		
	if self.curElementName == 'sms':
            self.stopCode += chrs
	elif self.curElementName == 'nom':
            self.stopName += chrs
	elif self.curElementName == 'x':
            self.x += chrs
	elif self.curElementName == 'y':
            self.y += chrs
	elif self.curElementName == 'mnemo':
	    self.curService += chrs
