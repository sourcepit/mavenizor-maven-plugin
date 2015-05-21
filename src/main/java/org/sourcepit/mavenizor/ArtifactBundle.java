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

package org.sourcepit.mavenizor;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Model;
import org.sourcepit.mavenizor.maven.converter.ConvertedArtifact;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class ArtifactBundle {
   private Model pom;

   private final List<ConvertedArtifact> artifacts = new ArrayList<ConvertedArtifact>();

   public Model getPom() {
      return pom;
   }

   public void setPom(Model pom) {
      this.pom = pom;
   }

   public List<ConvertedArtifact> getArtifacts() {
      return artifacts;
   }
}
