/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.Test;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.manifest.osgi.BundleManifestFactory;
import org.sourcepit.common.manifest.osgi.BundleRequirement;
import org.sourcepit.common.manifest.osgi.PackageExport;
import org.sourcepit.common.manifest.osgi.PackageImport;
import org.sourcepit.common.manifest.osgi.Parameter;
import org.sourcepit.common.manifest.osgi.ParameterType;
import org.sourcepit.common.manifest.osgi.Version;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.common.manifest.osgi.resource.BundleManifestResourceImpl;
import org.sourcepit.common.maven.testing.EmbeddedMavenEnvironmentTest;
import org.sourcepit.common.testing.Environment;
import org.sourcepit.common.utils.lang.Exceptions;

public class RequirementsCollectorTest extends EmbeddedMavenEnvironmentTest
{
   @Inject
   private RequirementsCollector collector;

   @Test
   public void test()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", "1");
      save(manifestA);

      BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", "[1,2)");
      save(manifestB);

      BundleManifest manifestC = createManifest("c", "3");
      addBundleRequirement(manifestC, "b", "[2,3)");
      save(manifestC);

      final State state = createState(manifestA, manifestB, manifestC);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");
      BundleDescription bundleC = getBundle(state, "c");

      assertThat(bundleB.getResolvedRequires().length, Is.is(0));
      ExportPackageDescription[] imports = bundleB.getResolvedImports();
      assertThat(imports.length, Is.is(1));
      ExportPackageDescription imported = imports[0];
      assertThat(imported.getExporter(), IsEqual.equalTo(bundleA));

