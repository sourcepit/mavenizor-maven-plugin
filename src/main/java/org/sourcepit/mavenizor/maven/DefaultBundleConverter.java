/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import static org.sourcepit.common.utils.io.IOResources.buffOut;
import static org.sourcepit.common.utils.io.IOResources.fileOut;
import static org.sourcepit.common.utils.io.IOResources.osgiIn;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.EList;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.slf4j.Logger;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.manifest.osgi.ClassPathEntry;
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.maven.model.MavenModelFactory;
import org.sourcepit.common.utils.io.DualIOOperation;
import org.sourcepit.common.utils.io.IOResource;
import org.sourcepit.common.utils.lang.PipedIOException;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.maven.converter.GAVStrategy;
import org.sourcepit.mavenizor.state.BundleAdapterFactory;

@Named
public class DefaultBundleConverter implements BundleConverter
{
   private final Logger log;

   @Inject
   public DefaultBundleConverter(Logger log)
   {
      this.log = log;
   }

   public Collection<MavenArtifact> toMavenArtifacts(BundleDescription bundle, GAVStrategy gavStrategy,
      PropertiesMap options)
   {
      // allow re-write
      final List<MavenArtifact> replacement = replaceWithExistingMavenArtifacts(bundle, gavStrategy, options);
      if (replacement != null)
      {
         return replacement;
      }

      final List<MavenArtifact> artifacts = new ArrayList<MavenArtifact>();
      unwrapEmbeddedLibraries(bundle, gavStrategy, options, artifacts);

      final boolean hasEmbeddedArtifacts = artifacts.size() > 0;
      final boolean omit = hasEmbeddedArtifacts ? isOmitMainBundle(bundle, options) : false;
      if (omit)
      {
         log.info("Omitting enclosing bundle");
      }
      else
      {
         final MavenArtifact mainArtifact = toMainMavenArtifact(bundle, gavStrategy);
         artifacts.add(0, mainArtifact);
      }
      return artifacts;
   }

   private MavenArtifact toMainMavenArtifact(BundleDescription bundle, GAVStrategy converter)
   {
      final MavenArtifact artifact = MavenModelFactory.eINSTANCE.createMavenArtifact();
      artifact.setGroupId(converter.deriveGroupId(bundle));
      artifact.setArtifactId(converter.deriveArtifactId(bundle));
      artifact.setVersion(converter.deriveMavenVersion(bundle));
      artifact.setFile(getBundleLocation(bundle));

      markAsMavenized(artifact);

      return artifact;
   }

   private void unwrapEmbeddedLibraries(BundleDescription bundle, GAVStrategy converter, PropertiesMap options,
      final List<MavenArtifact> embeddedArtifacts)
   {
      final List<Path> libEntries = getEmbeddedLibEntries(bundle);
      libEntries.remove(new Path("."));

      final boolean hasLibEntries = libEntries.size() > 0;
      if (hasLibEntries)
      {
         final boolean unwrap = isUnwrapEmbeddedLibraries(bundle, options);
         if (unwrap)
         {
            log.info("Processing embedded libraries " + libEntries);
            final File workingDir = getWorkingDir(options);
            final File bundleWorkingDir = new File(workingDir, bundle.toString());
            for (Path libEntry : libEntries)
            {
               unwrapEmbeddedArtifacts(bundleWorkingDir, bundle, libEntry, converter, options, embeddedArtifacts);
            }
         }
         else
         {
            log.warn("Detected embedded libraries in " + getBundleLocation(bundle));
         }
      }

   }

