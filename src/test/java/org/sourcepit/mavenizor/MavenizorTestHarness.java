/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sourcepit.common.utils.io.IO.buffIn;
import static org.sourcepit.common.utils.io.IO.buffOut;
import static org.sourcepit.common.utils.io.IO.fileIn;
import static org.sourcepit.common.utils.io.IO.fileOut;
import static org.sourcepit.common.utils.io.IO.jarOut;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.hamcrest.core.Is;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.sourcepit.common.manifest.Manifest;
import org.sourcepit.common.manifest.ManifestFactory;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.manifest.osgi.BundleManifestFactory;
import org.sourcepit.common.manifest.osgi.BundleRequirement;
import org.sourcepit.common.manifest.osgi.ClassPathEntry;
import org.sourcepit.common.manifest.osgi.PackageExport;
import org.sourcepit.common.manifest.osgi.PackageImport;
import org.sourcepit.common.manifest.osgi.Parameter;
import org.sourcepit.common.manifest.osgi.ParameterType;
import org.sourcepit.common.manifest.osgi.Version;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.common.manifest.osgi.resource.BundleManifestResourceImpl;
import org.sourcepit.common.manifest.resource.ManifestResource;
import org.sourcepit.common.manifest.resource.ManifestResourceImpl;
import org.sourcepit.common.maven.model.ArtifactConflictKey;
import org.sourcepit.common.maven.model.ProjectKey;
import org.sourcepit.common.utils.file.FileUtils;
import org.sourcepit.common.utils.file.FileVisitor;
import org.sourcepit.common.utils.io.IOOperation;
import org.sourcepit.common.utils.io.factories.JarOutputStreamHandle;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.common.utils.path.PathUtils;
import org.sourcepit.common.utils.props.LinkedPropertiesMap;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.common.utils.xml.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class MavenizorTestHarness
{
   private MavenizorTestHarness()
   {
      super();
   }

   public static BundleDescription newBundleDescription(String symbolicName)
   {
      return newBundleDescription(symbolicName, "1.0.0.qualifier");
   }

   public static BundleDescription newBundleDescription(String symbolicName, String version)
   {
      final BundleDescription bundle = mock(BundleDescription.class);
      when(bundle.getSymbolicName()).thenReturn(symbolicName);
      if (version != null)
      {
         when(bundle.getVersion()).thenReturn(new org.osgi.framework.Version(version));
      }
      return bundle;
   }

   public static BundleManifest newManifest(String symbolicName, String version)
   {
      final BundleManifest manifest = BundleManifestFactory.eINSTANCE.createBundleManifest();
      manifest.setBundleSymbolicName(symbolicName);
      manifest.setBundleVersion(version);
      return manifest;
   }

   public static File newBundle(File bundlesDir, BundleManifest manifest)
   {
      final File bundleDir = new File(bundlesDir, manifest.getBundleSymbolicName().getSymbolicName() + "_"
         + manifest.getBundleVersion());
      final URI uri = URI.createFileURI(new File(bundleDir, "/META-INF/MANIFEST.MF").getAbsolutePath());
      final Resource eResource = new BundleManifestResourceImpl(uri);
      eResource.getContents().add(manifest);
      try
      {
         eResource.save(null);
      }
      catch (Exception e)
      {
         throw Exceptions.pipe(e);
      }
      return bundleDir;
   }

   public static State newState(File bundlesDir, BundleManifest... manifests)
   {
      final StateObjectFactory stateFactory = StateObjectFactory.defaultFactory;
      final State state = stateFactory.createState(true);

      for (BundleManifest manifest : manifests)
      {
         addBundleDescription(bundlesDir, state, manifest);
      }
      state.resolve();
      return state;
   }

   private static BundleDescription addBundleDescription(File bundlesDir, State state, BundleManifest manifest)
   {
      final String symbolicName = manifest.getBundleSymbolicName().getSymbolicName();
      final Version bundleVersion = manifest.getBundleVersion();

      final String location;

      final File bundleDir = new File(bundlesDir, symbolicName + "_" + bundleVersion);
      if (!bundleDir.exists())
      {
         location = new File(bundleDir.getPath() + ".jar").getAbsolutePath();
      }
      else
      {
         location = bundleDir.getAbsolutePath();
      }

      final StateObjectFactory stateFactory = state.getFactory();
      final Dictionary<String, String> headers = toDictionary(manifest);
      try
      {

         final BundleDescription bundle = stateFactory.createBundleDescription(state, headers, location,
            state.getHighestBundleId() + 1L);
         state.addBundle(bundle);
         return bundle;
      }
      catch (BundleException e)
      {
         throw Exceptions.pipe(e);
      }
   }

   public static BundleDescription getBundle(State state, String symbolicName)
   {
      final BundleDescription[] bundles = state.getBundles(symbolicName);
      assertThat(bundles.length, Is.is(1));
      return bundles[0];
   }

   public static Dictionary<String, String> toDictionary(final BundleManifest manifest)
   {
      final Dictionary<String, String> headers = new Hashtable<String, String>(manifest.getHeaders().size());
      for (Entry<String, String> header : manifest.getHeaders())
      {
         headers.put(header.getKey(), header.getValue());
      }
      return headers;
   }

   public static void addBundleRequirement(BundleManifest bundle, String symbolicName, String versionRange)
   {
      addBundleRequirement(bundle, symbolicName, versionRange, false);
   }

   public static void addBundleRequirement(BundleManifest bundle, String symbolicName, String versionRange,
      boolean optional)
   {
      final BundleRequirement bundleRequirement = BundleManifestFactory.eINSTANCE.createBundleRequirement();
      bundleRequirement.getSymbolicNames().add(symbolicName);
      if (versionRange != null)
      {
         bundleRequirement.setBundleVersion(VersionRange.parse(versionRange));
      }
      if (optional)
      {
         final Parameter parameter = BundleManifestFactory.eINSTANCE.createParameter();
         parameter.setName(Constants.RESOLUTION_DIRECTIVE);
         parameter.setValue(Constants.RESOLUTION_OPTIONAL);
         parameter.setType(ParameterType.DIRECTIVE);
         bundleRequirement.getParameters().add(parameter);
      }
      bundle.getRequireBundle(true).add(bundleRequirement);
   }

   public static void addPackageExport(BundleManifest bundle, String packageName, String version)
   {
      final PackageExport packageExport = BundleManifestFactory.eINSTANCE.createPackageExport();
      packageExport.getPackageNames().add(packageName);
      if (version != null)
      {
         packageExport.setVersion(Version.parse(version));
      }
      bundle.getExportPackage(true).add(packageExport);
   }

   public static void addPackageImport(BundleManifest bundle, String packageName, String versionRange)
   {
      addPackageImport(bundle, packageName, versionRange, false);
   }

   public static void addPackageImport(BundleManifest bundle, String packageName, String versionRange, boolean optional)
   {
      final PackageImport packageImport = BundleManifestFactory.eINSTANCE.createPackageImport();
      packageImport.getPackageNames().add(packageName);
      if (versionRange != null)
      {
         packageImport.setVersion(VersionRange.parse(versionRange));
      }
      if (optional)
      {
         final Parameter parameter = BundleManifestFactory.eINSTANCE.createParameter();
         parameter.setName(Constants.RESOLUTION_DIRECTIVE);
         parameter.setValue(Constants.RESOLUTION_OPTIONAL);
         parameter.setType(ParameterType.DIRECTIVE);
         packageImport.getParameters().add(parameter);
      }
      bundle.getImportPackage(true).add(packageImport);
   }

   public static void addEmbeddedLibrary(File bundlesDir, BundleManifest manifest, String libEntry)
   {
      addEmbeddedLibrary(bundlesDir, manifest, libEntry, (ProjectKey[]) null);
   }

   public static void addEmbeddedLibrary(File bundlesDir, BundleManifest manifest, String libEntry,
      final ProjectKey... gavs)
   {
      if (!".".equals(libEntry))
      {
         new IOOperation<JarOutputStream>(jarOut(buffOut(fileOut(bundlesDir, libEntry, true))))
         {
            @Override
            protected void run(JarOutputStream jarOut) throws IOException
            {
               JarEntry e = new JarEntry(JarFile.MANIFEST_NAME);
               jarOut.putNextEntry(e);

               Manifest manifest = ManifestFactory.eINSTANCE.createManifest();
               ManifestResource resource = new ManifestResourceImpl();
               resource.getContents().add(manifest);
               resource.save(jarOut, null);

               jarOut.closeEntry();

               if (gavs != null)
               {
                  for (ProjectKey gav : gavs)
                  {
                     PropertiesMap pomProps = toPomProperties(gav);

                     e = new JarEntry(toPomPropertiesPath(gav.getArtifactConflictKey()));
                     jarOut.putNextEntry(e);

                     pomProps.store(jarOut);

                     jarOut.closeEntry();

                     Document doc = toPomXml(gav);

                     e = new JarEntry(toPomXmlPath(gav.getArtifactConflictKey()));
                     jarOut.putNextEntry(e);
                     XmlUtils.writeXml(doc, jarOut);
                     jarOut.closeEntry();
                  }
               }
            }
         }.run();
      }

      final ClassPathEntry cpEntry = BundleManifestFactory.eINSTANCE.createClassPathEntry();
      cpEntry.getPaths().add(libEntry);
      manifest.getBundleClassPath(true).add(cpEntry);
      try
      {
         manifest.eResource().save(null);
      }
      catch (IOException e)
      {
         throw Exceptions.pipe(e);
      }
   }

   public static File jar(final File dir)
   {
      final File jarFile = new File(dir.getAbsolutePath() + ".jar");

      final JarOutputStreamHandle jarResource = jarOut(buffOut(fileOut(jarFile, true)));
      new IOOperation<JarOutputStream>(jarResource)
      {
         @Override
         protected void run(final JarOutputStream jarOut) throws IOException
         {
            final File mfFile = new File(dir, JarFile.MANIFEST_NAME);
            if (mfFile.exists())
            {
               appendFile(jarOut, mfFile, JarFile.MANIFEST_NAME);
            }

            FileUtils.accept(dir, new FileVisitor()
            {
               public boolean visit(File file)
               {
                  if (file != dir && !file.equals(mfFile))
                  {
                     final String path = PathUtils.getRelativePath(file, dir, "/");
                     try
                     {
                        appendFile(jarOut, file, path);
                     }
                     catch (IOException e)
                     {
                        throw Exceptions.pipe(e);
                     }
                  }
                  return true;
               }
            });
         }

         private void appendFile(final JarOutputStream jarOut, File file, String name) throws IOException
         {
            if (file.isDirectory())
            {
               final String dirName = name.endsWith("/") ? name : name + "/";
               final JarEntry entry = new JarEntry(dirName);
               jarOut.putNextEntry(entry);
               jarOut.closeEntry();
            }
            else
            {
               final JarEntry entry = new JarEntry(name);
               jarOut.putNextEntry(entry);
               new IOOperation<InputStream>(buffIn(fileIn(file)))
               {
                  @Override
                  protected void run(InputStream in) throws IOException
                  {
                     IOUtils.copy(in, jarOut);
                  }
               }.run();
               jarOut.closeEntry();
            }
         }

      }.run();

      return jarFile;
   }

   public static void addMavenMetaData(File bundleDir, final ProjectKey gav)
   {
      new IOOperation<OutputStream>(
         buffOut(fileOut(bundleDir, toPomPropertiesPath(gav.getArtifactConflictKey()), true)))
      {
         @Override
         protected void run(OutputStream outputStream) throws IOException
         {
            toPomProperties(gav).store(outputStream);
         }
      }.run();

      new IOOperation<OutputStream>(buffOut(fileOut(bundleDir, toPomXmlPath(gav.getArtifactConflictKey()), true)))
      {
         @Override
         protected void run(OutputStream outputStream) throws IOException
         {
            XmlUtils.writeXml(toPomXml(gav), outputStream);
         }
      }.run();
   }

   private static String toPomPropertiesPath(final ArtifactConflictKey ga)
   {
      return "META-INF/maven/" + ga.getGroupId() + "/" + ga.getArtifactId() + "/pom.properties";
   }

   private static String toPomXmlPath(final ArtifactConflictKey ga)
   {
      return "META-INF/maven/" + ga.getGroupId() + "/" + ga.getArtifactId() + "/pom.xml";
   }

   private static PropertiesMap toPomProperties(final ProjectKey gav)
   {
      final PropertiesMap pomProps = new LinkedPropertiesMap();
      pomProps.put("groupId", gav.getGroupId());
      pomProps.put("artifactId", gav.getArtifactId());
      pomProps.put("version", gav.getVersion());
      return pomProps;
   }

   private static Document toPomXml(final ProjectKey gav)
   {
      Document doc = XmlUtils.newDocument();

      Element projectElem = doc.createElement("project");
      doc.appendChild(projectElem);

      Element modelVersionElem = doc.createElement("modelVersion");
      modelVersionElem.setTextContent("4.0.0");
      projectElem.appendChild(modelVersionElem);

      Element groupIdElem = doc.createElement("groupId");
      groupIdElem.setTextContent(gav.getGroupId());
      projectElem.appendChild(groupIdElem);

      Element artifactIdElem = doc.createElement("artifactId");
      artifactIdElem.setTextContent(gav.getArtifactId());
      projectElem.appendChild(artifactIdElem);

      Element versionElem = doc.createElement("version");
      versionElem.setTextContent(gav.getVersion());
      projectElem.appendChild(versionElem);
      return doc;
   }
}
