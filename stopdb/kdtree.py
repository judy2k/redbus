# Copyright 2010 Colin Paton - cozzarp@googlemail.com
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
#
# Algorithms "inspired by" wikipedia kd-tree page algorithms :-)

import math
import struct

class Node:
    def __repr__(self):
        return self.location.__str__()

    def dump(self,maxdepth=1000,depth=0):
	if depth == maxdepth: return

	if self.leftChild:
	    self.leftChild.dump(maxdepth,depth+1)
        print "    "*depth, self.location
	if self.rightChild:
	    self.rightChild.dump(maxdepth,depth+1)

    def write(self,file,recordnumgen):
        leftfilepos=-1
        rightfilepos=-1

        if self.leftChild:
            leftfilepos=self.leftChild.write(file,recordnumgen)
        if self.rightChild:
            rightfilepos=self.rightChild.write(file,recordnumgen)
        
        bin=struct.pack(">iiddI16sQ",leftfilepos,rightfilepos,
                                      self.location[0][0],      # axis 0
                                      self.location[0][1],      # axis 1
                                      self.location[1],         # stop code
                                      (self.location[2]+u"                ").encode('utf-8'),    # stop name
                                      self.location[3]          # stopmap
                        )
        file.write(bin)
        return recordnumgen.next()

 
def kdtree(pointList, ndims=2, depth=0):
    if not pointList:
        return

    # Select axis based on depth so that axis cycles through all valid values
    axis = depth % ndims
 
    # Sort point list and choose median as pivot element
    pointList.sort(key=lambda point: point[0][axis])
    median = len(pointList)/2 # choose median
 
    # Create node and construct subtrees
    node = Node()
    node.location = pointList[median]
    node.leftChild = kdtree(pointList[0:median], ndims, depth+1)
    node.rightChild = kdtree(pointList[median+1:], ndims, depth+1)
    return node

def distance(a,b):
    x1,y1=a
    x2,y2=b

    return math.sqrt((x1-x2)**2 + (y1-y2)**2)

def searchnearest(here,wantedpos,best=None,depth=0):
    if here is None: return best
    if best is None: best = here

    # How about this node?
    disthere = distance(here.location[0],wantedpos)
    distbest = distance(best.location[0],wantedpos)
    if depth %2 == 0: print "---"
    print depth, depth %2 , ":", here, "best is",best.location[1:], "herecloser ",disthere<distbest

    if disthere < distbest: # and here.location[1] != 36235324:
        print "    New best! distance is ",disthere-distbest
        print "    Old best ",best, "distance wanted<>best ",distbest
        print "    New best ",here, "distance wanted<>new ",disthere
        print "    Wanted: ",wantedpos
        best = here

    # Work out which children to search

    axis = depth % 2
    nodeval = here.location[0][axis]
    wantedposval = wantedpos[axis]

    #print "Axis ", axis, "Nos pos ",nodeval, " Wanted pos ",wantedposval

    # Search the nearest branch
    if wantedposval < nodeval:
	nearestNode=here.leftChild
        furthestNode=here.rightChild
    if wantedposval >= nodeval:
        nearestNode=here.rightChild
        furthestNode=here.leftChild

    if wantedposval == nodeval:
        print "What now?!?!"
#        return 1/0

    best=searchnearest(nearestNode,wantedpos,best,depth+1)

    # Search the away branch - maybe
    distbest = distance(best.location[0],wantedpos)

    #print "Distbest is ",distbest," Axis dist ",abs(wantedposval-nodeval)
    distaxis = abs(wantedposval-nodeval)  # distance_axis(here,point)

    if distaxis <= distbest:
        print "Search furthest ",distaxis, " wanted pos ",wantedposval, " node val ",nodeval
        best = searchnearest(furthestNode,wantedpos,best,depth+1)

    return best

def searchrange(node,topleft,bottomright,ndims=2,depth=0):
   if node is None: return None

   axis = depth % ndims
   nodelocation = node.location[0]

   topleftval = topleft[axis]
   bottomrightval = bottomright[axis]
   nodeval = nodelocation[axis]

   if topleftval > bottomrightval:
       print "Error! co-ords incorrect."
       return 

   print node.location[1:], "Axis: ",axis, " Topleft: ",topleftval, " Node: ",nodeval, " Bottomright: ",bottomrightval

   if bottomrightval > nodeval:
       searchrange(node.rightChild,topleft,bottomright,ndims,depth+1) 
   if topleftval < nodeval: 
       searchrange(node.leftChild,topleft,bottomright,ndims,depth+1)

   nodelocation = node.location[0]

   if topleft[0] <= nodelocation[0] and bottomright[0] >= nodelocation[0] and \
      topleft[1] <= nodelocation[1] and bottomright[1] >= nodelocation[1]:
       print node.location
