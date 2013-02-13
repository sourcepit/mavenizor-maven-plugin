/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;


import javax.inject.Inject;

import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;

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
      return new InstallationHandler(logger, installer, localRepository);
   }
}
