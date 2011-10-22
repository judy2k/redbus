# -*- coding: utf-8 -*-

import sys, string
from xml.sax import handler, make_parser

class BusServiceSaxDocumentHandler(handler.ContentHandler):
    def __init__(self, services, stops, mergeService):
        self.services = services
        self.stops = stops
        self.mergeService = mergeService

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
            if self.mergeService in self.servicesAtThisStop:
                if self.stopCode in self.stops:
                    oldStop = self.stops[self.stopCode]
                    oldStop['Services'] += ( self.mergeService, )

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
