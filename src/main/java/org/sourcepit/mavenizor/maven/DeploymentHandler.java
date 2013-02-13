/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
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
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.slf4j.Logger;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.impl.MetadataResolver;
import org.sonatype.aether.impl.RemoteRepositoryManager;
import org.sonatype.aether.metadata.Metadata;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.MetadataRequest;
import org.sonatype.aether.resolution.MetadataResult;
import org.sonatype.aether.spi.connector.ArtifactDownload;
import org.sonatype.aether.spi.connector.RepositoryConnector;
import org.sonatype.aether.transfer.ArtifactNotFoundException;
import org.sonatype.aether.transfer.ArtifactTransferException;
import org.sonatype.aether.transfer.MetadataNotFoundException;
import org.sonatype.aether.transfer.NoRepositoryConnectorException;
import org.sonatype.aether.util.ChecksumUtils;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.metadata.DefaultMetadata;
import org.sourcepit.common.utils.lang.Exceptions;

@SuppressWarnings("deprecation")
public final class DeploymentHandler extends AbstractDistributionHandler
{
   private final RemoteRepositoryManager remoteRepositoryManager;
   private final RepositorySystemSession repositorySession;
   private final ArtifactDeployer deployer;
   private final ArtifactRepository localRepository;
   private final ArtifactRepository snapshotRepository;
   private final ArtifactRepository releaseRepository;
   private final MetadataResolver metadataResolver;

   public DeploymentHandler(Logger log, RemoteRepositoryManager remoteRepositoryManager,
      RepositorySystemSession repositorySession, ArtifactDeployer deployer, ArtifactRepository localRepository,
      ArtifactRepository snapshotRepository, ArtifactRepository releaseRepository, MetadataResolver metadataResolver)
   {
      super(log);
      this.remoteRepositoryManager = remoteRepositoryManager;
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
      /*
       * NOTE: This provides backward-compat with maven-deploy-plugin:2.4 which bypasses the repository factory when
       * using an alternative deployment location.
       */
      if (deploymentRepository instanceof DefaultArtifactRepository && deploymentRepository.getAuthentication() == null)
      {
         remoteRepo.setAuthentication(repositorySession.getAuthenticationSelector().getAuthentication(remoteRepo));
         remoteRepo.setProxy(repositorySession.getProxySelector().getProxy(remoteRepo));
      }

      final String remoteChecksum = readRemoteChecksum(remoteRepo, RepositoryUtils.toArtifact(artifact));
      return remoteChecksum;
   }

   private String readRemoteChecksum(RemoteRepository remoteRepository, org.sonatype.aether.artifact.Artifact artifact)
   {
      final org.sonatype.aether.artifact.Artifact sha1Artifact = toSha1Artifact(expandSnapshotVersion(remoteRepository,
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
         connector = remoteRepositoryManager.getRepositoryConnector(repositorySession, remoteRepository);
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

   private org.sonatype.aether.artifact.Artifact toSha1Artifact(org.sonatype.aether.artifact.Artifact artifact)
   {
      return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
         artifact.getExtension() + ".sha1", artifact.getVersion());
   }

   private org.sonatype.aether.artifact.Artifact expandSnapshotVersion(final RemoteRepository remoteRepo,
      org.sonatype.aether.artifact.Artifact artifact)
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