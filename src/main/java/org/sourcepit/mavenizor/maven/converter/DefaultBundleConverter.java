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
import static org.sourcepit.mavenizor.maven.converter.ConvertionDirective.AUTO_DETECT;
import static org.sourcepit.mavenizor.maven.converter.ConvertionDirective.IGNORE;
import static org.sourcepit.mavenizor.maven.converter.ConvertionDirective.MAVENIZE;
import static org.sourcepit.mavenizor.maven.converter.ConvertionDirective.OMIT;
import static org.sourcepit.mavenizor.maven.converter.ConvertionDirective.REPLACE;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.sourcepit.common.utils.file.FileUtils;
import org.sourcepit.common.utils.file.FileVisitor;
import org.sourcepit.common.utils.io.DualIOOperation;
import org.sourcepit.common.utils.io.IOOperation;
import org.sourcepit.common.utils.io.IOResource;
import org.sourcepit.common.utils.lang.PipedIOException;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.common.utils.path.PathUtils;
import org.sourcepit.common.utils.props.LinkedPropertiesMap;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.common.utils.xml.XmlUtils;
import org.sourcepit.mavenizor.Mavenizor.TargetType;
import org.sourcepit.mavenizor.state.BundleAdapterFactory;
import org.w3c.dom.Document;

@Named
public class DefaultBundleConverter implements BundleConverter
{
   private static final Set<String> SUPPORTED_PACKAGINGS = new HashSet<String>();
   static
   {
      SUPPORTED_PACKAGINGS.add("jar");
   }

   private final Logger log;

   @Inject
   public DefaultBundleConverter(Logger log)
   {
      this.log = log;
   }

   private ConvertedArtifact newConvertedArtifact(MavenArtifact mavenArtifact, ConvertionDirective directive,
      boolean embeddedLibrary)
   {
      return new ConvertedArtifact(mavenArtifact, directive, embeddedLibrary);
   }

   public Result toMavenArtifacts(Request request)
   {
      final BundleDescription bundle = request.getBundle();
      final ConverterAction bundleAction = determineLibraryAction(bundle, null, request.getOptions());
      switch (bundleAction.getDirective())
      {
         case IGNORE :
            return caseIgnore(bundle);
         case REPLACE :
            return caseReplace(bundle, bundleAction.getReplacement());
         case AUTO_DETECT :
            return caseAutoDetect(request);
         case OMIT :
            return caseOmit(request);
         case MAVENIZE :
            return caseMavenize(request);
         default :
            throw new IllegalStateException();
      }
   }

   private Result caseOmit(Request request)
   {
      final BundleDescription bundle = request.getBundle();
      log.info(bundle + " (omitted)");

      final Result result = new Result(bundle, OMIT);
      final List<Path> libEntries = getEmbeddedLibEntries(bundle);
      libEntries.remove(new Path("."));
      if (!libEntries.isEmpty())
      {
         processEmbeddedLibraries(request, libEntries, result);
      }
      return result;
   }

   private Result caseMavenize(Request request)
   {
      final BundleDescription bundle = request.getBundle();

      final List<Path> libEntries = getEmbeddedLibEntries(bundle);

      final boolean hasEmbeddedArtifacts = libEntries.size() > 0;
      final boolean hasDotOnCP = libEntries.remove(new Path("."));

      final boolean autoOmit = request.getTargetType() == TargetType.JAVA && hasEmbeddedArtifacts && !hasDotOnCP;

      Result result;
      if (autoOmit)
      {
         log.info(bundle + " (auto omitted)");
         result = new Result(bundle, OMIT);
      }
      else
      {
         final MavenArtifact mainArtifact = toMainMavenArtifact(bundle, request.getGAVStrategy());
         log.info(bundle + " -> " + toArtifactKey(mainArtifact) + " (mavenized)");
         result = new Result(bundle, MAVENIZE);
         result.getConvertedArtifacts().add(newConvertedArtifact(mainArtifact, MAVENIZE, false));
      }
      if (hasEmbeddedArtifacts)
      {
         processEmbeddedLibraries(request, libEntries, result);
      }
      return result;
   }

   private Result caseAutoDetect(Request request)
   {
      final BundleDescription bundle = request.getBundle();
      final MavenArtifact artifact = detectMavenArtifact(bundle);
      if (artifact != null)
      {
         log.info(bundle + " -> " + toArtifactKey(artifact) + " (detected)");
         final Result result = new Result(bundle, AUTO_DETECT);
         result.getConvertedArtifacts().add(newConvertedArtifact(artifact, AUTO_DETECT, false));
         return result;
      }
      return caseMavenize(request); // fallback
   }

