# -*- coding: utf-8 -*-
# Copyright 2010, 2011 Colin Paton - cozzarp@googlemail.com
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

    def write(self, treeFile, stopNamesFile, recordnumgen):
        leftfilepos=-1
        rightfilepos=-1

        if self.leftChild:
            leftfilepos=self.leftChild.write(treeFile,stopNamesFile,recordnumgen)
        if self.rightChild:
            rightfilepos=self.rightChild.write(treeFile,stopNamesFile,recordnumgen)

        stopNameOffset = stopNamesFile.tell()
        stopNamesFile.write((self.details['stopname'] + '\0').encode('utf-8'))

        treeFile.write(struct.pack(">hhIiiQQBI",
                                    leftfilepos,
                                    rightfilepos,
                                    self.details['stopcode'],
                                    int(self.details['xy'][0] * 1000000),
                                    int(self.details['xy'][1] * 1000000),
                                    (self.details['stopmap'] >> 64) & 0xffffffffffffffff,
                                    self.details['stopmap'] & 0xffffffffffffffff,
                                    self.details['facing'], # note, 4 bits are unused here
                                    stopNameOffset
                                    ))
        return next(recordnumgen)


def makeTree(pointList, ndims=2, depth=0):
    if not pointList:
        return

    # Select axis based on depth so that axis cycles through all valid values
    axis = depth % ndims

    # Sort point list and choose median as pivot element
    pointList.sort(key=lambda point: point['xy'][axis])
    median = int(len(pointList)/2) # choose median

    # Create node and construct subtrees
    node = Node()
    node.details = pointList[median]
    node.leftChild = makeTree(pointList[0:median], ndims, depth+1)
    node.rightChild = makeTree(pointList[median+1:], ndims, depth+1)
    return node
