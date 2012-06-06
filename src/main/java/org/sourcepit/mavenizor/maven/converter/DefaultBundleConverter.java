/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import static org.sourcepit.common.maven.model.util.MavenModelUtils.parseArtifactKey;
import static org.sourcepit.common.maven.model.util.MavenModelUtils.toArtifactKey;
import static org.sourcepit.common.utils.io.IOResources.buffIn;
import static org.sourcepit.common.utils.io.IOResources.buffOut;
import static org.sourcepit.common.utils.io.IOResources.fileIn;
import static org.sourcepit.common.utils.io.IOResources.fileOut;
import static org.sourcepit.common.utils.io.IOResources.osgiIn;
import static org.sourcepit.common.utils.io.IOResources.zipIn;
import static org.sourcepit.mavenizor.Mavenizor.Result.markAsEmbeddedArtifact;
import static org.sourcepit.mavenizor.Mavenizor.Result.markAsMavenized;
import static org.sourcepit.mavenizor.maven.converter.EmbeddedLibraryAction.Action.AUTO_DETECT;
import static org.sourcepit.mavenizor.maven.converter.EmbeddedLibraryAction.Action.IGNORE;
import static org.sourcepit.mavenizor.maven.converter.EmbeddedLibraryAction.Action.MAVENIZE;
import static org.sourcepit.mavenizor.maven.converter.EmbeddedLibraryAction.Action.REPLACE;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
import org.sourcepit.common.utils.io.IOOperation;
import org.sourcepit.common.utils.io.IOResource;
import org.sourcepit.common.utils.lang.PipedIOException;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.common.utils.props.LinkedPropertiesMap;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.Mavenizor.TargetType;
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

   public Result toMavenArtifacts(TargetType mode, BundleDescription bundle, GAVStrategy gavStrategy,
      PropertiesMap options)
   {
      // allow re-write
      final List<MavenArtifact> replacement = replaceWithExistingMavenArtifacts(bundle, gavStrategy, options);
      if (replacement != null)
      {
         final Result result = new Result();
         result.getMavenArtifacts().addAll(replacement);
         return result;
      }

      final Result result = new Result();

      processEmbeddedLibraries(mode, bundle, gavStrategy, options, result);

      final boolean hasEmbeddedArtifacts = result.getMavenArtifacts().size() > 0;
      final boolean omit = hasEmbeddedArtifacts ? isOmitMainBundle(bundle, options) : false;
      if (omit)
      {
         log.info("Omitting enclosing bundle");
      }
      else
      {
         final MavenArtifact mainArtifact = toMainMavenArtifact(bundle, gavStrategy);
         result.getMavenArtifacts().add(0, mainArtifact);
      }

      return result;
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

   private void processEmbeddedLibraries(TargetType targetType, BundleDescription bundle, GAVStrategy converter,
      PropertiesMap options, Result result)
   {
      final List<Path> libEntries = getEmbeddedLibEntries(bundle);
      libEntries.remove(new Path("."));

      final boolean hasLibEntries = libEntries.size() > 0;
      if (hasLibEntries)
      {
         switch (targetType)
         {
            case OSGI :
               log.info("Detected embedded libraries in " + getBundleLocation(bundle));
               break;
            case JAVA :
               log.info("Processing embedded libraries...");
               for (Path libEntry : libEntries)
               {
                  processEmbeddedLibrary(bundle, libEntry, converter, options, result);
               }
               break;
            default :
               throw new IllegalStateException("Unsupported target type " + targetType);
         }
      }

   }

   private void processEmbeddedLibrary(BundleDescription bundle, Path libEntry, GAVStrategy converter,
      PropertiesMap options, Result result)
   {
      final EmbeddedLibraryAction libAction = determineEmbeddedLibraryAction(bundle, libEntry, options);
      switch (libAction.getAction())
      {
         case IGNORE :
            log.info(libEntry + " (ignored)");
            break;
         case AUTO_DETECT :
         case MAVENIZE :
            final boolean autoDetect = libAction.getAction() == AUTO_DETECT;
            mavenizeEmbeddedLibrary(bundle, libEntry, autoDetect, converter, options, result);
            break;
         case REPLACE :
            final MavenArtifact replacement = libAction.getReplacement();
            log.info(libEntry + " -> " + toArtifactKey(replacement) + " (mapped)");
            result.getMavenArtifacts().add(replacement);
            break;
         default :
            throw new IllegalStateException();
      }
   }

   private void mavenizeEmbeddedLibrary(BundleDescription bundle, Path libEntry, boolean autoDetect,
      GAVStrategy converter, PropertiesMap options, Result result)
   {
      final File bundleLocation = getBundleLocation(bundle);

      final File workingDir = getWorkingDir(options);
      final File bundleWorkingDir = new File(workingDir, bundle.toString());
      final File libFile = new File(bundleWorkingDir, libEntry.toString());

      if (copyEmbeddedLib(bundleLocation, libEntry, libFile))
      {
         if (autoDetect)
         {
            MavenArtifact artifact = detectMavenArtifact(libFile);
            if (artifact == null)
            {
               result.getUnhandledEmbeddedLibraries().add(libEntry);
            }
            else
            {
               log.info(libEntry + " -> " + toArtifactKey(artifact) + " (detected)");
               result.getMavenArtifacts().add(artifact);
            }
         }
         else
         {
            MavenArtifact artifact = MavenModelFactory.eINSTANCE.createMavenArtifact();
            artifact.setGroupId(converter.deriveGroupId(bundle));
            artifact.setArtifactId(converter.deriveArtifactId(bundle, libEntry));
            artifact.setVersion(converter.deriveMavenVersion(bundle));
            artifact.setFile(libFile);

            markAsMavenized(artifact);
            markAsEmbeddedArtifact(artifact);

            log.info(libEntry + " -> " + toArtifactKey(artifact) + " (mavenized)");

            result.getMavenArtifacts().add(artifact);
         }
      }
      else
      {
         result.getMissingEmbeddedLibraries().add(libEntry);
      }
   }

   private static MavenArtifact detectMavenArtifact(final File libFile)
   {
      final PropertiesMap pomProperties = new LinkedPropertiesMap();
      new IOOperation<ZipInputStream>(zipIn(buffIn(fileIn(libFile))))
      {
         @Override
         protected void run(ZipInputStream zipIn) throws IOException
         {
            ZipEntry zipEntry = zipIn.getNextEntry();
            while (zipEntry != null)
            {
               if (isPomPropertiesEntry(zipEntry))
               {
                  pomProperties.load(zipIn);
               }
               zipEntry = zipIn.getNextEntry();
            }
         }

         private boolean isPomPropertiesEntry(ZipEntry zipEntry)
         {
            if (!zipEntry.isDirectory())
            {
               final String name = zipEntry.getName();
               if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties"))
               {
                  final Path path = new Path(zipEntry.getName());
                  if (path.getSegments().size() == 5)
                  {
                     return true;
                  }
               }
            }
            return false;
         }
      }.run();

      if (pomProperties.isEmpty())
      {
         return null;
      }

      final MavenArtifact artifact = MavenModelFactory.eINSTANCE.createMavenArtifact();
      artifact.setGroupId(pomProperties.get("groupId"));
      artifact.setArtifactId(pomProperties.get("artifactId"));
      artifact.setVersion(pomProperties.get("version"));
      return artifact;
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
      return options.getBoolean("omitMainBundles", false);
   }

   private EmbeddedLibraryAction determineEmbeddedLibraryAction(BundleDescription bundle, Path libEntry,
      PropertiesMap options)
   {
      final String libActionProperty = determineEmbeddedLibraryActionProperty(bundle, libEntry, options);

      final EmbeddedLibraryAction libAction;

      if (AUTO_DETECT.literal().equals(libActionProperty))
      {
         libAction = new EmbeddedLibraryAction(AUTO_DETECT, null);
      }
      else if (IGNORE.literal().equals(libActionProperty))
      {
         libAction = new EmbeddedLibraryAction(IGNORE, null);
      }
      else if (MAVENIZE.literal().equals(libActionProperty))
      {
         libAction = new EmbeddedLibraryAction(MAVENIZE, null);
      }
      else
      {
         final MavenArtifact replacement = parseArtifactKey(libActionProperty);
         markAsEmbeddedArtifact(replacement);
         libAction = new EmbeddedLibraryAction(REPLACE, replacement);
      }

      return libAction;
   }

   private String determineEmbeddedLibraryActionProperty(BundleDescription bundle, Path libEntry, PropertiesMap options)
   {
      String libActionProperty = options.get(bundle.getSymbolicName() + "_" + bundle.getVersion() + "/" + libEntry);
      if (libActionProperty == null)
      {
         libActionProperty = options.get(bundle.getSymbolicName() + "/" + libEntry);
         if (libActionProperty == null)
         {
            return AUTO_DETECT.literal();
         }
      }
      return libActionProperty;
   }
}
