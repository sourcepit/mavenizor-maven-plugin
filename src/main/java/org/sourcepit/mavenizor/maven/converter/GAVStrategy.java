/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.common.utils.path.Path;

public interface GAVStrategy
{
   String deriveGroupId(BundleDescription bundle);

   String deriveArtifactId(BundleDescription bundle);

   String deriveArtifactId(BundleDescription bundle, Path libraryEntry);

   String deriveMavenVersion(BundleDescription bundle);

   String deriveMavenVersionRange(BundleDescription bundle, VersionRange versionRange);
}