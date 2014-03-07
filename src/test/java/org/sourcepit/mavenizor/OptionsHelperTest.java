/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;

import javax.inject.Inject;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.sisu.launch.InjectedTest;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.Test;
import org.osgi.framework.Version;
import org.sourcepit.common.utils.props.LinkedPropertiesMap;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.OptionsHelper.CompareMode;
import org.sourcepit.mavenizor.state.Requirement;

public class OptionsHelperTest extends InjectedTest
{
   @Inject
   private OptionsHelper optionsHelper;

   @Test
   public void testGetBundleOptions()
   {
      PropertiesMap options = new LinkedPropertiesMap();
      options.put("@foo", "1");
      options.put("org.sourcepit.bundle_1.0.0.qualifier@foo", "2");
      options.put("org.sourcepit.bundle@foo", "3");
      options.put("org.sourcepit.*_1.0.0.qualifier@foo", "4");
      options.put("org.sourcepit.*@foo", "5");
      options.put("org.**@foo", "6");
      options.put("murks@foo", "7");
      options.put("murks.**@foo", "8");

      BundleDescription bundle = mock(BundleDescription.class);
      when(bundle.getSymbolicName()).thenReturn("org.sourcepit.bundle");
      when(bundle.getVersion()).thenReturn(new Version("1.0.0.qualifier"));

      LinkedHashMap<String, String> bundleOptions = optionsHelper.getBundleOptions(bundle, options, "@foo",
         CompareMode.MATCH_PATTERN);
      assertThat(bundleOptions.size(), Is.is(6));
      assertThat(bundleOptions.get("@foo"), IsEqual.equalTo("1"));
      assertThat(bundleOptions.get("org.sourcepit.bundle_1.0.0.qualifier@foo"), IsEqual.equalTo("2"));
      assertThat(bundleOptions.get("org.sourcepit.bundle@foo"), IsEqual.equalTo("3"));
      assertThat(bundleOptions.get("org.sourcepit.*_1.0.0.qualifier@foo"), IsEqual.equalTo("4"));
      assertThat(bundleOptions.get("org.sourcepit.*@foo"), IsEqual.equalTo("5"));
      assertThat(bundleOptions.get("org.**@foo"), IsEqual.equalTo("6"));

      bundleOptions = optionsHelper.getBundleOptions(bundle, options, "@foo", CompareMode.EQUAL_VALUE);
      assertThat(bundleOptions.size(), Is.is(3));
      assertThat(bundleOptions.get("@foo"), IsEqual.equalTo("1"));
      assertThat(bundleOptions.get("org.sourcepit.bundle_1.0.0.qualifier@foo"), IsEqual.equalTo("2"));
      assertThat(bundleOptions.get("org.sourcepit.bundle@foo"), IsEqual.equalTo("3"));
   }

   @Test
   public void testGetBooleanValue()
   {
      BundleDescription bundle = mock(BundleDescription.class);
      when(bundle.getSymbolicName()).thenReturn("org.sourcepit.bundle");
      when(bundle.getVersion()).thenReturn(new Version("1.0.0.qualifier"));

      PropertiesMap options = new LinkedPropertiesMap();
      assertFalse(optionsHelper.getBooleanValue(bundle, options, "@foo", false));
      assertTrue(optionsHelper.getBooleanValue(bundle, options, "@foo", true));

      options = new LinkedPropertiesMap();
      options.put("@foo", "true");
      options.put("@bar", "false");
      assertTrue(optionsHelper.getBooleanValue(bundle, options, "@foo", false));
      assertFalse(optionsHelper.getBooleanValue(bundle, options, "@bar", true));
   }

   @Test
   public void testIsMatch()
   {
      PropertiesMap options = new LinkedPropertiesMap();
      options.put("@foo", "org.**");
      options.put("org.sourcepit.bundle_1.0.0.qualifier@foo", "org.**");
      options.put("org.sourcepit.bundle@foo", "org.**");
      options.put("org.sourcepit.*_1.0.0.qualifier@foo", "org.**");
      options.put("org.sourcepit.*@foo", "org.**");
      options.put("org.**@foo", "org.**");
      options.put("murks@foo", "org.**");
      options.put("murks.**@foo", "org.**");

      BundleDescription from = mock(BundleDescription.class);
      when(from.getSymbolicName()).thenReturn("org.sourcepit.bundle.a");
      when(from.getVersion()).thenReturn(new Version("1.0.0.qualifier"));

      BundleDescription to = mock(BundleDescription.class);
      when(to.getSymbolicName()).thenReturn("org.sourcepit.bundle.b");
      when(to.getVersion()).thenReturn(new Version("1.0.0.qualifier"));

      Requirement requirement = new Requirement();
      requirement.setFrom(from);
      requirement.setTo(to);

      options = new LinkedPropertiesMap();
      options.put("@foo", "org.**");
      assertFalse(optionsHelper.isMatch(requirement, options, "@murks", false));
      assertTrue(optionsHelper.isMatch(requirement, options, "@foo", false));

      options = new LinkedPropertiesMap();
      options.put("@foo", "!org.**");
      assertFalse(optionsHelper.isMatch(requirement, options, "@murks", false));
      assertFalse(optionsHelper.isMatch(requirement, options, "@foo", false));

      options = new LinkedPropertiesMap();
      options.put("**@foo", "org.**");
      assertFalse(optionsHelper.isMatch(requirement, options, "@murks", false));
      assertTrue(optionsHelper.isMatch(requirement, options, "@foo", false));

      options = new LinkedPropertiesMap();
      options.put("!**@foo", "org.**");
      assertFalse(optionsHelper.isMatch(requirement, options, "@murks", false));
      assertFalse(optionsHelper.isMatch(requirement, options, "@foo", false));

      options = new LinkedPropertiesMap();
      options.put("org.sourcepit.bundle.a@foo", "org.sourcepit.bundle.b");
      assertFalse(optionsHelper.isMatch(requirement, options, "@murks", false));
      assertTrue(optionsHelper.isMatch(requirement, options, "@foo", false));

      options = new LinkedPropertiesMap();
      options.put("org.sourcepit.bundle.a_1.0.0.qualifier@foo", "org.sourcepit.bundle.b_1.0.0.qualifier");
      assertFalse(optionsHelper.isMatch(requirement, options, "@murks", false));
      assertTrue(optionsHelper.isMatch(requirement, options, "@foo", false));

      options = new LinkedPropertiesMap();
      options.put("org.**,!org.sourcepit.bundle.b@foo", "org.sourcepit.bundle.b");
      assertFalse(optionsHelper.isMatch(requirement, options, "@murks", false));
      assertTrue(optionsHelper.isMatch(requirement, options, "@foo", false));

      options = new LinkedPropertiesMap();
      options.put("org.sourcepit.bundle.a@foo", "!org.sourcepit.bundle.b");
      assertFalse(optionsHelper.isMatch(requirement, options, "@murks", false));
      assertFalse(optionsHelper.isMatch(requirement, options, "@foo", false));
   }
}
