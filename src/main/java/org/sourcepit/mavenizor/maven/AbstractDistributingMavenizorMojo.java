/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
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
public abstract class AbstractDistributingMavenizorMojo extends AbstractMavenizorMojo
{
   @Inject
   private ArtifactBundleDistributor distributor;

   @Inject
   protected RepositorySystem repositorySystem;

   /** @parameter expression="${localRepository}" */
   protected ArtifactRepository localRepository;

   /** @parameter expression="${localRepositoryPath}" */
   protected File localRepositoryPath;

   /** @parameter expression="${forceOverwrite}" default-value=false */
   protected boolean forceOverwrite;

   @Override
   protected void processResult(Result result)
   {
      final DistributionHandler handler = getDistributionHandler();
      final Set<ArtifactBundle> scopeProject = new LinkedHashSet<ArtifactBundle>();
      final Set<ArtifactBundle> scopeDependency = new LinkedHashSet<ArtifactBundle>();
      for (ArtifactBundle artifactBundle : result.getArtifactBundles())
      {
         if (isInProjectScope(result, artifactBundle))
         {
            scopeProject.add(artifactBundle);
         }
         else
         {
            scopeDependency.add(artifactBundle);
         }
      }
      logger.info("Distributing project bundles...");
      for (ArtifactBundle artifactBundle : scopeProject)
      {
         distributor.distribute(workingDir, artifactBundle, handler, true);
      }
      logger.info("Distributing target platform bundles...");
      for (ArtifactBundle artifactBundle : scopeDependency)
      {
         distributor.distribute(workingDir, artifactBundle, handler, forceOverwrite);
      }
   }

   private boolean isInProjectScope(Result result, ArtifactBundle artifactBundle)
   {
      final Set<BundleDescription> bundles = result.getBundles(artifactBundle);
      for (BundleDescription bundle : bundles)
      {
         final File bundleLocation = BundleAdapterFactory.DEFAULT.adapt(bundle, File.class);
         if (getBundleLocationsInBuildScope().contains(bundleLocation))
         {
            return true;
         }
      }
      return false;
   }

   protected abstract AbstractDistributionHandler getDistributionHandler();

   protected ArtifactRepository getLocalRepository()
   {
      if (localRepositoryPath == null)
      {
         return localRepository;
      }
      else
      {
         try
         {
            return repositorySystem.createLocalRepository(localRepositoryPath);
         }
         catch (InvalidRepositoryException e)
         {
            throw Exceptions.pipe(e);
         }
      }

   }
}
