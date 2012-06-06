/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.manifest.osgi.Version;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.mavenizor.maven.converter.GAVStrategyFactory.SnapshotRule;

public class DefaultGAVStrategy implements GAVStrategy
{
   private static final Pattern GROUP_2_PATTERN = Pattern.compile("^(\\w*\\.\\w*)(\\..*)?$");
   private static final Pattern GROUP_3_PATTERN = Pattern.compile("^(\\w*\\.\\w*\\.\\w*)(\\..*)?$");
   private static final String GROUP_3[] = { "net.sf", "org.apache", "org.codehaus", "org.tigris", "org.sourcepit",
      "org.eclipse" };

   private final List<SnapshotRule> snapshotRules;
   private final String groupIdPrefix;
   private final boolean trimQualifiers;

   public DefaultGAVStrategy(@NotNull List<SnapshotRule> snapshotRules, String groupIdPrefix, boolean trimQualifiers)
   {
      this.snapshotRules = snapshotRules;
      this.groupIdPrefix = groupIdPrefix == null ? null : groupIdPrefix.endsWith(".") ? groupIdPrefix : groupIdPrefix
         + ".";
      this.trimQualifiers = trimQualifiers;
   }

   public String deriveGroupId(@NotNull BundleDescription bundle)
   {
      final String groupId = deriveGroupId(bundle.getSymbolicName());
      if (groupIdPrefix == null)
      {
         return groupId;
      }
      return groupIdPrefix + groupId;
   }

   private String deriveGroupId(@NotNull @Size(min = 1) String symbolicName)
   {
      Matcher m = null;
      for (int i = 0; i < GROUP_3.length; i++)
      {
         if (symbolicName.startsWith(GROUP_3[i]))
         {
            m = GROUP_3_PATTERN.matcher(symbolicName);
         }
      }

      if (m == null)
      {
         m = GROUP_2_PATTERN.matcher(symbolicName);
      }

      if (m.matches())
      {
         return m.group(1);
      }

      return symbolicName;
   }

   public String deriveArtifactId(@NotNull BundleDescription bundle)
   {
      return deriveArtifactId(bundle.getSymbolicName());
   }

   private String deriveArtifactId(@NotNull @Size(min = 1) String symbolicName)
   {
      return symbolicName;
   }

   public String deriveArtifactId(@NotNull BundleDescription bundle, @NotNull Path libraryEntry)
   {
      return libraryEntry.getFileName();
   }

   public String deriveMavenVersion(@NotNull BundleDescription bundle)
   {
      final Version version = Version.parse(bundle.getVersion().toString());
      return deriveMavenVersion(bundle, version);
   }

   private String deriveMavenVersion(BundleDescription bundle, final Version version)
   {
      final StringBuilder sb = new StringBuilder();
      sb.append(version.getMajor());
      sb.append('.');
      sb.append(version.getMinor());
      sb.append('.');
      sb.append(version.getMicro());

      final String qualifier = deriveMavenVersionQualifier(bundle, version);
      if (qualifier != null)
      {
         sb.append('-');
         sb.append(qualifier);
      }

      return sb.toString();
   }

   private String deriveMavenVersionQualifier(BundleDescription bundle, Version version)
   {
      if (isSnapshotVersion(bundle, version))
      {
         return "SNAPSHOT";
      }
      if (!trimQualifiers && version.getQualifier().length() > 0)
      {
         return version.getQualifier();
      }
      return null;
   }

   public String deriveMavenVersionRange(@NotNull BundleDescription bundle, @NotNull VersionRange versionRange)
   {
      final Version lowVersion = versionRange.getLowVersion();
      final Version highVersion = versionRange.getHighVersion();

      if (highVersion == null && lowVersion != null)
      {
         return deriveMavenVersionRange(bundle, lowVersion);
      }

      final StringBuilder sb = new StringBuilder();
      sb.append(versionRange.isLowInclusive() ? '[' : '(');
      appendToRange(sb, lowVersion);
      sb.append(',');
      appendToRange(sb, highVersion);
      sb.append(versionRange.isHighInclusive() ? ']' : ')');
      return sb.toString();
   }

   private String deriveMavenVersionRange(@NotNull BundleDescription bundle, @NotNull Version recommendedVersion)
   {
      final String qualifier = deriveMavenVersionQualifier(bundle, recommendedVersion);
      final StringBuilder sb = new StringBuilder();
      if (qualifier == null)
      {
         sb.append('[');
         appendToRange(sb, recommendedVersion);
         sb.append(",)");
      }
      else
      {
         sb.append(recommendedVersion.getMajor());
         sb.append('.');
         sb.append(recommendedVersion.getMinor());
         sb.append('.');
         sb.append(recommendedVersion.getMicro());
         sb.append('-');
         sb.append(qualifier);
      }
      return sb.toString();

   }

   private void appendToRange(final StringBuilder sb, final Version version)
   {
      final boolean hasMicro = version.getMicro() > 0;
      final boolean hasMinorOrMicro = version.getMinor() > 0 || hasMicro;
      sb.append(version.getMajor());
      if (hasMinorOrMicro)
      {
         sb.append('.');
         sb.append(version.getMinor());
         if (hasMicro)
         {
            sb.append('.');
            sb.append(version.getMicro());
         }
      }
   }

   private boolean isSnapshotVersion(BundleDescription bundle, Version version)
   {
      for (SnapshotRule snapshotRule : snapshotRules)
      {
         if (snapshotRule.isSnapshotVersion(bundle, version))
         {
            return true;
         }
      }
      return false;
   }
}
