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

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.manifest.osgi.VersionRange;

public class Requirement {
   private BundleDescription from, to;
   private VersionRange versionRange;
   private boolean optional;

   public BundleDescription getFrom() {
      return from;
   }

   public void setFrom(BundleDescription from) {
      this.from = from;
   }

   public BundleDescription getTo() {
      return to;
   }

   public void setTo(BundleDescription to) {
      this.to = to;
   }

   public VersionRange getVersionRange() {
      return versionRange;
   }

   public void setVersionRange(VersionRange versionRange) {
      this.versionRange = versionRange;
   }

   public boolean isOptional() {
      return optional;
   }

   public void setOptional(boolean optional) {
      this.optional = optional;
   }
}