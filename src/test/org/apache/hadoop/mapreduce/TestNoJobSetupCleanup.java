/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.mapreduce;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.mapred.FileAlreadyExistsException;
import org.apache.hadoop.mapred.HadoopTestCase;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

public class TestNoJobSetupCleanup extends HadoopTestCase {
  private static String TEST_ROOT_DIR =
    new File(System.getProperty("test.build.data","/tmp"))
    .toURI().toString().replace(' ', '+');
  private final Path inDir = new Path(TEST_ROOT_DIR, "./wc/input");
  private final Path outDir = new Path(TEST_ROOT_DIR, "./wc/output");

  public TestNoJobSetupCleanup() throws IOException {
    super(HadoopTestCase.CLUSTER_MR , HadoopTestCase.LOCAL_FS, 2, 2);
  }

  private Job submitAndValidateJob(JobConf conf, int numMaps, int numReds)
      throws IOException, InterruptedException, ClassNotFoundException {
    conf.setJobSetupCleanupNeeded(false);
    Job job = MapReduceTestUtil.createJob(conf, inDir, outDir,
                numMaps, numReds);

    job.setOutputFormatClass(MyOutputFormat.class);
    job.waitForCompletion(true);
    assertTrue(job.isSuccessful());
    JobID jobid = (org.apache.hadoop.mapred.JobID)job.getID();

    JobClient jc = new JobClient(conf);
    assertTrue(jc.getSetupTaskReports(jobid).length == 0);
    assertTrue(jc.getCleanupTaskReports(jobid).length == 0);
    assertTrue(jc.getMapTaskReports(jobid).length == numMaps);
    assertTrue(jc.getReduceTaskReports(jobid).length == numReds);
    FileSystem fs = FileSystem.get(conf);
    assertTrue("Job output directory doesn't exit!", fs.exists(outDir));
    FileStatus[] list = fs.listStatus(outDir, new OutputFilter());
    int numPartFiles = numReds == 0 ? numMaps : numReds;
    assertTrue("Number of part-files is " + list.length + " and not "
        + numPartFiles, list.length == numPartFiles);
    return job;
  }

  public void testNoJobSetupCleanup() throws Exception {
    try {
      JobConf conf = createJobConf();

      // run a job without job-setup and cleanup
      submitAndValidateJob(conf, 1, 1);

      // run a map only job.
      submitAndValidateJob(conf, 1, 0);

      // run empty job without job setup and cleanup
      submitAndValidateJob(conf, 0, 0);

      // run empty job without job setup and cleanup, with non-zero reduces
      submitAndValidateJob(conf, 0, 1);
    } finally {
      tearDown();
    }
  }

  static class MyOutputFormat extends TextOutputFormat {
    public void checkOutputSpecs(JobContext job)
        throws FileAlreadyExistsException, IOException{
      super.checkOutputSpecs(job);
      // creating dummy TaskAttemptID
      TaskAttemptID tid = new TaskAttemptID("jt", 1, true, 0, 0);
      getOutputCommitter(new TaskAttemptContext(job.getConfiguration(), tid)).
        setupJob(job);
    }
  }

  private static class OutputFilter implements PathFilter {
    public boolean accept(Path path) {
      return !(path.getName().startsWith("_"));
    }
  }
}
