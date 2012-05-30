/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.ArtifactUtils;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.manifest.osgi.Version;

public interface ConverterFactory
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
      private boolean useDefaultSnapshotRules = true;

      private final List<SnapshotRule> additionalSnapshotRules = new ArrayList<SnapshotRule>();

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
   }

   Converter newConverter(Request request);
}
