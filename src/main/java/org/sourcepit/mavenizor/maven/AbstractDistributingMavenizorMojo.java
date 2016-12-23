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
import java.util.LinkedHashSet;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.repository.RepositorySystem;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.mavenizor.ArtifactBundle;
import org.sourcepit.mavenizor.Mavenizor.Result;
import org.sourcepit.mavenizor.maven.ArtifactBundleDistributor.DistributionHandler;
import org.sourcepit.mavenizor.state.BundleAdapterFactory;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public abstract class AbstractDistributingMavenizorMojo extends AbstractMavenizorMojo {
   @Inject
   private ArtifactBundleDistributor distributor;

   @Inject
   protected RepositorySystem repositorySystem;

   @Parameter(property = "localRepository")
   protected ArtifactRepository localRepository;

   @Parameter(property = "localRepositoryPath")
   protected File localRepositoryPath;

   @Parameter(property = "forceOverwrite", defaultValue = "false")
   protected boolean forceOverwrite;

   @Parameter(property = "forceOverwriteProjectBundles", defaultValue = "true")
   protected boolean forceOverwriteProjectBundles;

   @Override
   protected void processResult(Result result) {
      final DistributionHandler handler = getDistributionHandler();
      final Set<ArtifactBundle> scopeProject = new LinkedHashSet<ArtifactBundle>();
      final Set<ArtifactBundle> scopeDependency = new LinkedHashSet<ArtifactBundle>();
      for (ArtifactBundle artifactBundle : result.getArtifactBundles()) {
         if (isInProjectScope(result, artifactBundle)) {
            scopeProject.add(artifactBundle);
         }
         else {
            scopeDependency.add(artifactBundle);
         }
      }
      logger.info("Distributing project bundles...");
      for (ArtifactBundle artifactBundle : scopeProject) {
         distributor.distribute(workingDir, artifactBundle, handler, forceOverwriteProjectBundles);
      }
      logger.info("Distributing target platform bundles...");
      for (ArtifactBundle artifactBundle : scopeDependency) {
         distributor.distribute(workingDir, artifactBundle, handler, forceOverwrite);
      }
   }

   private boolean isInProjectScope(Result result, ArtifactBundle artifactBundle) {
      final Set<BundleDescription> bundles = result.getBundles(artifactBundle);
      for (BundleDescription bundle : bundles) {
         final File bundleLocation = BundleAdapterFactory.DEFAULT.adapt(bundle, File.class);
         if (getBundleLocationsInBuildScope().contains(bundleLocation)) {
            return true;
         }
      }
      return false;
   }

   protected abstract AbstractDistributionHandler getDistributionHandler();

   protected ArtifactRepository getLocalRepository() {
      if (localRepositoryPath == null) {
         return localRepository;
      }
      else {
         try {
            return repositorySystem.createLocalRepository(localRepositoryPath);
         }
         catch (InvalidRepositoryException e) {
            throw Exceptions.pipe(e);
         }
      }

   }
}
