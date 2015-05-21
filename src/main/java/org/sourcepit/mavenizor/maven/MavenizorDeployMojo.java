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
import org.sourcepit.common.maven.core.MavenProjectUtils;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
@Mojo(name = "deploy-bundles", defaultPhase = LifecyclePhase.DEPLOY, requiresDependencyResolution = ResolutionScope.TEST)
public class MavenizorDeployMojo extends AbstractDistributingMavenizorMojo {
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
   protected AbstractDistributionHandler getDistributionHandler() {
      final ArtifactRepository localRepository = getLocalRepository();
      final ArtifactRepository deploymentRepository = getAlternateDeploymentRepository();
      final ArtifactRepository snapshotRepository;
      final ArtifactRepository releaseRepository;
      if (deploymentRepository == null) {
         snapshotRepository = getSnapshotRepository();
         releaseRepository = getReleaseRepository();
      }
      else {
         snapshotRepository = deploymentRepository;
         releaseRepository = deploymentRepository;
      }
      return new DeploymentHandler(logger, repositoryConnectorProvider, session.getRepositorySession(), deployer,
         localRepository, snapshotRepository, releaseRepository, metadataResolver);
   }

   protected ArtifactRepository getSnapshotRepository() {
      return MavenProjectUtils.getSnapshotArtifactRepository(project);
   }

   protected ArtifactRepository getReleaseRepository() {
      return MavenProjectUtils.getReleaseArtifactRepository(project);
   }

   protected ArtifactRepository getAlternateDeploymentRepository() {
      if (altDeploymentRepository == null) {
         return null;
      }
      else {
         final String[] segments = altDeploymentRepository.split("::");
         if (segments.length != 3) {
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
