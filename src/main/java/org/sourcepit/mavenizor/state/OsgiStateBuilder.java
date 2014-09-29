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

package org.sourcepit.mavenizor.state;

import static org.sourcepit.common.utils.io.IO.cpIn;
import static org.sourcepit.common.utils.io.IO.osgiIn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.manifest.osgi.resource.BundleManifestResourceImpl;
import org.sourcepit.common.utils.adapt.Adapters;
import org.sourcepit.common.utils.io.IOOperation;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.common.utils.props.PropertiesUtils;


public class OsgiStateBuilder
{
   public final static String OSGI_WS = "osgi.ws";
   public final static String OSGI_OS = "osgi.os";
   public final static String OSGI_ARCH = "osgi.arch";

   private static String J2SE = "J2SE-";
   private static String JAVASE = "JavaSE-";

   private final ClassLoader classLoader;

   private StateObjectFactory stateFactory;

   private State state;

   private long currentId;

   public OsgiStateBuilder()
   {
      this(Thread.currentThread().getContextClassLoader());
   }

   public OsgiStateBuilder(ClassLoader classLoader)
   {
      this.classLoader = classLoader;
      stateFactory = StateObjectFactory.defaultFactory;
      state = stateFactory.createState(true);
   }

   public State getState()
   {
      return state;
   }

   public void addBundle(File location)
   {
      state.addBundle(createBundle(location));
   }

   private BundleDescription createBundle(File location)
   {
      final BundleManifest manifest = readManifest(location);
      final Dictionary<String, String> headers = toDictionary(manifest);
      final BundleDescription bundle;
      try
      {
         bundle = stateFactory.createBundleDescription(state, headers, location.getAbsolutePath(), currentId++);
      }
      catch (BundleException e)
      {
         throw Exceptions.pipe(e);
      }
      Adapters.addAdapter(bundle, location);
      Adapters.addAdapter(bundle, manifest);
      return bundle;
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
      new IOOperation<InputStream>(osgiIn(location, "META-INF/MANIFEST.MF"))
      {
         @Override
         protected void run(InputStream inputStream) throws IOException
         {
            resource.load(inputStream, null);
         }
      }.run();
      return (BundleManifest) resource.getContents().get(0);
   }

   @SuppressWarnings({ "rawtypes", "unchecked" })
   public void addPlatformProperties(Map properties)
   {
      final Dictionary<Object, Object> platformProperties = getPlatformProperties();
      for (Entry entry : (Set<Entry>) properties.entrySet())
      {
         platformProperties.put(entry.getKey(), entry.getValue());
      }
   }

   public void addExecutionEnvironmentProperties(String name)
   {
      new IOOperation<InputStream>(cpIn(classLoader, name + ".profile"))
      {
         @Override
         protected void run(InputStream inputStream) throws IOException
         {
            PropertiesUtils.load(inputStream, getPlatformProperties());
         }
      }.run();
   }

   public void addSystemExecutionEnvironmentProperties()
   {
      final String name = determineProfileName(System.getProperties(), "OSGi/Minimum-1.1");
      addExecutionEnvironmentProperties(name);
   }

   private static String determineProfileName(Properties properties, String defaultName)
   {
      String javaSpecVersion = properties.getProperty("java.specification.version");
      if (javaSpecVersion != null)
      {
         final StringTokenizer st = new StringTokenizer(javaSpecVersion, " _-");
         javaSpecVersion = st.nextToken();
         String javaSpecName = properties.getProperty("java.specification.name");
         if ("J2ME Foundation Specification".equals(javaSpecName))
         {
            return "CDC-" + javaSpecVersion + "_Foundation-" + javaSpecVersion;
         }
         else
         {
            // look for JavaSE if 1.6 or greater; otherwise look for J2SE
            Version v16 = new Version("1.6"); //$NON-NLS-1$
            String javaEdition = J2SE;
            try
            {
               Version javaVersion = new Version(javaSpecVersion);
               if (v16.compareTo(javaVersion) <= 0)
               {
                  javaEdition = JAVASE;
               }
            }
            catch (IllegalArgumentException e)
            {
               // do nothing
            }
            return javaEdition + javaSpecVersion;
         }
      }
      return defaultName;
   }

   @SuppressWarnings("unchecked")
   private Dictionary<Object, Object> getPlatformProperties()
   {
      return state.getPlatformProperties()[0];
   }
}
