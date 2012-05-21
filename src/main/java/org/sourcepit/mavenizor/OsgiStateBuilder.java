/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import static org.sourcepit.common.utils.io.IOResources.buffIn;
import static org.sourcepit.common.utils.io.IOResources.fileIn;
import static org.sourcepit.common.utils.io.IOResources.zipIn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map.Entry;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.osgi.framework.BundleException;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.manifest.osgi.resource.BundleManifestResourceImpl;
import org.sourcepit.common.utils.io.IOOperation;
import org.sourcepit.common.utils.io.IOResource;
import org.sourcepit.common.utils.lang.Exceptions;


public class OsgiStateBuilder
{
   private StateObjectFactory stateFactory;

   private State state;

   private long currentId;

   public OsgiStateBuilder()
   {
      stateFactory = StateObjectFactory.defaultFactory;
      state = stateFactory.createState(true);
   }

   public void addBundle(File location)
   {
      state.addBundle(createBundle(location));
   }

   private BundleDescription createBundle(File location)
   {
      final Dictionary<String, String> headers = toDictionary(readManifest(location));
      try
      {
         return stateFactory.createBundleDescription(state, headers, location.getAbsolutePath(), currentId++);
      }
      catch (BundleException e)
      {
         throw Exceptions.pipe(e);
      }
   }

   private static Dictionary<String, String> toDictionary(final BundleManifest manifest)
   {
      final Dictionary<String, String> headers = new Hashtable<String, String>(manifest.getHeaders().size());
      for (Entry<String, String> header : manifest.getHeaders())
      {
         headers.put(header.getKey(), header.getValue());
      }
      return headers;
   }

   private static BundleManifest readManifest(File location)
   {
      final Resource resource = new BundleManifestResourceImpl();
      new IOOperation<InputStream>(newBundleResource(location, "META-INF/MANIFEST.MF"))
      {
         @Override
         protected void run(InputStream inputStream) throws IOException
         {
            resource.load(inputStream, null);
         }
      }.run();
      return (BundleManifest) resource.getContents().get(0);
   }

   private static IOResource<? extends InputStream> newBundleResource(File bundleLocation, String entryName)
   {
      if (bundleLocation.isDirectory())
      {
         return buffIn(fileIn(new File(bundleLocation, entryName)));
      }
      else
      {
         return zipIn(buffIn(fileIn(bundleLocation)), entryName);
      }
   }

   public State getState()
   {
      return state;
   }
}
