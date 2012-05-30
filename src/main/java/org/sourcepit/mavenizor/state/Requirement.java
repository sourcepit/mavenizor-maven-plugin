/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.state;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.manifest.osgi.VersionRange;

public class Requirement
{
   private BundleDescription from, to;
   private VersionRange versionRange;
   private boolean optional;

   public BundleDescription getFrom()
   {
      return from;
   }

   public void setFrom(BundleDescription from)
   {
      this.from = from;
   }

   public BundleDescription getTo()
   {
      return to;
   }

   public void setTo(BundleDescription to)
   {
      this.to = to;
   }

   public VersionRange getVersionRange()
   {
      return versionRange;
   }

   public void setVersionRange(VersionRange versionRange)
   {
      this.versionRange = versionRange;
   }

   public boolean isOptional()
   {
      return optional;
   }

   public void setOptional(boolean optional)
   {
      this.optional = optional;
   }
}