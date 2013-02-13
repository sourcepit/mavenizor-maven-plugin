/*
 * Copyright (C) 2013 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.mavenizor.maven;

import java.io.File;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.slf4j.Logger;
import org.sourcepit.common.utils.lang.Exceptions;

public class InstallationHandler extends AbstractDistributionHandler
{
   private final ArtifactInstaller installer;

   private final ArtifactRepository localRepository;

   public InstallationHandler(Logger log, ArtifactInstaller installer, ArtifactRepository localRepository)
   {
      super(log);
      this.localRepository = localRepository;
      this.installer = installer;
   }

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
   protected String getLocalChecksum(Artifact artifact)
   {
      return calc(artifact.getFile(), "SHA-1");
   }

   @Override
   protected String getTargetChecksum(Artifact artifact)
   {
      final String basedir = localRepository.getBasedir();
      final String path = localRepository.pathOf(artifact);
      final File targetFile = new File(basedir, path);
      return targetFile.exists() ? calc(targetFile, "SHA-1") : null;
   }
}