/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.sourcepit.common.maven.util.MavenProjectUtils;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
@Mojo(name = "deploy-bundles", defaultPhase = LifecyclePhase.DEPLOY, requiresDependencyResolution = ResolutionScope.TEST)
public class MavenizorDeployMojo extends AbstractDistributingMavenizorMojo
{
   @Inject
   private RepositoryConnectorProvider repositoryConnectorProvider;

   @Inject
   private ArtifactDeployer deployer;

   @Inject
   private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

   @Parameter(property = "altDeploymentRepository")
   private String altDeploymentRepository;

   @Inject
   private MetadataResolver metadataResolver;

   @Override
   protected AbstractDistributionHandler getDistributionHandler()
   {
      final ArtifactRepository localRepository = getLocalRepository();
      final ArtifactRepository deploymentRepository = getAlternateDeploymentRepository();
      final ArtifactRepository snapshotRepository;
      final ArtifactRepository releaseRepository;
      if (deploymentRepository == null)
      {
         snapshotRepository = getSnapshotRepository();
         releaseRepository = getReleaseRepository();
      }
      else
      {
         snapshotRepository = deploymentRepository;
         releaseRepository = deploymentRepository;
      }
      return new DeploymentHandler(logger, repositoryConnectorProvider, session.getRepositorySession(), deployer,
         localRepository, snapshotRepository, releaseRepository, metadataResolver);
   }

   protected ArtifactRepository getSnapshotRepository()
   {
      return MavenProjectUtils.getSnapshotArtifactRepository(project);
   }

   protected ArtifactRepository getReleaseRepository()
   {
      return MavenProjectUtils.getReleaseArtifactRepository(project);
   }

   protected ArtifactRepository getAlternateDeploymentRepository()
   {
      if (altDeploymentRepository == null)
      {
         return null;
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
         final ArtifactRepository artifactRepository = repositorySystem.createArtifactRepository(segments[0],
            segments[2], repositoryLayout, new ArtifactRepositoryPolicy(), new ArtifactRepositoryPolicy());
         repositorySystem.injectAuthentication(session.getRepositorySession(),
            Collections.singletonList(artifactRepository));
         return artifactRepository;
      }
   }
}
