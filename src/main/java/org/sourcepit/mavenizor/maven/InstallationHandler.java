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