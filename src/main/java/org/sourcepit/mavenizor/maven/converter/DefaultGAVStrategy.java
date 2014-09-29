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

package org.sourcepit.mavenizor.maven.converter;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sourcepit.common.constraints.NotNull;

import org.codehaus.plexus.interpolation.AbstractValueSource;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.manifest.osgi.Version;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.common.utils.path.PathMatcher;
import org.sourcepit.common.utils.props.LinkedPropertiesMap;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.common.utils.props.PropertiesSource;
import org.sourcepit.mavenizor.maven.converter.GAVStrategyFactory.SnapshotRule;
import org.sourcepit.tools.shared.resources.harness.StringInterpolator;

public class DefaultGAVStrategy implements GAVStrategy
{
   private static final Pattern GROUP_2_PATTERN = Pattern.compile("^(\\w*\\.\\w*)(\\..*)?$");
   private static final Pattern GROUP_3_PATTERN = Pattern.compile("^(\\w*\\.\\w*\\.\\w*)(\\..*)?$");
   private static final String GROUP_3[] = { "net.sf", "org.apache", "org.codehaus", "org.tigris", "org.sourcepit" };

   private final List<SnapshotRule> snapshotRules;
   private final String groupIdPrefix;
   private final boolean trimQualifiers;
   private final Collection<String> group3Prefixes;
   private final Map<String, String> groupIdMappings;

   public DefaultGAVStrategy(@NotNull List<SnapshotRule> snapshotRules, String groupIdPrefix, boolean trimQualifiers,
      Collection<String> group3Prefixes, Map<String, String> groupIdMappings)
   {
      this.snapshotRules = snapshotRules;
      this.groupIdPrefix = groupIdPrefix == null ? null : groupIdPrefix.endsWith(".") ? groupIdPrefix : groupIdPrefix
         + ".";
      this.trimQualifiers = trimQualifiers;
      this.groupIdMappings = groupIdMappings;

      this.group3Prefixes = new HashSet<String>();
      Collections.addAll(this.group3Prefixes, GROUP_3);
      if (group3Prefixes != null)
      {
         this.group3Prefixes.addAll(group3Prefixes);
      }
   }

   public String deriveGroupId(@NotNull BundleDescription bundle)
   {
      final String symbolicName = bundle.getSymbolicName();

      String groupId = applyGroupIdMapping(symbolicName);
      if (groupId == null)
      {
         groupId = deriveGroupId(symbolicName);
      }
      if (groupIdPrefix != null)
      {
         groupId = groupIdPrefix + groupId;
      }
      return groupId;
   }

   private String applyGroupIdMapping(final String symbolicName)
   {
      for (Entry<String, String> entry : groupIdMappings.entrySet())
      {
         final PathMatcher matcher = PathMatcher.parsePackagePatterns(entry.getKey());
         if (matcher.isMatch(symbolicName))
         {
            PropertiesMap props = new LinkedPropertiesMap(1);
            props.put("bundle.groupId", deriveGroupId(symbolicName));
            props.put("bundle.symbolicName", symbolicName);

            return interpolate(props, entry.getValue());
         }
      }
      return null;
   }

   private static String interpolate(final PropertiesSource moduleProperties, String value)
   {
      StringInterpolator s = new StringInterpolator();
      s.setEscapeString("\\");
      s.getValueSources().add(new AbstractValueSource(false)
      {
         public Object getValue(String expression)
         {
            return moduleProperties.get(expression);
         }
      });
      return s.interpolate(value);
   }

   private String deriveGroupId(@NotNull String symbolicName)
   {
      checkArgument(symbolicName.length() > 0);
      Matcher m = null;

      for (String group3Prefix : group3Prefixes)
      {
         if (symbolicName.startsWith(group3Prefix))
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

   private String deriveArtifactId(@NotNull String symbolicName)
   {
      checkArgument(symbolicName.length() > 0);
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