   private Result caseIgnore(final BundleDescription bundle)
   {
      log.info(bundle + " (ignored)");
      return new Result(bundle, IGNORE);
   }

   private Result caseReplace(final BundleDescription bundle, MavenArtifact replacement)
   {
      log.info(bundle + " -> " + toArtifactKey(replacement) + " (mapped)");
      final Result result = new Result(bundle, REPLACE);
      result.getConvertedArtifacts().add(newConvertedArtifact(replacement, REPLACE, false));
      return result;
   }

   private MavenArtifact toMainMavenArtifact(BundleDescription bundle, GAVStrategy converter)
   {
      final MavenArtifact artifact = MavenModelFactory.eINSTANCE.createMavenArtifact();
      artifact.setGroupId(converter.deriveGroupId(bundle));
      artifact.setArtifactId(converter.deriveArtifactId(bundle));
      artifact.setVersion(converter.deriveMavenVersion(bundle));
      artifact.setFile(getBundleLocation(bundle));

      return artifact;
   }

   private void processEmbeddedLibraries(Request request, List<Path> libEntries, Result result)
   {
      final TargetType targetType = request.getTargetType();
      switch (targetType)
      {
         case OSGI :
            log.info("Detected embedded libraries in " + getBundleLocation(request.getBundle()));
            break;
         case JAVA :
            for (Path libEntry : libEntries)
            {
               processEmbeddedLibrary(request, libEntry, result);
            }
            break;
         default :
            throw new IllegalStateException("Unsupported target type " + targetType);
      }
   }

   private void processEmbeddedLibrary(Request request, Path libEntry, Result result)
   {
      final BundleDescription bundle = request.getBundle();
      final ConverterAction libAction = determineLibraryAction(bundle, libEntry, request.getOptions());
      final ConvertionDirective directive = libAction.getDirective();
      switch (directive)
      {
         case OMIT :
         case IGNORE :
            log.info(bundle + "/" + libEntry + " (ignored)");
            break;
         case AUTO_DETECT :
         case MAVENIZE :
            final boolean autoDetect = directive == AUTO_DETECT;
            mavenizeEmbeddedLibrary(request, libEntry, autoDetect, result);
            break;
         case REPLACE :
            final MavenArtifact replacement = libAction.getReplacement();
            log.info(bundle + "/" + libEntry + " -> " + toArtifactKey(replacement) + " (mapped)");
            result.getConvertedArtifacts().add(newConvertedArtifact(replacement, directive, true));
            break;
         default :
            throw new IllegalStateException();
      }
   }

