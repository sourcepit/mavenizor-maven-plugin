/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.slf4j.Logger;
import org.sourcepit.common.manifest.osgi.Version;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.mavenizor.maven.ArtifactDescriptor;
import org.sourcepit.mavenizor.maven.ArtifactDescriptorsStrategy;
import org.sourcepit.mavenizor.maven.DefaultArtifactDescriptorsStrategy;
import org.sourcepit.mavenizor.maven.converter.Converter;
import org.sourcepit.mavenizor.state.Requirement;
import org.sourcepit.mavenizor.state.RequirementsCollector;

@Named
public class DefaultMavenizor implements Mavenizor
{
   @Inject
   private Logger log;

   @Inject
   private RequirementsCollector requirementsCollector;

   public Result mavenize(Request request)
   {
      final State state = request.getState();

      final BundleFilter inputFilter = request.getInputFilter() == null ? BundleFilter.ACCEPT_ALL : request
         .getInputFilter();

      final Converter converter = request.getConverter();

      final ArtifactDescriptorsStrategy descriptorsStrategy = request.getArtifactDescriptorsStrategy() == null
         ? new DefaultArtifactDescriptorsStrategy()
         : request.getArtifactDescriptorsStrategy();

      final Result result = new Result();

      for (BundleDescription bundle : state.getBundles())
      {
         if (inputFilter.accept(bundle))
         {
            log.info(bundle.toString());
            result.getInputBundles().add(bundle);
            mavenize(bundle, descriptorsStrategy, converter, result);
         }
      }

      return result;
   }

   private void mavenize(BundleDescription bundle, ArtifactDescriptorsStrategy descriptorsStrategy,
      Converter converter, Result result)
   {
      final List<Dependency> dependencies = new ArrayList<Dependency>();

      processDependenciesRecursive(bundle, descriptorsStrategy, converter, dependencies, result);

      final Collection<ArtifactDescriptor> descriptors = descriptorsStrategy.determineArtifactDescriptors(bundle,
         dependencies, converter);

      result.getArtifactDescriptors().put(bundle, descriptors);
   }

   private void processDependenciesRecursive(BundleDescription bundle, ArtifactDescriptorsStrategy descriptorsStrategy,
      Converter converter, List<Dependency> dependencies, Result result)
   {
      final Collection<Requirement> requirements = requirementsCollector.collectRequirements(bundle);
      for (Requirement requirement : requirements)
      {
         final BundleDescription requiredBundle = requirement.getTo();

         Collection<ArtifactDescriptor> descriptors = result.getArtifactDescriptors().get(requiredBundle);
         if (descriptors == null)
         {
            mavenize(requiredBundle, descriptorsStrategy, converter, result);
            descriptors = result.getArtifactDescriptors().get(requiredBundle);
         }

         for (ArtifactDescriptor descriptor : descriptors)
         {
            final Model model = descriptor.getModel();

            final Dependency dependency = new Dependency();
            dependency.setGroupId(model.getGroupId());
            dependency.setArtifactId(model.getArtifactId());
            dependency.setOptional(requirement.isOptional());

            final Version requiredVersion = Version.parse(requiredBundle.getVersion().toString());
            VersionRange versionRange = requirement.getVersionRange();
            if (versionRange == null || VersionRange.INFINITE_RANGE.equals(versionRange)
               || !versionRange.includes(requiredVersion))
            {
               versionRange = VersionRange.parse(requiredVersion.toString());
            }

            dependency.setVersion(converter.deriveMavenVersionRange(requiredBundle, versionRange));

            dependencies.add(dependency);
         }
      }
   }
}
