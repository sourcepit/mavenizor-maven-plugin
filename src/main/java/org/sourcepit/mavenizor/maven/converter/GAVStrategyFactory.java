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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.ArtifactUtils;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.manifest.osgi.Version;

public interface GAVStrategyFactory
{
   interface SnapshotRule
   {
      boolean isSnapshotVersion(BundleDescription bundle, Version version);
   }

   SnapshotRule OSGI_SNAPSHOT_RULE = new SnapshotRule()
   {
      public boolean isSnapshotVersion(BundleDescription bundle, Version version)
      {
         return "qualifier".equals(version.getQualifier());
      }
   };

   SnapshotRule MAVEN_SNAPSHOT_RULE = new SnapshotRule()
   {
      public boolean isSnapshotVersion(BundleDescription bundle, Version version)
      {
         final String qualifier = version.getQualifier();
         return qualifier.length() > 0 && ArtifactUtils.isSnapshot("1-" + qualifier);
      }
   };

   SnapshotRule[] DEFAULT_SNAPSHOT_RULES = new SnapshotRule[] { OSGI_SNAPSHOT_RULE, MAVEN_SNAPSHOT_RULE };

   class Request
   {
      private String groupIdPrefix;
      private boolean useDefaultSnapshotRules = true;
      private boolean trimQualifiers = false;
      private Collection<String> group3Prefixes = new HashSet<String>();
      private Map<String, String> groupIdMappings = new LinkedHashMap<String, String>();

      private final List<SnapshotRule> additionalSnapshotRules = new ArrayList<SnapshotRule>();

      public String getGroupIdPrefix()
      {
         return groupIdPrefix;
      }

      public void setGroupIdPrefix(String groupIdPrefix)
      {
         this.groupIdPrefix = groupIdPrefix;
      }

      public boolean isUseDefaultSnapshotRules()
      {
         return useDefaultSnapshotRules;
      }

      public void setUseDefaultSnapshotRules(boolean useDefaultSnapshotRules)
      {
         this.useDefaultSnapshotRules = useDefaultSnapshotRules;
      }

      public List<SnapshotRule> getAdditionalSnapshotRules()
      {
         return additionalSnapshotRules;
      }

      public boolean isTrimQualifiers()
      {
         return trimQualifiers;
      }

      public void setTrimQualifiers(boolean trimQualifiers)
      {
         this.trimQualifiers = trimQualifiers;
      }

      public Collection<String> getGroup3Prefixes()
      {
         return group3Prefixes;
      }

      public Map<String, String> getGroupIdMappings()
      {
         return groupIdMappings;
      }
   }

   GAVStrategy newGAVStrategy(Request request);
}
