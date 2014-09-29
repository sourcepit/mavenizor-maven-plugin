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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.util.ChecksumUtils;
import org.slf4j.Logger;
import org.sourcepit.common.utils.lang.Exceptions;

public final class DeploymentHandler extends AbstractDistributionHandler
{
   private final RepositoryConnectorProvider repositoryConnectorProvider;
   private final RepositorySystemSession repositorySession;
   private final ArtifactDeployer deployer;
   private final ArtifactRepository localRepository;
   private final ArtifactRepository snapshotRepository;
   private final ArtifactRepository releaseRepository;
   private final MetadataResolver metadataResolver;

   public DeploymentHandler(Logger log, RepositoryConnectorProvider repositoryConnectorProvider,
      RepositorySystemSession repositorySession, ArtifactDeployer deployer, ArtifactRepository localRepository,
      ArtifactRepository snapshotRepository, ArtifactRepository releaseRepository, MetadataResolver metadataResolver)
   {
      super(log);
      this.repositoryConnectorProvider = repositoryConnectorProvider;
      this.repositorySession = repositorySession;
      this.deployer = deployer;
      this.localRepository = localRepository;
      this.snapshotRepository = snapshotRepository;
      this.releaseRepository = releaseRepository;
      this.metadataResolver = metadataResolver;
   }

   @Override
   protected void doDistribute(Artifact artifact)
   {
      final ArtifactRepository deploymentRepository = determineDeploymentRepository(artifact);
      try
      {
         deployer.deploy(artifact.getFile(), artifact, deploymentRepository, localRepository);
      }
      catch (ArtifactDeploymentException e)
      {
         throw Exceptions.pipe(e);
      }
   }

   @Override
   protected String getLocalChecksum(Artifact artifact)
   {
      return calc(artifact.getFile(), "SHA-1");
   }

   @Override
   protected String getTargetChecksum(Artifact artifact)
   {
      final ArtifactRepository deploymentRepository = determineDeploymentRepository(artifact);
      final RemoteRepository remoteRepo = RepositoryUtils.toRepo(deploymentRepository);
      return readRemoteChecksum(remoteRepo, RepositoryUtils.toArtifact(artifact));
   }

   private String readRemoteChecksum(RemoteRepository remoteRepository, org.eclipse.aether.artifact.Artifact artifact)
   {
      final org.eclipse.aether.artifact.Artifact sha1Artifact = toSha1Artifact(expandSnapshotVersion(remoteRepository,
         artifact));

      final String sha1Path = repositorySession.getLocalRepositoryManager().getPathForLocalArtifact(sha1Artifact) + "_"
         + UUID.randomUUID().toString();

      final File sha1File = new File(localRepository.getBasedir(), sha1Path);
      try
      {
         final ArtifactDownload download = new ArtifactDownload();
         download.setArtifact(sha1Artifact);
         download.setFile(sha1File);
         download.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
         download(remoteRepository, download);
         return ChecksumUtils.read(sha1File);
      }
      catch (ArtifactNotFoundException e)
      {
         return null;
      }
      catch (IOException e)
      {
         throw Exceptions.pipe(e);
      }
      finally
      {
         sha1File.delete();
      }

   }

   private void download(RemoteRepository remoteRepository, final ArtifactDownload download)
      throws ArtifactNotFoundException
   {
      RepositoryConnector connector = null;
      try
      {
         connector = repositoryConnectorProvider.newRepositoryConnector(repositorySession, remoteRepository);
      }
      catch (NoRepositoryConnectorException e)
      {
         throw Exceptions.pipe(e);
      }

      try
      {
         connector.get(Collections.singletonList(download), null);
         final ArtifactTransferException error = download.getException();
         if (error != null)
         {
            throw error;
         }
      }
      catch (ArtifactNotFoundException e)
      {
         throw e;
      }
      catch (ArtifactTransferException e)
      {
         throw Exceptions.pipe(e);
      }
      finally
      {
         if (connector != null)
         {
            connector.close();
         }
      }
   }

   private org.eclipse.aether.artifact.Artifact toSha1Artifact(org.eclipse.aether.artifact.Artifact artifact)
   {
      return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
         artifact.getExtension() + ".sha1", artifact.getVersion());
   }

   private org.eclipse.aether.artifact.Artifact expandSnapshotVersion(final RemoteRepository remoteRepo,
      org.eclipse.aether.artifact.Artifact artifact)
   {
      final String groupId = artifact.getGroupId();
      final String artifactId = artifact.getArtifactId();
      final String version = expandSnapshotVersion(remoteRepo, groupId, artifactId, artifact.getVersion());

      return new DefaultArtifact(groupId, artifactId, artifact.getClassifier(), artifact.getExtension(), version);
   }

   private String expandSnapshotVersion(final RemoteRepository remoteRepo, String groupId, String artifactId,
      String version)
   {
      if (version.endsWith("-SNAPSHOT"))
      {
         try
         {
            final Metadata metadata = resolveMetadata(new DefaultMetadata(groupId, artifactId, version,
               "maven-metadata.xml", Metadata.Nature.SNAPSHOT), remoteRepo);

            File file = metadata.getFile();

            final org.apache.maven.artifact.repository.metadata.Metadata mavenMetadata = readMavenMetadata(file);

            final Versioning versioning = mavenMetadata.getVersioning();
            if (versioning != null)
            {
               Snapshot snapshot = versioning.getSnapshot();
               if (snapshot != null)
               {
                  String qualifier = snapshot.getTimestamp() + "-" + snapshot.getBuildNumber();
                  return version.substring(0, version.length() - "-SNAPSHOT".length() + 1) + qualifier;
               }
            }
         }
         catch (MetadataNotFoundException e)
         {
         }
      }
      return version;
   }

   private org.apache.maven.artifact.repository.metadata.Metadata readMavenMetadata(File file)
   {
      FileInputStream fis = null;
      try
      {
         fis = new FileInputStream(file);
         return new MetadataXpp3Reader().read(fis, false);
      }
      catch (Exception e)
      {
         throw Exceptions.pipe(e);
      }
      finally
      {
         IOUtils.closeQuietly(fis);
      }
   }

   private Metadata resolveMetadata(Metadata metadata, RemoteRepository remoteRepository)
      throws MetadataNotFoundException
   {
      final MetadataRequest request = new MetadataRequest();
      request.setMetadata(metadata);
      request.setRepository(remoteRepository);
      request.setDeleteLocalCopyIfMissing(true);
      request.setFavorLocalRepository(false);

      final List<MetadataResult> metadataResults = metadataResolver.resolveMetadata(repositorySession,
         Arrays.asList(request));

      final MetadataResult metadataResult = metadataResults.get(0);

      final Exception exception = metadataResult.getException();
      if (exception instanceof MetadataNotFoundException)
      {
         throw (MetadataNotFoundException) exception;
      }
      else if (exception != null)
      {
         throw Exceptions.pipe(exception);
      }

      return metadataResult.getMetadata();
   }

   private ArtifactRepository determineDeploymentRepository(Artifact artifact)
   {
      final ArtifactRepository deploymentRepository;
      if (ArtifactUtils.isSnapshot(artifact.getVersion()))
      {
         deploymentRepository = snapshotRepository;
      }
      else
      {
         deploymentRepository = releaseRepository;
      }
      return deploymentRepository;
   }
}