   private void unwrapEmbeddedArtifacts(File bundleWorkingDir, BundleDescription bundle, Path libEntry,
      GAVStrategy converter, PropertiesMap options, final List<MavenArtifact> embeddedArtifacts)
   {
      final File bundleLocation = getBundleLocation(bundle);

      final File libFile = new File(bundleWorkingDir, libEntry.toString());
      if (copyEmbeddedLib(bundleLocation, libEntry, libFile))
      {
         final List<MavenArtifact> existingArtifacts = replaceWithExistingMavenArtifacts(bundle, libEntry, libFile,
            options);
         if (existingArtifacts == null)
         {
            final MavenArtifact artifact = MavenModelFactory.eINSTANCE.createMavenArtifact();
            artifact.setGroupId(converter.deriveGroupId(bundle));
            artifact.setArtifactId(libEntry.getFileName());
            artifact.setVersion(converter.deriveMavenVersion(bundle));
            artifact.setFile(libFile);

            markAsMavenized(artifact);
            markAsEmbeddedArtifact(artifact);

            embeddedArtifacts.add(artifact);
         }
         else
         {
            embeddedArtifacts.addAll(existingArtifacts);
         }
      }
      else
      {
         log.warn("Library " + libEntry + " not found in " + bundleLocation);
      }
   }

   private static void markAsMavenized(final MavenArtifact artifact)
   {
      artifact.getAnnotation("mavenizor", true).setData("mavenized", true);
   }

   private static void markAsEmbeddedArtifact(final MavenArtifact artifact)
   {
      artifact.getAnnotation("mavenizor", true).setData("embeddedArtifact", true);
   }

   private static File getBundleLocation(BundleDescription bundle)
   {
      final File bundleLocation = BundleAdapterFactory.DEFAULT.adapt(bundle, File.class);
      if (bundleLocation == null)
      {
         throw new IllegalStateException("Unable to determine location for bundle " + bundle);
      }
      return bundleLocation;
   }

   private boolean copyEmbeddedLib(final File bundleLocation, Path libEntry, final File libCopyFile)
   {
      final IOResource<? extends InputStream> lib = osgiIn(bundleLocation, libEntry.toString());
      final IOResource<? extends OutputStream> copy = buffOut(fileOut(libCopyFile, true));
      try
      {
         new DualIOOperation<InputStream, OutputStream>(lib, copy)
         {
            @Override
            protected void run(InputStream input, OutputStream output) throws IOException
            {
               IOUtils.copy(input, output);
            }
         }.run();
         return true;
      }
      catch (PipedIOException e)
      {
         final FileNotFoundException fnfe = e.adapt(FileNotFoundException.class);
         if (fnfe != null)
         {
            return false;
         }
         else
         {
            throw e;
         }
      }
   }

   private static File getWorkingDir(Map<String, String> options)
   {
      final String path = options.get("workingDir");
      if (path == null)
      {
         throw new IllegalArgumentException("Option workingDir not set");
      }
      return new File(path);
   }

   private static List<Path> getEmbeddedLibEntries(BundleDescription bundle)
   {
      final BundleManifest manifest = BundleAdapterFactory.DEFAULT.adapt(bundle, BundleManifest.class);
      if (manifest == null)
      {
         throw new IllegalStateException("Unable to load bundle manifest for bundle " + bundle);
      }
      final List<Path> jarPaths = new ArrayList<Path>();
      final EList<ClassPathEntry> bundleCP = manifest.getBundleClassPath();
      if (bundleCP != null)
      {
         for (ClassPathEntry cpEntry : bundleCP)
         {
            EList<String> paths = cpEntry.getPaths();
            for (String path : paths)
            {
               jarPaths.add(new Path(path));
            }
         }
      }
      return jarPaths;
   }

   private List<MavenArtifact> replaceWithExistingMavenArtifacts(BundleDescription bundle, GAVStrategy gavStrategy,
      PropertiesMap options)
   {
      return null;
   }

   private boolean isOmitMainBundle(BundleDescription enclosingBundle, PropertiesMap options)
   {
      return options.getBoolean("omitEnclosingBundles", false);
   }

   private boolean isUnwrapEmbeddedLibraries(BundleDescription enclosingBundle, PropertiesMap options)
   {
      return options.getBoolean("unwrapEmbeddedLibraries", false);
   }

   private List<MavenArtifact> replaceWithExistingMavenArtifacts(BundleDescription bundle, Path libEntry, File libFile,
      PropertiesMap options)
   {
      return null;
   }
}
