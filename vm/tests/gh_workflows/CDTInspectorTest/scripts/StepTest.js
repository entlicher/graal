/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

// Finds the count of numbers that are smaller than the provided one.

var data = [2, 3, 10, 1, 5, 20, 8, 18, 12, 30];

function binarySearch(array, element) {
  let i1 = 0;
  let i2 = array.length - 1;
  while (i1 < i2) {
    let i = (i1 + i2) >> 1;
    let diff = array[i] - element;
    if (diff > 0) {
      i2 = i;
    } else if (diff < 0) {
      i1 = i;
    } else {
      return i;
    }
  }
  return i1;
}

data = data.sort(function(a,b){return a - b});

var index12 = binarySearch(data, 12);
var index8 = binarySearch(data, 8);
print('Index of element 12 is: ' + index12);
print('Index of element 8 is: ' + index8);
