/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import org.sourcepit.common.maven.model.MavenArtifact;

public class ConvertedArtifact
{
   private final MavenArtifact mavenArtifact;

   private final ConvertionDirective directive;

   private final boolean embeddedLibrary;

   public ConvertedArtifact(MavenArtifact mavenArtifact, ConvertionDirective directive, boolean embeddedLibrary)
   {
      this.mavenArtifact = mavenArtifact;
      this.directive = directive;
      this.embeddedLibrary = embeddedLibrary;
   }

   public MavenArtifact getMavenArtifact()
   {
      return mavenArtifact;
   }

   public ConvertionDirective getDirective()
   {
      return directive;
   }

   public boolean isMavenized()
   {
      return ConvertionDirective.MAVENIZE == directive;
   }

   public boolean isEmbeddedLibrary()
   {
      return embeddedLibrary;
   }
}