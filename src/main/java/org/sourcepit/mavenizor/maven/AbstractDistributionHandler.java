/*
 * Copyright 2014 Bernd Vogt and others.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sourcepit.mavenizor.maven;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.eclipse.aether.util.ChecksumUtils;
import org.slf4j.Logger;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.mavenizor.maven.ArtifactBundleDistributor.DistributionHandler;

public abstract class AbstractDistributionHandler implements DistributionHandler {
   final private Logger log;

   public AbstractDistributionHandler(Logger log) {
      this.log = log;
   }

   public void distribute(Artifact artifact, boolean forceOverwrite) {
      if (forceOverwrite || !existsInTarget(artifact)) {
         doDistribute(artifact);
      }
      else {
         log.info("Skipped distribution of " + artifact + ". It already exists in target.");
      }
   }

   protected Logger getLog() {
      return log;
   }

   protected abstract void doDistribute(Artifact artifact);

   protected final boolean existsInTarget(Artifact artifact) {
      final String remoteChecksum = getTargetChecksum(artifact);
      if (remoteChecksum == null) {
         return false;
      }

      final String localChecksum = getLocalChecksum(artifact);
      if (!localChecksum.equals(remoteChecksum)) {
         if (ArtifactUtils.isSnapshot(artifact.getVersion())) {
            getLog().info("Target SNAPSHOT artifact " + artifact + " differs from local artifact.");
            return false;
         }
         else {
            getLog().warn("Target artifact " + artifact + " exists, but with diffrent checksum.");
         }
      }

      return true;
   }

   protected abstract String getLocalChecksum(Artifact artifact);

   protected abstract String getTargetChecksum(Artifact artifact);

   protected static String calc(final File targetFile, String algo) {
      try {
         final Map<String, Object> checksums = ChecksumUtils.calc(targetFile, Collections.singleton(algo));

         final Object checksum = checksums.values().iterator().next();
         if (checksum instanceof Throwable) {
            throw new IllegalStateException((Throwable) checksum);
         }

         return (String) checksum;
      }
      catch (IOException e) {
         throw Exceptions.pipe(e);
      }
   }
}