      assertThat(bundleC.getResolvedImports().length, Is.is(0));
      BundleDescription[] resolvedRequires = bundleC.getResolvedRequires();
      assertThat(resolvedRequires.length, Is.is(1));
      BundleDescription resolvedRequired = resolvedRequires[0];
      assertThat(resolvedRequired, IsEqual.equalTo(bundleB));
   }

   @Test
   public void testPackageImport()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", null);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testPackageImportIsOptional()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", null, true);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(true));
   }

   @Test
   public void testPackageImportWithRange()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", "4");
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", "[2,6)");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("[2,6)")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testPackageImportWithIntersectedRange()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", "4");
      addPackageExport(manifestA, "package.b", "4");
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", "[2,6)");
      addPackageImport(manifestB, "package.b", "[1,5)");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("[2,5)")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testBundleRequirement()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addBundleRequirement(manifestB, "a", null);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testBundleRequirementIsOptional()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addBundleRequirement(manifestB, "a", null, true);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(true));
   }

   @Test
   public void testBundleRequirementWithRange()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addBundleRequirement(manifestB, "a", "[1,2)");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("[1,2)")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixed()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      save(manifestB);

      final BundleManifest manifestC = createManifest("c", "2");
      addPackageImport(manifestC, "package.a", null);
      addBundleRequirement(manifestC, "b", null);
      save(manifestC);

      final State state = createState(manifestA, manifestB, manifestC);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");
      BundleDescription bundleC = getBundle(state, "c");

      Collection<Requirement> requirements = collector.collectRequirements(bundleC);
      assertThat(requirements.size(), Is.is(2));

      final Iterator<Requirement> it = requirements.iterator();
      Requirement requirement = it.next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleC));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));

      requirement = it.next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleC));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundle()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", null);
      addBundleRequirement(manifestB, "a", null);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundleWithRange()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", "1");
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", null);
      addBundleRequirement(manifestB, "a", "[1,2)");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("[1,2)")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundleWithRange2()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", "1");
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", "[1,1.2)");
      addBundleRequirement(manifestB, "a", "[1,2)");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("[1,1.2)")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundleIsOptional()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", null, true);
      addBundleRequirement(manifestB, "a", null, false);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundleIsOptional2()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", null, false);
      addBundleRequirement(manifestB, "a", null, true);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundleIsOptional3()
   {
      final BundleManifest manifestA = createManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = createManifest("b", "2");
      addPackageImport(manifestB, "package.a", null, true);
      addBundleRequirement(manifestB, "a", null, true);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(true));
   }

   private static BundleManifest createManifest(String symbolicName, String version)
   {
      final BundleManifest manifest = BundleManifestFactory.eINSTANCE.createBundleManifest();
      manifest.setBundleSymbolicName(symbolicName);
      manifest.setBundleVersion(version);
      return manifest;
   }

   public static BundleDescription[] getDependentBundles(BundleDescription root)
   {
      if (root == null)
         return new BundleDescription[0];
      BundleDescription[] imported = getImportedBundles(root);
      BundleDescription[] required = getRequiredBundles(root);
      BundleDescription[] dependents = new BundleDescription[imported.length + required.length];
      System.arraycopy(imported, 0, dependents, 0, imported.length);
      System.arraycopy(required, 0, dependents, imported.length, required.length);
      return dependents;
   }

   public static BundleDescription[] getImportedBundles(BundleDescription root)
   {
      if (root == null)
         return new BundleDescription[0];
      ExportPackageDescription[] packages = getResolvedImports(root);
      ArrayList<BundleDescription> resolvedImports = new ArrayList<BundleDescription>(packages.length);
      for (int i = 0; i < packages.length; i++)
      {
         resolvedImports.add(packages[i].getExporter());
      }
      return (BundleDescription[]) resolvedImports.toArray(new BundleDescription[resolvedImports.size()]);
   }

   public static ExportPackageDescription[] getResolvedImports(BundleDescription root)
   {
      ExportPackageDescription[] packages = root.getResolvedImports();
      ArrayList<ExportPackageDescription> resolvedImports = new ArrayList<ExportPackageDescription>(packages.length);
      for (int i = 0; i < packages.length; i++)
      {
         if (!root.getLocation().equals(packages[i].getExporter().getLocation())
            && !resolvedImports.contains(packages[i].getExporter()))
         {

            resolvedImports.add(packages[i]);
         }
      }
      return (ExportPackageDescription[]) resolvedImports.toArray(new ExportPackageDescription[resolvedImports.size()]);
   }

   public static BundleDescription[] getRequiredBundles(BundleDescription root)
   {
      if (root == null)
         return new BundleDescription[0];
      return root.getResolvedRequires();
   }


   private static BundleDescription getBundle(State state, String symbolicName)
   {
      final BundleDescription[] bundles = state.getBundles(symbolicName);
      assertThat(bundles.length, Is.is(1));
      return bundles[0];
   }

   private State createState(BundleManifest... manifests)
   {
      final StateObjectFactory stateFactory = StateObjectFactory.defaultFactory;
      final State state = stateFactory.createState(true);

      for (BundleManifest manifest : manifests)
      {
         final Dictionary<String, String> headers = toDictionary(manifest);
         final String symbolicName = manifest.getBundleSymbolicName().getSymbolicName();
         final String location = new File(getWs().getRoot(), symbolicName).getAbsolutePath();
         try
         {
            final BundleDescription bundle = stateFactory.createBundleDescription(state, headers, location,
               state.getHighestBundleId() + 1L);
            state.addBundle(bundle);
         }
         catch (BundleException e)
         {
            throw Exceptions.pipe(e);
         }
      }

      return state;
   }

   private static Dictionary<String, String> toDictionary(final BundleManifest manifest)
   {
      final Dictionary<String, String> headers = new Hashtable<String, String>(manifest.getHeaders().size());
      for (Entry<String, String> header : manifest.getHeaders())
      {
         headers.put(header.getKey(), header.getValue());
      }
      return headers;
   }

   private void save(BundleManifest manifest)
   {
      try
      {
         Resource eResource = manifest.eResource();
         if (eResource == null)
         {
            final File file = getWs().newFile(
               manifest.getBundleSymbolicName().getSymbolicName() + "/META-INF/MANIFEST.MF");
            final URI uri = URI.createFileURI(file.getAbsolutePath());
            eResource = new BundleManifestResourceImpl(uri);
            eResource.getContents().add(manifest);
         }
         eResource.save(null);
      }
      catch (Exception e)
      {
         throw Exceptions.pipe(e);
      }
   }

   private static void addBundleRequirement(BundleManifest bundle, String symbolicName, String versionRange)
   {
      addBundleRequirement(bundle, symbolicName, versionRange, false);
   }

   private static void addBundleRequirement(BundleManifest bundle, String symbolicName, String versionRange,
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

   private static void addPackageExport(BundleManifest bundle, String packageName, String version)
   {
      final PackageExport packageExport = BundleManifestFactory.eINSTANCE.createPackageExport();
      packageExport.getPackageNames().add(packageName);
      if (version != null)
      {
         packageExport.setVersion(Version.parse(version));
      }
      bundle.getExportPackage(true).add(packageExport);
   }

   private static void addPackageImport(BundleManifest bundle, String packageName, String versionRange)
   {
      addPackageImport(bundle, packageName, versionRange, false);
   }

   private static void addPackageImport(BundleManifest bundle, String packageName, String versionRange, boolean optional)
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

   @Override
   protected Environment newEnvironment()
   {
      return Environment.get("env-it.properties");
   }

}
