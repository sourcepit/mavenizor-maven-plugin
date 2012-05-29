/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.manifest.osgi.Version;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.maven.model.MavenModelFactory;

@Named
public class DefaultConverter implements Converter
{
   private Pattern group2Pattern = Pattern.compile("^(\\w*\\.\\w*)(\\..*)?$");
   private Pattern group3Pattern = Pattern.compile("^(\\w*\\.\\w*\\.\\w*)(\\..*)?$");
   public static String group3[] = { "net.sf", "org.apache", "org.codehaus", "org.tigris" };

   public MavenArtifact toMavenArtifact(BundleDescription bundle)
   {
      final MavenArtifact artifact = MavenModelFactory.eINSTANCE.createMavenArtifact();
      artifact.setGroupId(deriveGroupId(bundle));
      artifact.setArtifactId(deriveArtifactId(bundle));
      artifact.setVersion(deriveVersion(bundle));

      final String location = bundle.getLocation();
      if (location != null)
      {
         artifact.setFile(new File(location));
      }

      return artifact;
   }

   public String deriveGroupId(BundleDescription bundle)
   {
      final String symbolicName = bundle.getSymbolicName();
      Matcher m = null;
      for (int i = 0; i < group3.length; i++)
      {
         if (symbolicName.startsWith(group3[i]))
         {
            m = group3Pattern.matcher(symbolicName);
         }
      }

      if (m == null)
      {
         m = group2Pattern.matcher(symbolicName);
      }

      if (m.matches())
      {
         return m.group(1);
      }

      return symbolicName;
   }

   public String deriveArtifactId(BundleDescription bundle)
   {
      return bundle.getSymbolicName();
   }

   public String deriveVersion(BundleDescription bundle)
   {
      return toMavenVersion(Version.parse(bundle.getVersion().toString()));
   }

   public String toMavenVersion(Version version)
   {
      final StringBuilder sb = new StringBuilder();
      toMavenVersion(sb, version, false);
      return sb.toString();
   }

   private void toMavenVersion(final StringBuilder sb, Version version, boolean trimQualifier)
   {
      sb.append(version.getMajor());
      sb.append('.');
      sb.append(version.getMinor());
      sb.append('.');
      sb.append(version.getMicro());

      if (!trimQualifier)
      {
         final String qualifier = version.getQualifier();
         if (qualifier.length() > 0)
         {
            sb.append('-');
            sb.append("qualifier".equals(qualifier) ? "SNAPSHOT" : qualifier);
         }
      }
   }

   public String toMavenVersionRange(VersionRange versionRange)
   {
      final Version lowVersion = versionRange.getLowVersion();
      final Version highVersion = versionRange.getHighVersion();
      if (lowVersion != null && highVersion != null)
      {
         final StringBuilder sb = new StringBuilder();
         if (versionRange.isLowInclusive())
         {
            sb.append('[');
         }
         else
         {
            sb.append(')');
         }
         toMavenVersion(sb, lowVersion, true);
         sb.append(',');
         toMavenVersion(sb, highVersion, true);
         if (versionRange.isHighInclusive())
         {
            sb.append('[');
         }
         else
         {
            sb.append(')');
         }
         return sb.toString();
      }
      else if (lowVersion != null)
      {
         return toMavenVersion(lowVersion);
      }
      throw new IllegalArgumentException();
   }
}
