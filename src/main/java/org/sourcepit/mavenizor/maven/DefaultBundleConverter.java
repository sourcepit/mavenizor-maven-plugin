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
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.eclipse.emf.common.util.EList;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.slf4j.Logger;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.manifest.osgi.ClassPathEntry;
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

   public Collection<ArtifactDescription> toMavenArtifacts(BundleDescription bundle, List<Dependency> dependencies,
      GAVStrategy converter, PropertiesMap options)
   {
      // allow re-write
      final List<ArtifactDescription> replacement = replaceWithExistingMavenArtifacts(bundle);
      if (replacement != null)
      {
         return replacement;
      }

      // no re-write... so go further
      final List<ArtifactDescription> descriptors = new ArrayList<ArtifactDescription>();
      processEmbeddedLibraries(bundle, dependencies, converter, options, descriptors);

      final boolean hasUnwrappedLibs = descriptors.size() > 0;
      final boolean omit = hasUnwrappedLibs ? omitEnclosingBundle(bundle, options) : false;
      if (omit)
      {
         log.info("Omitting enclosing bundle");
      }
      else
      {
         processEnclosingBundle(bundle, dependencies, converter, descriptors);
      }
      return descriptors;
   }

   private void processEnclosingBundle(BundleDescription bundle, List<Dependency> dependencies, GAVStrategy converter,
      final List<ArtifactDescription> descriptors)
   {
      final Model model = new Model();
      model.setGroupId(converter.deriveGroupId(bundle));
      model.setArtifactId(converter.deriveArtifactId(bundle));
      model.setVersion(converter.deriveMavenVersion(bundle));

      for (ArtifactDescription embeddedLibDescriptor : descriptors)
      {
         final Dependency dependency = new Dependency();
         final Model embeddedLibModel = embeddedLibDescriptor.getModel();

         dependency.setGroupId(embeddedLibModel.getGroupId());
         dependency.setArtifactId(embeddedLibModel.getArtifactId());
         dependency.setVersion(embeddedLibModel.getVersion());

         model.getDependencies().add(dependency);
      }

      model.getDependencies().addAll(dependencies);

      final ArtifactDescription descriptor = new ArtifactDescription();
      descriptor.setModel(model);
      descriptor.setFile(getBundleLocation(bundle));
      descriptors.add(0, descriptor);
   }

   private void processEmbeddedLibraries(BundleDescription bundle, List<Dependency> dependencies,
      GAVStrategy converter, PropertiesMap options, final List<ArtifactDescription> descriptors)
   {
      final List<Path> libEntries = getEmbeddedLibEntries(bundle);
      libEntries.remove(new Path("."));

      final boolean hasLibEntries = libEntries.size() > 0;
      if (hasLibEntries)
      {
         final boolean unwrap = unwrapEmbeddedLibraries(bundle, options);
         if (unwrap)
         {
            log.info("Processing embedded libraries " + libEntries);
            final File workingDir = getWorkingDir(options);
            final File bundleWorkingDir = new File(workingDir, bundle.toString());
            for (Path libEntry : libEntries)
            {
               processEmbeddedLibrary(bundleWorkingDir, bundle, libEntry, dependencies, converter, descriptors);
            }
         }
         else
         {
            log.warn("Detected embedded libraries in " + getBundleLocation(bundle));
         }
      }

   }

   private void processEmbeddedLibrary(File bundleWorkingDir, BundleDescription bundle, Path libEntry,
      List<Dependency> dependencies, GAVStrategy converter, final List<ArtifactDescription> descriptors)
   {
      final File bundleLocation = getBundleLocation(bundle);

      final File libFile = new File(bundleWorkingDir, libEntry.toString());
      if (copyEmbeddedLib(bundleLocation, libEntry, libFile))
      {
         final List<ArtifactDescription> existingArtifacts = replaceWithExistingMavenArtifacts(bundle, libEntry,
            libFile);
         if (existingArtifacts == null)
         {
            final Model model = new Model();
            model.setGroupId(converter.deriveGroupId(bundle));
            model.setArtifactId(libEntry.getFileName());
            model.setVersion(converter.deriveMavenVersion(bundle));
            model.setDependencies(dependencies);

            final ArtifactDescription descriptor = new ArtifactDescription();
            descriptor.setModel(model);
            descriptor.setFile(libFile);
            descriptors.add(descriptor);
         }
         else
         {
            descriptors.addAll(existingArtifacts);
         }
      }
      else
      {
         log.warn("Library " + libEntry + " not found in " + bundleLocation);
      }
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


   private boolean omitEnclosingBundle(BundleDescription enclosingBundle, PropertiesMap options)
   {
      return options.getBoolean("omitEnclosingBundles", false);
   }

   private boolean unwrapEmbeddedLibraries(BundleDescription enclosingBundle, PropertiesMap options)
   {
      return options.getBoolean("unwrapEmbeddedLibraries", false);
   }

   private List<ArtifactDescription> replaceWithExistingMavenArtifacts(BundleDescription bundle)
   {
      return null;
   }

   private List<ArtifactDescription> replaceWithExistingMavenArtifacts(BundleDescription bundle, Path libEntry,
      File libFile)
   {
      return null;
   }
}
