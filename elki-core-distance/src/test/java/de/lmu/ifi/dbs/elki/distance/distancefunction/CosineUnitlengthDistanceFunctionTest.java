/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 *
 * Copyright (C) 2017
 * ELKI Development Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.lmu.ifi.dbs.elki.distance.distancefunction;

import static de.lmu.ifi.dbs.elki.math.linearalgebra.VMath.normalizeEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.data.HyperBoundingBox;
import de.lmu.ifi.dbs.elki.data.SparseDoubleVector;
import de.lmu.ifi.dbs.elki.math.MathUtil;

/**
 * Unit test for unit-length cosine distance.
 *
 * @author Erich Schubert
 */
public class CosineUnitlengthDistanceFunctionTest {
  @Test
  public void compareOnUnitSphere() {
    Random r = new Random(0L);
    double[][] data = new double[100][10];
    for(int i = 0; i < data.length; i++) {
      double[] row = data[i];
      for(int j = 0; j < row.length; j++) {
        row[j] = r.nextDouble();
      }
      normalizeEquals(row);
    }

    CosineUnitlengthDistanceFunction cosnorm = CosineUnitlengthDistanceFunction.STATIC;
    CosineDistanceFunction cosfull = CosineDistanceFunction.STATIC;
    for(int i = 0; i < data.length; i++) {
      for(int j = 0; j < data.length; j++) {
        double actual = cosnorm.distance(DoubleVector.wrap(data[i]), DoubleVector.wrap(data[j]));
        double expected = cosfull.distance(DoubleVector.wrap(data[i]), DoubleVector.wrap(data[j]));
        assertEquals("Distances do not agree.", expected, actual, 1e-15);
        actual = cosnorm.minDist(DoubleVector.wrap(data[i]), DoubleVector.wrap(data[j]));
        expected = cosfull.minDist(DoubleVector.wrap(data[i]), DoubleVector.wrap(data[j]));
        assertEquals("Distances do not agree.", expected, actual, 1e-15);
        actual = cosnorm.minDist(new HyperBoundingBox(data[i], data[i]), new HyperBoundingBox(data[j], data[j]));
        expected = cosfull.minDist(new HyperBoundingBox(data[i], data[i]), new HyperBoundingBox(data[j], data[j]));
        assertEquals("Distances do not agree.", expected, actual, 1e-15);
      }
    }
  }

  @Test
  public void compareSparseUnitSphere() {
    Random r = new Random(0L);
    int dim = 100, set = 20;
    int[] dimset = MathUtil.sequence(0, dim);
    SparseDoubleVector[] sparse = new SparseDoubleVector[100];
    for(int i = 0; i < sparse.length; i++) {
      // Fisher-Yates shuffle to choose non-zero dimensions
      int[] idx = new int[set];
      for(int j = 0; j < set; j++) {
        int p = r.nextInt(dim - j) + j;
        int tmp = dimset[j]; // Swap
        idx[j] = dimset[j] = dimset[p];
        dimset[p] = tmp; // Swap
      }
      Arrays.sort(idx);
      // Choose values
      double[] vals = new double[set];
      for(int j = 0; j < set; j++) {
        vals[j] = r.nextDouble();
      }
      normalizeEquals(vals);
      sparse[i] = new SparseDoubleVector(idx, vals, dim);
    }
    DoubleVector[] dense = new DoubleVector[10];
    for(int i = 0; i < dense.length; i++) {
      double[] row = new double[dim];
      for(int j = 0; j < dim; j++) {
        row[j] = r.nextDouble();
      }
      dense[i] = DoubleVector.wrap(normalizeEquals(row));
    }

    CosineUnitlengthDistanceFunction cosnorm = CosineUnitlengthDistanceFunction.STATIC;
    CosineDistanceFunction cosfull = CosineDistanceFunction.STATIC;
    for(int i = 0; i < sparse.length; i++) {
      for(int j = 0; j < sparse.length; j++) {
        double actual = cosnorm.distance(sparse[i], sparse[j]);
        double expected = cosfull.distance(sparse[i], sparse[j]);
        assertEquals("Distances do not agree.", expected, actual, 1e-15);
        actual = cosnorm.minDist(sparse[i], sparse[j]);
        expected = cosfull.minDist(sparse[i], sparse[j]);
        assertEquals("Distances do not agree.", expected, actual, 1e-15);
      }
      for(int j = 0; j < dense.length; j++) {
        double actual = cosnorm.distance(sparse[i], dense[j]);
        double expected = cosfull.distance(sparse[i], dense[j]);
        assertEquals("Distances do not agree.", expected, actual, 1e-15);
        actual = cosnorm.minDist(sparse[i], dense[j]);
        expected = cosfull.minDist(sparse[i], dense[j]);
        assertEquals("Distances do not agree.", expected, actual, 1e-15);
        actual = cosnorm.distance(dense[j], sparse[i]);
        expected = cosfull.distance(dense[j], sparse[i]);
        assertEquals("Distances do not agree.", expected, actual, 1e-15);
        actual = cosnorm.minDist(dense[j], sparse[i]);
        expected = cosfull.minDist(dense[j], sparse[i]);
        assertEquals("Distances do not agree.", expected, actual, 1e-15);
      }
    }
  }

  @Test
  public void testNotCosine() {
    DoubleVector d1 = DoubleVector.wrap(new double[] { 1, 0 });
    DoubleVector d2 = DoubleVector.wrap(new double[] { .5, 0 });
    assertEquals("Cosine not ok", 0, CosineDistanceFunction.STATIC.distance(d1, d2), 0);
    assertEquals("Length not ignored", 0.5, CosineUnitlengthDistanceFunction.STATIC.distance(d1, d2), 0);
  }
}