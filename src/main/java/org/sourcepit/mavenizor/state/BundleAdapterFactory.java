/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.state;

import static org.sourcepit.common.utils.io.IOResources.osgiIn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.manifest.osgi.resource.BundleManifestResourceImpl;
import org.sourcepit.common.utils.adapt.AbstractAdapterFactory;
import org.sourcepit.common.utils.io.IOOperation;

public class BundleAdapterFactory extends AbstractAdapterFactory
{
   public final static BundleAdapterFactory DEFAULT = new BundleAdapterFactory();

   @Override
   @SuppressWarnings("unchecked")
   protected <A> A newAdapter(Object adaptable, Class<A> adapterType)
   {
      if (adaptable instanceof BundleDescription)
      {
         final BundleDescription bundle = (BundleDescription) adaptable;
         if (File.class.isAssignableFrom(adapterType))
         {
            final String location = bundle.getLocation();
            if (location != null)
            {
               final File bundleLocation = new File(location);
               if (bundleLocation.canRead())
               {
                  return (A) bundleLocation;
               }
            }
         }
         else if (BundleManifest.class.isAssignableFrom(adapterType))
         {
            final File bundleLocation = adapt(adaptable, File.class);
            if (bundleLocation != null)
            {
               return (A) readManifest(bundleLocation);
            }
         }
      }
      return null;
   }

   private static BundleManifest readManifest(File location)
   {
      final Resource resource = new BundleManifestResourceImpl();
      new IOOperation<InputStream>(osgiIn(location, "META-INF/MANIFEST.MF"))
      {
         @Override
         protected void run(InputStream inputStream) throws IOException
         {
            resource.load(inputStream, null);
         }
      }.run();
      final BundleManifest bundleManifest = (BundleManifest) resource.getContents().get(0);
      resource.getContents().clear();
      return bundleManifest;
   }
}
