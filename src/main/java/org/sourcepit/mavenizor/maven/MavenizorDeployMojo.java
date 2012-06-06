/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import java.util.Map;

import javax.inject.Inject;

import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.sonatype.aether.impl.RemoteRepositoryManager;

/**
 * @requiresDependencyResolution test
 * @goal deploy-bundles
 * @phase deploy
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class MavenizorDeployMojo extends AbstractDistributingMavenizorMojo
{
   @Inject
   private RemoteRepositoryManager remoteRepositoryManager;

   @Inject
   private ArtifactDeployer deployer;

   @Inject
   private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

   /** @parameter expression="${altDeploymentRepository}" */
   private String altDeploymentRepository;

   @Override
   protected AbstractDistributionHandler getDistributionHandler()
   {
      final ArtifactRepository localRepository = getLocalRepository();
      final ArtifactRepository deploymentRepository = getDeploymentRepository();
      return new DeploymentHandler(logger, remoteRepositoryManager, session.getRepositorySession(), deployer,
         localRepository, deploymentRepository);
   }

   private ArtifactRepository getDeploymentRepository()
   {
      if (altDeploymentRepository == null)
      {
         return project.getDistributionManagementArtifactRepository();
      }
      else
      {
         final String[] segments = altDeploymentRepository.split("::");
         if (segments.length != 3)
         {
            throw new IllegalArgumentException("Invalid repository declaration " + altDeploymentRepository
               + ". Expected format: id::layout::url");
         }
         final ArtifactRepositoryLayout repositoryLayout = repositoryLayouts.get(segments[1]);
         return repositorySystem.createArtifactRepository(segments[0], segments[2], repositoryLayout,
            new ArtifactRepositoryPolicy(), new ArtifactRepositoryPolicy());
      }
   }
}
