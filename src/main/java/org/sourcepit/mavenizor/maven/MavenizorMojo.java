/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.tycho.core.TargetEnvironment;
import org.eclipse.tycho.core.TargetPlatformConfiguration;
import org.eclipse.tycho.core.utils.TychoProjectUtils;
import org.slf4j.Logger;
import org.sonatype.guice.bean.locators.MutableBeanLocator;
import org.sonatype.inject.BeanEntry;
import org.sourcepit.common.utils.lang.PipedException;
import org.sourcepit.mavenizor.Mavenizor;
import org.sourcepit.mavenizor.Mavenizor.Result;
import org.sourcepit.mavenizor.maven.converter.GAVStrategyFactory;
import org.sourcepit.mavenizor.maven.tycho.TychoProjectBundleResolver;
import org.sourcepit.mavenizor.state.OsgiStateBuilder;

import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

/**
 * @requiresDependencyResolution compile
 * @goal mavenize
 * @phase package
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class MavenizorMojo extends AbstractMavenizorMojo
{
   private final class MultiProperty extends HashSet<String>
   {
      private static final long serialVersionUID = 1L;

      @Override
      public boolean equals(Object o)
      {
         return isEmpty() || contains(o);
      }
   }

   @Inject
   protected Logger logger;

   /** @parameter expression="${session}" */
   private MavenSession session;

   /** @parameter */
   private Map<String, String> options;

   /** @parameter expression="${project.build.directory}/mavenize" */
   protected File work;

   @Inject
   @Named("tycho-project")
   private BundleResolver bundleResolver;

   @Inject
   private Mavenizor mavenizor;

   @Inject
   private GAVStrategyFactory gavStrategyFactory;

   @Inject
   private MutableBeanLocator locator;

   @Override
   protected void doExecute() throws PipedException
   {
      logger.info("Hello :-)");

      final OsgiStateBuilder stateBuilder = new OsgiStateBuilder(TychoProjectUtils.class.getClassLoader());
      addPlatformProperties(session, stateBuilder);
      addBundles(stateBuilder);

      final State state = stateBuilder.getState();
      state.resolve(false);

      final Mavenizor.Request request = new Mavenizor.Request();
      populateDefaults(request);
      if (options != null)
      {
         request.getOptions().putAll(options);
      }
      request.setState(state);
      request.setGAVStrategy(gavStrategyFactory.newGAVStrategy(new GAVStrategyFactory.Request()));

      final Mavenizor.Result result = mavenizor.mavenize(request);

      postProcess(result);

      logger.info("awesome!");
   }

   private void populateDefaults(final Mavenizor.Request request)
   {
      request.getOptions().put("workingDir", work.getAbsolutePath());
      request.getOptions().setBoolean("unwrapEmbeddedLibraries", true);
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private BundleConverter locateArtifactDescriptorsStrategy(String name)
   {
      final Key<BundleConverter> key;

      // final Named bindingName;
      if (name == null || "ignore".equals(name))
      {
         // bindingName = Names.named(DefaultArtifactDescriptorsStrategy.class.getName());
         key = (Key) Key.get(DefaultBundleConverter.class);
      }
      else
      {
         key = Key.get(BundleConverter.class, Names.named(name));
      }


      final Iterator<BeanEntry<Annotation, BundleConverter>> iterator = locator.locate(key).iterator();
      while (iterator.hasNext())
      {
         final BeanEntry<Annotation, ? extends BundleConverter> beanEntry = (BeanEntry<Annotation, ? extends BundleConverter>) iterator
            .next();
         return beanEntry.getValue();
      }

      return null;
   }

   protected void postProcess(Result result)
   {
   }

   private void addPlatformProperties(final MavenSession session, final OsgiStateBuilder stateBuilder)
   {
      final MavenProject project = session.getCurrentProject();

      final TargetPlatformConfiguration configuration = TychoProjectUtils.getTargetPlatformConfiguration(project);

      final String eeName = configuration.getExecutionEnvironment();
      if (eeName == null)
      {
         stateBuilder.addSystemExecutionEnvironmentProperties();
      }
      else
      {
         stateBuilder.addExecutionEnvironmentProperties(eeName);
      }

      final Set<String> os = new MultiProperty();
      final Set<String> ws = new MultiProperty();
      final Set<String> arch = new MultiProperty();
      for (TargetEnvironment targetEnvironment : configuration.getEnvironments())
      {
         os.add(targetEnvironment.getOs());
         ws.add(targetEnvironment.getWs());
         arch.add(targetEnvironment.getArch());
      }

      final Map<String, Object> targetMap = new HashMap<String, Object>();
      targetMap.put(OsgiStateBuilder.OSGI_OS, os);
      targetMap.put(OsgiStateBuilder.OSGI_WS, ws);
      targetMap.put(OsgiStateBuilder.OSGI_ARCH, arch);
      stateBuilder.addPlatformProperties(targetMap);
   }

   private void addBundles(final OsgiStateBuilder stateBuilder)
   {
      bundleResolver.resolve(session, new TychoProjectBundleResolver.Handler()
      {
         public void resolved(File bundleLocation)
         {
            stateBuilder.addBundle(bundleLocation);
         }
      });
   }
}
