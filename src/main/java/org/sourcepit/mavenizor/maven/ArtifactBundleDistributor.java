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

package org.sourcepit.mavenizor.maven;

import static org.sourcepit.common.utils.io.IO.buffOut;
import static org.sourcepit.common.utils.io.IO.fileOut;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.repository.RepositorySystem;
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.utils.io.IOOperation;
import org.sourcepit.mavenizor.ArtifactBundle;
import org.sourcepit.mavenizor.maven.converter.ConvertedArtifact;

@Named
public class ArtifactBundleDistributor
{
   @Inject
   private RepositorySystem repositorySystem;

   @Inject
   private ArtifactRepositoryLayout repositoryLayout;

   @Inject
   private ModelWriter modelWriter;

   interface DistributionHandler
   {
      void distribute(Artifact artifact, boolean forceOverwrite);
   }

   public void distribute(File workingDir, ArtifactBundle artifactBundle, DistributionHandler distributor,
      boolean forceOverwrite)
   {
      boolean pomDistributed = false;
      for (ConvertedArtifact cArtifact : artifactBundle.getArtifacts())
      {
         if (cArtifact.isMavenized())
         {
            MavenArtifact mavenArtifact = cArtifact.getMavenArtifact();
            final Model pom = artifactBundle.getPom();
            if (!pomDistributed)
            {
               distributePom(workingDir, pom, distributor, forceOverwrite);
               pomDistributed = true;
            }
            final Artifact artifact = createArtifact(pom, mavenArtifact.getClassifier(), mavenArtifact.getType());
            artifact.setFile(mavenArtifact.getFile());
            distribute(distributor, artifact, forceOverwrite);
         }
      }
   }

   private void distributePom(File workinDir, final Model pom, DistributionHandler distributor, boolean forceOverwrite)
   {
      final Artifact pomArtifact = createArtifact(pom, "pom");

      final File pomFile = new File(workinDir, repositoryLayout.pathOf(pomArtifact));
      new IOOperation<OutputStream>(buffOut(fileOut(pomFile, true)))
      {
         @Override
         protected void run(OutputStream outputStream) throws IOException
         {
            modelWriter.write(outputStream, null, pom);
         }
      }.run();
      pomArtifact.setFile(pomFile);
      distribute(distributor, pomArtifact, forceOverwrite);
   }

   private void distribute(DistributionHandler distributor, Artifact artifact, boolean forceOverwrite)
   {
      distributor.distribute(artifact, forceOverwrite);
   }

   private Artifact createArtifact(final Model model, String packaging)
   {
      return repositorySystem.createArtifact(model.getGroupId(), model.getArtifactId(), model.getVersion(), packaging);
   }

   private Artifact createArtifact(final Model model, String classifier, String type)
   {
      return repositorySystem.createArtifactWithClassifier(model.getGroupId(), model.getArtifactId(),
         model.getVersion(), type, classifier);
   }
}
