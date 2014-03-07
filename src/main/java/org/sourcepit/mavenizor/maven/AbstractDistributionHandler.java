/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.slf4j.Logger;
import org.eclipse.aether.util.ChecksumUtils;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.mavenizor.maven.ArtifactBundleDistributor.DistributionHandler;

public abstract class AbstractDistributionHandler implements DistributionHandler
{
   final private Logger log;

   public AbstractDistributionHandler(Logger log)
   {
      this.log = log;
   }

   public void distribute(Artifact artifact, boolean forceOverwrite)
   {
      if (forceOverwrite || !existsInTarget(artifact))
      {
         doDistribute(artifact);
      }
      else
      {
         log.info("Skipped distribution of " + artifact + ". It already exists in target.");
      }
   }

   protected Logger getLog()
   {
      return log;
   }

   protected abstract void doDistribute(Artifact artifact);

   protected final boolean existsInTarget(Artifact artifact)
   {
      final String remoteChecksum = getTargetChecksum(artifact);
      if (remoteChecksum == null)
      {
         return false;
      }

      final String localChecksum = getLocalChecksum(artifact);
      if (!localChecksum.equals(remoteChecksum))
      {
         if (ArtifactUtils.isSnapshot(artifact.getVersion()))
         {
            getLog().info("Target SNAPSHOT artifact " + artifact + " differs from local artifact.");
            return false;
         }
         else
         {
            getLog().warn("Target artifact " + artifact + " exists, but with diffrent checksum.");
         }
      }

      return true;
   }

   protected abstract String getLocalChecksum(Artifact artifact);

   protected abstract String getTargetChecksum(Artifact artifact);

   protected static String calc(final File targetFile, String algo)
   {
      try
      {
         final Map<String, Object> checksums = ChecksumUtils.calc(targetFile, Collections.singleton(algo));

         final Object checksum = checksums.values().iterator().next();
         if (checksum instanceof Throwable)
         {
            throw new IllegalStateException((Throwable) checksum);
         }

         return (String) checksum;
      }
      catch (IOException e)
      {
         throw Exceptions.pipe(e);
      }
   }
}
