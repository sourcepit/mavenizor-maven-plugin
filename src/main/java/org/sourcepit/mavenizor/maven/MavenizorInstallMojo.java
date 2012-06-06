/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import java.io.File;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.sourcepit.common.utils.lang.Exceptions;

/**
 * @requiresDependencyResolution test
 * @goal install-bundles
 * @phase install
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class MavenizorInstallMojo extends AbstractDistributingMavenizorMojo
{
   @Inject
   private ArtifactInstaller installer;

   @Override
   protected AbstractDistributionHandler getDistributionHandler()
   {
      final ArtifactRepository localRepository = getLocalRepository();
      return new AbstractDistributionHandler(logger)
      {
         @Override
         protected void doDistribute(Artifact artifact)
         {
            try
            {
               installer.install(artifact.getFile(), artifact, localRepository);
            }
            catch (ArtifactInstallationException e)
            {
               throw Exceptions.pipe(e);
            }
         }

         @Override
         protected boolean existsInTarget(Artifact artifact)
         {
            return new File(localRepository.getBasedir(), localRepository.pathOf(artifact)).exists();
         }
      };
   }
}
