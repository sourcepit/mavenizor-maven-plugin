/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.slf4j.Logger;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.maven.model.MavenModelFactory;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.maven.converter.BundleConverter;
import org.sourcepit.mavenizor.maven.converter.ConvertedArtifact;
import org.sourcepit.mavenizor.maven.converter.ConvertionDirective;
import org.sourcepit.mavenizor.maven.converter.GAVStrategy;
import org.sourcepit.mavenizor.maven.tycho.TychoSourceIUResolver;
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

      final Result result = new Result();

      for (BundleDescription bundle : state.getBundles())
      {
         if (isEclipseSourceBundle(bundle))
         {
            result.getSourceBundles().add(bundle);
         }
         else
         {
            if (inputFilter.accept(bundle))
            {
               result.getInputBundles().add(bundle);
               mavenize(request, bundle, result);
            }
         }
      }

      processSourceBundles(state, result, request.getSourceJarResolver());

      return result;
   }

   private void mavenize(Request request, BundleDescription bundle, Result result)
   {
      if (result.getConverterResult(bundle) != null || result.getSourceBundles().contains(bundle))
      {
         return;
      }

      if (isEclipseSourceBundle(bundle))
      {
         throw new IllegalStateException();
      }
      else
      {
         final BundleConverter.Request converterRequest = new BundleConverter.Request();
         converterRequest.setBundle(bundle);
         converterRequest.setTargetType(request.getTargetType());
         converterRequest.setGAVStrategy(request.getGAVStrategy());
         converterRequest.setOptions(request.getOptions());
         converterRequest.setWorkingDirectory(request.getWorkingDirectory());

         final BundleConverter.Result converterResult = bundleConverter.toMavenArtifacts(converterRequest);

         Mavenizor.Result.addConverterResult(result, converterResult);

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

         addDependencies(request, bundle, result);
      }
   }

   private void addDependencies(Request request, BundleDescription bundle, Result result)
   {
      final Collection<ConvertedArtifact> artifacts = result.getConvertedArtifacts(bundle);

      List<Dependency> embeddedDependencies = null;
      List<Dependency> dependencies = null;

      final ConvertedArtifact mainArtifact = determineMainArtifact(artifacts);

      for (ConvertedArtifact convertedArtifact : artifacts)
      {
         final ArtifactBundle artifactBundle = result.getArtifactBundle(convertedArtifact);
         final MavenArtifact artifact = convertedArtifact.getMavenArtifact();

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


         if (convertedArtifact.isMavenized())
         {
            if (mainArtifact.equals(convertedArtifact)) // is main bundle
            {
               // add dependencies to embedded bundles
               if (embeddedDependencies == null)
               {
                  embeddedDependencies = determineEmbeddedDependencies(bundle, mainArtifact, request.getOptions(),
                     result);
               }
               pom.getDependencies().addAll(embeddedDependencies);
            }

            // add normal dependencies
            if (dependencies == null)
            {
               dependencies = determineDependencies(request, bundle, result);
            }
            pom.getDependencies().addAll(dependencies);
         }
      }
   }

   private ConvertedArtifact determineMainArtifact(final Collection<ConvertedArtifact> artifacts)
   {
      final List<ConvertedArtifact> mavenized = new ArrayList<ConvertedArtifact>();
      for (ConvertedArtifact convertedArtifact : artifacts)
      {
         if (convertedArtifact.isMavenized())
         {
            mavenized.add(convertedArtifact);
         }
      }

      Collections.sort(mavenized, new Comparator<ConvertedArtifact>()
      {
         public int compare(ConvertedArtifact o1, ConvertedArtifact o2)
         {
            final int l1 = o1.getMavenArtifact().getFile().getParent().length();
            final int l2 = o2.getMavenArtifact().getFile().getParent().length();
            return l1 - l2;
         }
      });

      return mavenized.isEmpty() ? null : mavenized.get(0);
   }

   private List<Dependency> determineEmbeddedDependencies(BundleDescription bundle, ConvertedArtifact mainArtifact,
      PropertiesMap options, Result result)
   {
      final List<Dependency> embeddedDependencies = new ArrayList<Dependency>();
      for (ConvertedArtifact convertedArtifact : result.getConvertedArtifacts(bundle))
      {
         if (convertedArtifact.isEmbeddedLibrary() && !convertedArtifact.equals(mainArtifact))
         {
            final MavenArtifact embeddedArtifact = convertedArtifact.getMavenArtifact();

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
      }
      return embeddedDependencies;
   }

   private List<Dependency> determineDependencies(Request request, BundleDescription bundle, Result result)
   {
      final PropertiesMap options = request.getOptions();

      final List<Dependency> dependencies = new ArrayList<Dependency>();

      final Collection<Requirement> requirements = requirementsCollector.collectRequirements(bundle);
      for (Requirement requirement : requirements)
      {
         if (optionsHelper.isMatch(requirement, options, "@requirements.erase", false))
         {
            log.info("Omitting requirement from " + requirement.getFrom() + " to " + requirement.getTo());
            continue;
         }

         if (!optionsHelper.isMatch(requirement, options, "@requirements.permited", true))
         {
            throw new IllegalStateException("Requirement from " + requirement.getFrom() + " to " + requirement.getTo()
               + " is not permitted.");
         }

         final BundleDescription requiredBundle = requirement.getTo();
         final BundleConverter.Result converterResult = convertOnDemand(request, requiredBundle, result);

         final boolean omitMainArtifacts = converterResult.getConvertionDirective() == ConvertionDirective.OMIT;

         for (ConvertedArtifact requiredArtifact : converterResult.getConvertedArtifacts())
         {
            if ((omitMainArtifacts && requiredArtifact.isEmbeddedLibrary())
               || (!omitMainArtifacts && !requiredArtifact.isEmbeddedLibrary()))
            {
               final MavenArtifact mavenArtifact = requiredArtifact.getMavenArtifact();
               final Dependency dependency = new Dependency();
               dependency.setGroupId(mavenArtifact.getGroupId());
               dependency.setArtifactId(mavenArtifact.getArtifactId());
               if (requirement.isOptional())
               {
                  dependency.setOptional(true);
               }

               final boolean allowVersionRanges = options.getBoolean("allowVersionRanges", false);
               if (allowVersionRanges)
               {
                  dependency.setVersion(deriveMavenVersionRange(requirement, requiredArtifact.getMavenArtifact(),
                     request.getGAVStrategy()));
               }
               else
               {
                  dependency.setVersion(requiredArtifact.getMavenArtifact().getVersion());
               }


               if (optionsHelper.isMatch(requirement, options, "@requirements.provided", false))
               {
                  dependency.setScope(Artifact.SCOPE_PROVIDED);
               }

               if (optionsHelper.isMatch(requirement, options, "@requirements.optional", false))
               {
                  dependency.setOptional(true);
               }

               dependencies.add(dependency);
            }
         }
      }

      return dependencies;
   }

   private String deriveMavenVersionRange(Requirement requirement, MavenArtifact requiredArtifact,
      GAVStrategy gavStrategy)
   {
      final BundleDescription requiredBundle = requirement.getTo();

      String mavenVersionRange;

      VersionRange versionRange = requirement.getVersionRange();
      if (versionRange == null || VersionRange.INFINITE_RANGE.equals(versionRange))
      {
         mavenVersionRange = requiredArtifact.getVersion();
      }
      else
      {
         mavenVersionRange = gavStrategy.deriveMavenVersionRange(requiredBundle, versionRange);
         if (!isIncluded(mavenVersionRange, requiredArtifact.getVersion()))
         {
            mavenVersionRange = requiredArtifact.getVersion();
         }
      }

      return mavenVersionRange;
   }

   private boolean isIncluded(String mavenVersionRange, String mavenVersion)
   {
      try
      {
         final org.apache.maven.artifact.versioning.VersionRange range = org.apache.maven.artifact.versioning.VersionRange
            .createFromVersionSpec(mavenVersionRange);
         final ArtifactVersion version = new DefaultArtifactVersion(mavenVersion);
         if (range.hasRestrictions())
         {
            return range.containsVersion(version);
         }
         return range.getRecommendedVersion().equals(version);
      }
      catch (InvalidVersionSpecificationException e)
      {
         throw Exceptions.pipe(e);
      }
   }

   private BundleConverter.Result convertOnDemand(Request request, BundleDescription bundle, Result result)
   {
      BundleConverter.Result converterResult = result.getConverterResult(bundle);
      if (converterResult == null)
      {
         // The bundle has not been mavenized... do it!
         mavenize(request, bundle, result);
         converterResult = result.getConverterResult(bundle);
      }
      return converterResult;
   }

   private boolean isEclipseSourceBundle(BundleDescription bundle)
   {
      final BundleManifest manifest = BundleAdapterFactory.DEFAULT.adapt(bundle, BundleManifest.class);
      return manifest.getHeaderValue("Eclipse-SourceBundle") != null || bundle.getSymbolicName().endsWith(".source");
   }

   private void processSourceBundles(final State state, final Result result, SourceJarResolver sourceJarResolver)
   {
      final Map<BundleDescription, BundleDescription> hostToSourceBundleMap = new HashMap<BundleDescription, BundleDescription>();
      for (BundleDescription sourceBundle : result.getSourceBundles())
      {
         final BundleManifest manifest = BundleAdapterFactory.DEFAULT.adapt(sourceBundle, BundleManifest.class);

         String[] targetIdAndVersion = TychoSourceIUResolver.getTargetIdAndVersion(manifest);
         if (targetIdAndVersion == null)
         {
            targetIdAndVersion = TychoSourceIUResolver.getTargetIdAndVersion(sourceBundle.getSymbolicName(),
               sourceBundle.getVersion().toString());
         }

         final String hostBundleName = targetIdAndVersion[0];
         final String version = targetIdAndVersion[1];

         final BundleDescription hostBundle = state.getBundle(hostBundleName, new org.osgi.framework.Version(version));
         if (hostBundle != null)
         {
            hostToSourceBundleMap.put(hostBundle, sourceBundle);
         }
      }

      final Set<BundleDescription> hostBundles = new LinkedHashSet<BundleDescription>();
      for (ArtifactBundle artifactBundle : result.getArtifactBundles())
      {
         for (BundleDescription bundle : result.getBundles(artifactBundle))
         {
            hostBundles.add(bundle);
         }
      }

      for (BundleDescription hostBundle : hostBundles)
      {
         final File sourceJar;

         final BundleDescription sourceBundle = hostToSourceBundleMap.get(hostBundle);
         if (sourceBundle != null)
         {
            sourceJar = BundleAdapterFactory.DEFAULT.adapt(sourceBundle, File.class);
         }
         else if (sourceJarResolver != null)
         {
            sourceJar = sourceJarResolver.resolveSource(hostBundle);
         }
         else
         {
            sourceJar = null;
         }

         if (sourceJar != null)
         {
            final List<ArtifactBundle> artifactBundles = result.getArtifactBundles(hostBundle);

            for (ArtifactBundle artifactBundle : artifactBundles)
            {
               if (hasMavenizedArtifact(artifactBundle))
               {
                  final Model pom = artifactBundle.getPom();
                  log.info("Attaching source " + sourceJar + " to " + pom);

                  final MavenArtifact sourceArtifact = MavenModelFactory.eINSTANCE.createMavenArtifact();
                  sourceArtifact.setGroupId(pom.getGroupId());
                  sourceArtifact.setArtifactId(pom.getArtifactId());
                  sourceArtifact.setVersion(pom.getVersion());
                  sourceArtifact.setClassifier("sources");
                  sourceArtifact.setType("java-source");
                  sourceArtifact.setFile(sourceJar);

                  artifactBundle.getArtifacts().add(
                     new ConvertedArtifact(sourceArtifact, ConvertionDirective.MAVENIZE, false));
               }
            }
         }
      }
   }

   private boolean hasMavenizedArtifact(ArtifactBundle artifactBundle)
   {
      for (ConvertedArtifact artifact : artifactBundle.getArtifacts())
      {
         if (artifact.isMavenized())
         {
            return true;
         }
      }
      return false;
   }
}