   private void mavenizeEmbeddedLibrary(Request request, Path libEntry, boolean autoDetect, Result result)
   {
      final BundleDescription bundle = request.getBundle();
      final File bundleLocation = getBundleLocation(bundle);

      final File workingDir = request.getWorkingDirectory();
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
               log.info(bundle + "/" + libEntry + " -> " + toArtifactKey(artifact) + " (detected)");
               result.getConvertedArtifacts().add(newConvertedArtifact(artifact, AUTO_DETECT, true));
            }
         }
         else
         {
            final GAVStrategy gav = request.getGAVStrategy();

            final MavenArtifact artifact = MavenModelFactory.eINSTANCE.createMavenArtifact();
            artifact.setGroupId(gav.deriveGroupId(bundle));
            artifact.setArtifactId(gav.deriveArtifactId(bundle, libEntry));
            artifact.setVersion(gav.deriveMavenVersion(bundle));
            artifact.setFile(libFile);

            log.info(bundle + "/" + libEntry + " -> " + toArtifactKey(artifact) + " (mavenized)");
            result.getConvertedArtifacts().add(newConvertedArtifact(artifact, MAVENIZE, true));
         }
      }
      else
      {
         result.getMissingEmbeddedLibraries().add(libEntry);
      }
   }

   private static MavenArtifact detectMavenArtifact(BundleDescription bundle)
   {
      final PropertiesMap pomProperties = loadPomProperties(bundle);
      return toMavenArtifact(pomProperties, getBundleLocation(bundle));
   }

   private static MavenArtifact detectMavenArtifact(final File libFile)
   {
      final PropertiesMap pomProperties = loadPomProperties(libFile);
      return toMavenArtifact(pomProperties, libFile);
   }

   private static MavenArtifact toMavenArtifact(final PropertiesMap pomProperties, final File artifactFile)
   {
      if (pomProperties.isEmpty())
      {
         return null;
      }

      final MavenArtifact artifact = MavenModelFactory.eINSTANCE.createMavenArtifact();
      artifact.setGroupId(pomProperties.get("groupId"));
      artifact.setArtifactId(pomProperties.get("artifactId"));
      artifact.setVersion(pomProperties.get("version"));
      artifact.setFile(artifactFile);

      final String packaging = determineMavenPackaging(artifact);
      if (SUPPORTED_PACKAGINGS.contains(packaging))
      {
         return artifact;
      }
      return null;
   }

   private static String determineMavenPackaging(final MavenArtifact artifact)
   {
      final String[] packaging = new String[1];
      final String pomPath = "META-INF/maven/" + artifact.getGroupId() + "/" + artifact.getArtifactId() + "/pom.xml";
      IOOperation<InputStream> ioop = new IOOperation<InputStream>(osgiIn(artifact.getFile(), pomPath))
      {
         @Override
         protected void run(InputStream inputStream) throws IOException
         {
            Document document = XmlUtils.readXml(inputStream);
            String result = XmlUtils.queryText(document, "/project/packaging");
            packaging[0] = "".equals(result) ? null : result;
         }
      };
      try
      {
         ioop.run();
      }
      catch (PipedIOException e)
      {
         if (e.adapt(FileNotFoundException.class) != null)
         {
            return null;
         }
         throw e;
      }
      return packaging[0] == null ? "jar" : packaging[0];
   }

   private static PropertiesMap loadPomProperties(BundleDescription bundle)
   {
      final File bundleLocation = getBundleLocation(bundle);
      if (bundleLocation.isDirectory())
      {
         final PropertiesMap pomProperties = new LinkedPropertiesMap();
         FileUtils.accept(new File(bundleLocation, "META-INF/maven"), new FileVisitor()
         {
            public boolean visit(File file)
            {
               final Path path = new Path(PathUtils.getRelativePath(file, bundleLocation, "/"));
               if (isPomPropertiesPath(path))
               {
                  pomProperties.load(file);
               }
               return true;
            }
         });
         return pomProperties;
      }
      else
      {
         return loadPomProperties(bundleLocation);
      }
   }

   private static PropertiesMap loadPomProperties(final File libFile)
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
               return isPomPropertiesPath(new Path(zipEntry.getName()));
            }
            return false;
         }
      }.run();
      return pomProperties;
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

   private static boolean isPomPropertiesPath(final Path path)
   {
      final String pathString = path.toString();
      if (pathString.startsWith("META-INF/maven/") && pathString.endsWith("/pom.properties"))
      {
         if (path.getSegments().size() == 5)
         {
            return true;
         }
      }
      return false;
   }

   private ConverterAction determineLibraryAction(BundleDescription bundle, Path libEntry, PropertiesMap options)
   {
      final String libActionProperty;
      if (libEntry == null)
      {
         libActionProperty = determineBundleLibraryActionProperty(bundle, options);
      }
      else
      {
         libActionProperty = determineEmbeddedLibraryActionProperty(bundle, libEntry, options);
      }

      final ConverterAction libAction;

      if (AUTO_DETECT.literal().equals(libActionProperty))
      {
         libAction = new ConverterAction(AUTO_DETECT, null);
      }
      else if (OMIT.literal().equals(libActionProperty))
      {
         libAction = new ConverterAction(OMIT, null);
      }
      else if (IGNORE.literal().equals(libActionProperty))
      {
         libAction = new ConverterAction(IGNORE, null);
      }
      else if (MAVENIZE.literal().equals(libActionProperty))
      {
         libAction = new ConverterAction(MAVENIZE, null);
      }
      else
      {
         final MavenArtifact replacement = parseArtifactKey(libActionProperty);
         libAction = new ConverterAction(REPLACE, replacement);
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

   private String determineBundleLibraryActionProperty(BundleDescription bundle, PropertiesMap options)
   {
      String libActionProperty = options.get(bundle.getSymbolicName() + "_" + bundle.getVersion());
      if (libActionProperty == null)
      {
         libActionProperty = options.get(bundle.getSymbolicName());
         if (libActionProperty == null)
         {
            return AUTO_DETECT.literal();
         }
      }
      return libActionProperty;
   }


}