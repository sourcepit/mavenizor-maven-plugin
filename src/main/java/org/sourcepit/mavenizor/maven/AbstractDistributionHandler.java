/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import org.apache.maven.artifact.Artifact;
import org.slf4j.Logger;
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

   protected abstract void doDistribute(Artifact artifact);

   protected abstract boolean existsInTarget(Artifact artifact);
}
