/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Model;
import org.sourcepit.common.maven.model.MavenArtifact;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class ArtifactBundle
{
   private Model pom;

   private final List<MavenArtifact> artifacts = new ArrayList<MavenArtifact>();

   public Model getPom()
   {
      return pom;
   }

   public void setPom(Model pom)
   {
      this.pom = pom;
   }

   public List<MavenArtifact> getArtifacts()
   {
      return artifacts;
   }
}
