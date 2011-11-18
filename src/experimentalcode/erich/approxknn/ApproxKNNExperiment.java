package experimentalcode.erich.approxknn;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

import de.lmu.ifi.dbs.elki.algorithm.outlier.KNNOutlier;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.StaticArrayDatabase;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.HashSetModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.datasource.AbstractDatabaseConnection;
import de.lmu.ifi.dbs.elki.datasource.FileBasedDatabaseConnection;
import de.lmu.ifi.dbs.elki.datasource.filter.ClassLabelFilter;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.EuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.evaluation.roc.ROC;
import de.lmu.ifi.dbs.elki.index.preprocessed.knn.MaterializeKNNPreprocessor;
import de.lmu.ifi.dbs.elki.index.preprocessed.knn.RandomSampleKNNPreprocessor;
import de.lmu.ifi.dbs.elki.index.tree.TreeIndexFactory;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.LoggingConfiguration;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.utilities.ClassGenericsUtil;
import de.lmu.ifi.dbs.elki.utilities.DatabaseUtil;
import de.lmu.ifi.dbs.elki.utilities.FormatUtil;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ListParameterization;
import de.lmu.ifi.dbs.elki.utilities.pairs.DoubleDoublePair;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2011
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * Simple experiment to estimate the effects of approximating the kNN distances.
 * 
 * @author Erich Schubert
 */
public class ApproxKNNExperiment {
  private static final Logging logger = Logging.getLogger(ApproxKNNExperiment.class);

  DistanceFunction<? super NumberVector<?, ?>, DoubleDistance> distanceFunction = EuclideanDistanceFunction.STATIC;

  private void run() {
    Database database = loadDatabase();
    Relation<NumberVector<?, ?>> rel = database.getRelation(TypeUtil.NUMBER_VECTOR_FIELD);
    DBIDs ids = rel.getDBIDs();
    HashSetModifiableDBIDs pos = DBIDUtil.newHashSet();

    // Number of iterations and step size
    final int iters = 10;
    final int step = 10;
    final int maxk = iters * step;

    // Build positive ids (outliers) once.
    {
      Pattern p = Pattern.compile("Outlier", Pattern.CASE_INSENSITIVE);
      Relation<String> srel = DatabaseUtil.guessLabelRepresentation(database);
      for(DBID id : ids) {
        String s = srel.get(id);
        if(s == null) {
          logger.warning("Object without label: " + id);
        }
        else if(p.matcher(s).matches()) {
          pos.add(id);
        }
      }
    }

    // Collect the data for output
    double[][] data = new double[step][3];
    // Results for full kNN:
    {
      // Setup preprocessor
      MaterializeKNNPreprocessor.Factory<NumberVector<?, ?>, DoubleDistance> ppf = new MaterializeKNNPreprocessor.Factory<NumberVector<?, ?>, DoubleDistance>(maxk + 1, distanceFunction);
      MaterializeKNNPreprocessor<NumberVector<?, ?>, DoubleDistance> pp = ppf.instantiate(rel);
      database.addIndex(pp);

      FiniteProgress prog = logger.isVerbose() ? new FiniteProgress("kNN iterations", iters, logger) : null;
      for(int i = 1; i <= iters; i++) {
        final int k = i * step;
        KNNOutlier<NumberVector<?, ?>, DoubleDistance> knn = new KNNOutlier<NumberVector<?, ?>, DoubleDistance>(distanceFunction, k);
        OutlierResult res = knn.run(database, rel);
        List<DoubleDoublePair> roccurve = ROC.materializeROC(ids.size(), pos, new ROC.OutlierScoreAdapter(res));
        double auc = ROC.computeAUC(roccurve);
        data[i - 1][0] = auc;
        if(prog != null) {
          prog.incrementProcessed(logger);
        }
      }

      // Remove the preprocessor again.
      database.removeIndex(pp);

      if(prog != null) {
        prog.ensureCompleted(logger);
      }
    }

    // Partial kNN outlier
    {
      FiniteProgress prog = logger.isVerbose() ? new FiniteProgress("Approximations.", iters - 1, logger) : null;
      for(int i = 1; i < iters; i++) {
        final int k = i * step;
        double share = i / (double) iters;
        // Setup preprocessor
        RandomSampleKNNPreprocessor.Factory<NumberVector<?, ?>, DoubleDistance> ppf = new RandomSampleKNNPreprocessor.Factory<NumberVector<?, ?>, DoubleDistance>(maxk + 1, distanceFunction, share, 1L);
        RandomSampleKNNPreprocessor<NumberVector<?, ?>, DoubleDistance> pp = ppf.instantiate(rel);
        database.addIndex(pp);

        // Max k run
        {
          KNNOutlier<NumberVector<?, ?>, DoubleDistance> knn = new KNNOutlier<NumberVector<?, ?>, DoubleDistance>(distanceFunction, maxk);
          OutlierResult res = knn.run(database, rel);
          List<DoubleDoublePair> roccurve = ROC.materializeROC(ids.size(), pos, new ROC.OutlierScoreAdapter(res));
          double auc = ROC.computeAUC(roccurve);
          data[i - 1][1] = auc;
        }
        // Scaled k run
        {
          KNNOutlier<NumberVector<?, ?>, DoubleDistance> knn = new KNNOutlier<NumberVector<?, ?>, DoubleDistance>(distanceFunction, k);
          OutlierResult res = knn.run(database, rel);
          List<DoubleDoublePair> roccurve = ROC.materializeROC(ids.size(), pos, new ROC.OutlierScoreAdapter(res));
          double auc = ROC.computeAUC(roccurve);
          data[i - 1][2] = auc;
        }

        if(prog != null) {
          prog.incrementProcessed(logger);
        }
      }
      if(prog != null) {
        prog.ensureCompleted(logger);
      }
    }
    for(int i = 0; i < step; i++) {
      System.out.println((i + 1) + " " + FormatUtil.format(data[i], " "));
    }
  }

  private Database loadDatabase() {
    try {
      ListParameterization dbpar = new ListParameterization();
      // Input file
      dbpar.addParameter(FileBasedDatabaseConnection.INPUT_ID, "/nfs/multimedia/images/ALOI/ColorHistograms/outlier/aloi-27d-75000-max4-tot717.csv.gz");
      // Index
      dbpar.addParameter(StaticArrayDatabase.INDEX_ID, "tree.spatial.rstarvariants.rstar.RStarTreeFactory");
      dbpar.addParameter(TreeIndexFactory.PAGE_SIZE_ID, "10000");
      // Class label filter
      List<Object> list = new ArrayList<Object>(1);
      list.add(ClassLabelFilter.class);
      dbpar.addParameter(AbstractDatabaseConnection.FILTERS_ID, list);
      dbpar.addParameter(ClassLabelFilter.CLASS_LABEL_INDEX_ID, 2);
      // Instantiate
      Database db = ClassGenericsUtil.tryInstantiate(Database.class, StaticArrayDatabase.class, dbpar);
      db.initialize();
      return db;
    }
    catch(Exception e) {
      throw new RuntimeException("Cannot load database.", e);
    }
  }

  public static void main(String[] args) {
    LoggingConfiguration.setDefaultLevel(Level.INFO);
    logger.getWrappedLogger().setLevel(Level.INFO);
    try {
      new ApproxKNNExperiment().run();
    }
    catch(Exception e) {
      logger.exception(e);
    }
  }
}