/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import org.sourcepit.common.maven.model.MavenArtifact;

public class ConverterAction
{
   private final ConvertionDirective directive;

   private final MavenArtifact replacement;

   public ConverterAction(ConvertionDirective directive, MavenArtifact replacement)
   {
      this.directive = directive;
      this.replacement = replacement;
   }

   public ConvertionDirective getDirective()
   {
      return directive;
   }

   public MavenArtifact getReplacement()
   {
      return replacement;
   }

}
