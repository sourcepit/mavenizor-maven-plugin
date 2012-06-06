/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import static org.sourcepit.mavenizor.Mavenizor.Result.markAsMavenized;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.Artifact;
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
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.maven.model.MavenModelFactory;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.maven.converter.BundleConverter;
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

   @Inject
   private OptionsHelper optionsHelper;

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
            mavenize(request.getTargetType(), bundle, gavStrategy, options, result);
         }
      }

      processSourceBundles(state, result);

      return result;
   }

   private void mavenize(TargetType targetType, BundleDescription bundle, GAVStrategy gavStrategy,
      PropertiesMap options, Result result)
   {
      if (result.getBundleToMavenArtifactsMap().containsKey(bundle) || result.getSourceBundles().contains(bundle))
      {
         return;
      }

      if (isEclipseSourceBundle(bundle))
      {
         result.getSourceBundles().add(bundle);
      }
      else
      {
         log.info("Mavenizing " + bundle);

         final BundleConverter.Result converterResult = bundleConverter.toMavenArtifacts(targetType, bundle,
            gavStrategy, options);

         result.getBundleToBundleConverterResultMap().put(bundle, converterResult);

         for (Path libEntry : converterResult.getMissingEmbeddedLibraries())
         {
            log.warn("Library " + libEntry + " not found in " + BundleAdapterFactory.DEFAULT.adapt(bundle, File.class));
         }

         for (Path libEntry : converterResult.getUnhandledEmbeddedLibraries())
         {
            log.warn("Unknown embedded library. Introduce it via property '" + bundle.getSymbolicName() + "[_"
               + bundle.getVersion() + "]/" + libEntry
               + " = mavenize | ignore | auto_detect | <groupId>:<artifactId>:<type>[:<classifier>]:<version>'");
         }

         final Collection<MavenArtifact> artifacts = converterResult.getMavenArtifacts();
         result.getBundleToMavenArtifactsMap().put(bundle, artifacts);
         for (MavenArtifact artifact : artifacts)
         {
            result.getArtifactBundle(artifact, true).getArtifacts().add(artifact);
         }

         addDependencies(targetType, bundle, gavStrategy, options, result);
      }
   }

   private void addDependencies(TargetType targetType, BundleDescription bundle, GAVStrategy gavStrategy,
      PropertiesMap options, Result result)
   {
      final Collection<MavenArtifact> artifacts = result.getBundleToMavenArtifactsMap().get(bundle);

      List<Dependency> embeddedDependencies = null;
      List<Dependency> dependencies = null;
      for (MavenArtifact artifact : artifacts)
      {
         final ArtifactBundle artifactBundle = result.getArtifactBundle(artifact, false);

         // create pom stub
         Model pom = artifactBundle.getPom();
         if (pom == null)
         {
            pom = new Model();
            pom.setModelVersion("4.0.0");
            pom.setGroupId(artifact.getGroupId());
            pom.setArtifactId(artifact.getArtifactId());
            pom.setVersion(artifact.getVersion());
            artifactBundle.setPom(pom);
         }

         if (Result.isMavenized(artifact))
         {
            if (!Result.isEmbeddedArtifact(artifact)) // is main bundle
            {
               // add dependencies to embedded bundles
               if (embeddedDependencies == null)
               {
                  embeddedDependencies = determineEmbeddedDependencies(bundle, options, result);
               }
               pom.getDependencies().addAll(embeddedDependencies);
            }

            // add normal dependencies
            if (dependencies == null)
            {
               dependencies = determineDependencies(targetType, bundle, gavStrategy, options, result);
            }
            pom.getDependencies().addAll(dependencies);
         }
      }
   }

   private List<Dependency> determineEmbeddedDependencies(BundleDescription bundle, PropertiesMap options, Result result)
   {
      final List<Dependency> embeddedDependencies = new ArrayList<Dependency>();
      for (MavenArtifact embeddedArtifact : result.getEmbeddedArtifacts(bundle))
      {
         final Dependency dependency = new Dependency();
         dependency.setGroupId(embeddedArtifact.getGroupId());
         dependency.setArtifactId(embeddedArtifact.getArtifactId());
         dependency.setVersion(embeddedArtifact.getVersion());
         if (optionsHelper.getBooleanValue(bundle, options, "@embeddedLibraries.provided", false))
         {
            dependency.setScope(Artifact.SCOPE_PROVIDED);
         }
         if (optionsHelper.getBooleanValue(bundle, options, "@embeddedLibraries.optional", false))
         {
            dependency.setOptional(true);
         }
         if (embeddedArtifact.getClassifier() != null)
         {
            dependency.setClassifier(embeddedArtifact.getClassifier());
         }
         if (!"jar".equals(embeddedArtifact.getType()))
         {
            dependency.setType(embeddedArtifact.getType());
         }
         embeddedDependencies.add(dependency);
      }
      return embeddedDependencies;
   }

   private List<Dependency> determineDependencies(TargetType targetType, BundleDescription bundle,
      GAVStrategy converter, PropertiesMap options, Result result)
   {
      final List<Dependency> dependencies = new ArrayList<Dependency>();

      final Collection<Requirement> requirements = requirementsCollector.collectRequirements(bundle);
      for (Requirement requirement : requirements)
      {
         final BundleDescription requiredBundle = requirement.getTo();

         Collection<MavenArtifact> requiredArtifacts = result.getBundleToMavenArtifactsMap().get(requiredBundle);
         if (requiredArtifacts == null)
         {
            // The bundle has not been mavenized... do it!
            mavenize(targetType, requiredBundle, converter, options, result);
            requiredArtifacts = result.getBundleToMavenArtifactsMap().get(requiredBundle);
         }

         for (MavenArtifact requiredArtifact : requiredArtifacts)
         {
            final Dependency dependency = new Dependency();
            dependency.setGroupId(requiredArtifact.getGroupId());
            dependency.setArtifactId(requiredArtifact.getArtifactId());
            if (requirement.isOptional())
            {
               dependency.setOptional(true);
            }

            final Version requiredVersion = Version.parse(requiredBundle.getVersion().toString());
            VersionRange versionRange = requirement.getVersionRange();
            if (versionRange == null || VersionRange.INFINITE_RANGE.equals(versionRange)
               || !versionRange.includes(requiredVersion))
            {
               versionRange = VersionRange.parse(requiredVersion.toString());
            }

            if (optionsHelper.isMatch(requirement, options, "@requirements.provided"))
            {
               dependency.setScope(Artifact.SCOPE_PROVIDED);
            }

            if (optionsHelper.isMatch(requirement, options, "@requirements.optional"))
            {
               dependency.setOptional(true);
            }

            dependency.setVersion(converter.deriveMavenVersionRange(requiredBundle, versionRange));

            dependencies.add(dependency);
         }
      }

      return dependencies;
   }

   private boolean isEclipseSourceBundle(BundleDescription bundle)
   {
      final BundleManifest manifest = BundleAdapterFactory.DEFAULT.adapt(bundle, BundleManifest.class);
      return manifest.getHeaderValue("Eclipse-SourceBundle") != null;
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
            log.info("Attaching source " + sourceBundle + " to " + hostBundleName + "_" + version);

            final File sourceJar = BundleAdapterFactory.DEFAULT.adapt(sourceBundle, File.class);

            final Set<ArtifactBundle> artifactBundles = result.getArtifactBundles(hostBundle);
            for (ArtifactBundle artifactBundle : artifactBundles)
            {
               final Model pom = artifactBundle.getPom();

               final MavenArtifact sourceArtifact = MavenModelFactory.eINSTANCE.createMavenArtifact();
               sourceArtifact.setGroupId(pom.getGroupId());
               sourceArtifact.setArtifactId(pom.getArtifactId());
               sourceArtifact.setVersion(pom.getVersion());
               sourceArtifact.setClassifier("sources");
               sourceArtifact.setFile(sourceJar);

               markAsMavenized(sourceArtifact);

               artifactBundle.getArtifacts().add(sourceArtifact);
            }
         }
      }
   }
}
