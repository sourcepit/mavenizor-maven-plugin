/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.io.File;
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
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.manifest.osgi.BundleSymbolicName;
import org.sourcepit.common.manifest.osgi.Version;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.common.manifest.osgi.parser.BundleHeaderParser;
import org.sourcepit.common.manifest.osgi.parser.BundleHeaderParserImpl;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.maven.ArtifactDescription;
import org.sourcepit.mavenizor.maven.BundleConverter;
import org.sourcepit.mavenizor.maven.converter.GAVStrategy;
import org.sourcepit.mavenizor.state.BundleAdapterFactory;
import org.sourcepit.mavenizor.state.Requirement;
import org.sourcepit.mavenizor.state.RequirementsCollector;

@Named
public class DefaultMavenizor implements Mavenizor
{
   @Inject
   private Logger log;

   @Inject
   private RequirementsCollector requirementsCollector;

   @Inject
   private BundleConverter bundleConverter;

   public Result mavenize(Request request)
   {
      final State state = request.getState();

      final BundleFilter inputFilter = request.getInputFilter() == null ? BundleFilter.ACCEPT_ALL : request
         .getInputFilter();

      final GAVStrategy gavStrategy = request.getGAVStrategy();

      final PropertiesMap options = request.getOptions();

      final Result result = new Result();

      for (BundleDescription bundle : state.getBundles())
      {
         if (inputFilter.accept(bundle))
         {
            result.getInputBundles().add(bundle);
            mavenize(bundle, gavStrategy, options, result);
         }
      }

      processSourceBundles(state, result);

      return result;
   }

   private void mavenize(BundleDescription bundle, GAVStrategy converter, PropertiesMap options, Result result)
   {
      if (isEclipseSourceBundle(bundle))
      {
         result.getSourceBundles().add(bundle);
      }
      else
      {
         log.info("Mavenizing " + bundle);
         final List<Dependency> dependencies = new ArrayList<Dependency>();
         processDependenciesRecursive(bundle, converter, dependencies, options, result);
         final Collection<ArtifactDescription> descriptors = bundleConverter.toMavenArtifacts(bundle, dependencies,
            converter, options);
         result.getArtifactDescriptors().put(bundle, descriptors);
      }
   }

   private boolean isEclipseSourceBundle(BundleDescription bundle)
   {
      final BundleManifest manifest = BundleAdapterFactory.DEFAULT.adapt(bundle, BundleManifest.class);
      return manifest.getHeaderValue("Eclipse-SourceBundle") != null;
   }

   private void processDependenciesRecursive(BundleDescription bundle, GAVStrategy converter,
      List<Dependency> dependencies, PropertiesMap options, Result result)
   {
      final Collection<Requirement> requirements = requirementsCollector.collectRequirements(bundle);
      for (Requirement requirement : requirements)
      {
         final BundleDescription requiredBundle = requirement.getTo();

         Collection<ArtifactDescription> descriptors = result.getArtifactDescriptors().get(requiredBundle);
         if (descriptors == null)
         {
            mavenize(requiredBundle, converter, options, result);
            descriptors = result.getArtifactDescriptors().get(requiredBundle);
         }

         for (ArtifactDescription descriptor : descriptors)
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

   private void processSourceBundles(final State state, final Result result)
   {
      for (BundleDescription sourceBundle : result.getSourceBundles())
      {
         final BundleManifest manifest = BundleAdapterFactory.DEFAULT.adapt(sourceBundle, BundleManifest.class);

         final BundleHeaderParser parser = new BundleHeaderParserImpl();

         final BundleSymbolicName symbolicName = parser.parseBundleSymbolicName(manifest
            .getHeaderValue("Eclipse-SourceBundle"));

         final String hostBundleName = symbolicName.getSymbolicName();
         final String version = symbolicName.getParameterValue("version");

         final BundleDescription hostBundle = state.getBundle(hostBundleName, new org.osgi.framework.Version(version));
         if (hostBundle == null)
         {
            log.warn("Skipping source bundle " + sourceBundle + ". Unable to find target " + hostBundleName + "_"
               + version);
         }
         else
         {
            final Collection<ArtifactDescription> descriptors = result.getArtifactDescriptors().get(hostBundle);
            if (descriptors != null && !descriptors.isEmpty())
            {
               final File sourceJar = BundleAdapterFactory.DEFAULT.adapt(sourceBundle, File.class);
               log.info("Attaching source " + sourceBundle + " to " + hostBundleName + "_" + version);
               for (ArtifactDescription artifactDescriptor : descriptors)
               {
                  artifactDescriptor.getClassifierToFile().put("sources", sourceJar);
               }
            }
         }
      }
   }
}
