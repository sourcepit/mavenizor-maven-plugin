/*
 * Copyright 2014 Bernd Vogt and others.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sourcepit.mavenizor.state;

import static org.sourcepit.common.manifest.osgi.VersionRange.intersect;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Named;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.BundleSpecification;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.osgi.service.resolver.ImportPackageSpecification;
import org.osgi.framework.Constants;
import org.sourcepit.common.manifest.osgi.VersionRange;

@Named
public class DefaultRequirementsCollector implements RequirementsCollector
{
   public Collection<Requirement> collectRequirements(BundleDescription bundle)
   {
      final Map<BundleDescription, Requirement> requirements = new LinkedHashMap<BundleDescription, Requirement>();
      collectRequirementsByBundleRequirements(bundle, requirements);
      collectRequirementsByPackageImports(bundle, requirements);
      requirements.remove(bundle); // remove self reference
      return requirements.values();
   }

   private static void collectRequirementsByBundleRequirements(BundleDescription bundle,
      final Map<BundleDescription, Requirement> requirements)
   {
      final Map<String, VersionRange> importVersioRange = new HashMap<String, VersionRange>();
      final Map<String, Boolean> importIsOptional = new HashMap<String, Boolean>();
      for (BundleSpecification spec : bundle.getRequiredBundles())
      {
         final String packageName = spec.getName();
         final VersionRange versionRange = VersionRange.parse(spec.getVersionRange().toString());
         importVersioRange.put(packageName, versionRange);

         final Boolean optional = Boolean.valueOf(spec.isOptional());
         importIsOptional.put(packageName, optional);
      }

      for (BundleDescription exporter : bundle.getResolvedRequires())
      {
         putRequitement(bundle, requirements, importVersioRange, importIsOptional, exporter, exporter.getName());
      }
   }

   private static void collectRequirementsByPackageImports(BundleDescription bundle,
      final Map<BundleDescription, Requirement> requirements)
   {
      final Map<String, VersionRange> importVersioRange = new HashMap<String, VersionRange>();
      final Map<String, Boolean> importIsOptional = new HashMap<String, Boolean>();
      for (ImportPackageSpecification spec : bundle.getImportPackages())
      {
         final String packageName = spec.getName();
         final VersionRange versionRange = VersionRange.parse(spec.getVersionRange().toString());
         importVersioRange.put(packageName, versionRange);

         final Boolean optional = Boolean.valueOf(Constants.RESOLUTION_OPTIONAL.equals(spec
            .getDirective(Constants.RESOLUTION_DIRECTIVE)));
         importIsOptional.put(packageName, optional);
      }

      for (ExportPackageDescription resolvedImport : bundle.getResolvedImports())
      {
         final BundleDescription exporter = resolvedImport.getExporter();
         final String packageName = resolvedImport.getName();
         putRequitement(bundle, requirements, importVersioRange, importIsOptional, exporter, packageName);
      }
   }

   private static void putRequitement(BundleDescription bundle, final Map<BundleDescription, Requirement> requirements,
      final Map<String, VersionRange> importVersioRange, final Map<String, Boolean> importIsOptional,
      final BundleDescription exporter, final String packageName)
   {
      Requirement requirement = requirements.get(exporter);
      if (requirement == null)
      {
         requirement = new Requirement();
         requirement.setFrom(bundle);
         requirement.setTo(exporter);
         requirement.setVersionRange(VersionRange.INFINITE_RANGE);

         final Boolean optional = importIsOptional.get(packageName);
         if (optional != null)
         {
            requirement.setOptional(optional.booleanValue());
         }
      }

      final Boolean optional = importIsOptional.get(packageName);
      if (optional != null)
      {
         requirement.setOptional(requirement.isOptional() ? optional.booleanValue() : false);
      }

      if (requirement.getVersionRange() != null) // null means invalid
      {
         final VersionRange versionRange = importVersioRange.get(packageName);
         if (versionRange != null)
         {
            try
            {
               final VersionRange newRange = intersect(requirement.getVersionRange(), versionRange);
               requirement.setVersionRange(newRange);
            }
            catch (IllegalArgumentException e)
            {
               requirement.setVersionRange(null); // invalidate
            }
         }
      }

      requirements.put(exporter, requirement);
   }
}
