/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import javax.inject.Named;
import javax.validation.constraints.NotNull;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.utils.path.PathMatcher;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.state.Requirement;

@Named
public class OptionsHelper
{
   enum CompareMode
   {
      MATCH_PATTERN, EQUAL_VALUE
   }

   @NotNull
   public LinkedHashMap<String, String> getBundleOptions(@NotNull BundleDescription bundle,
      @NotNull PropertiesMap options, @NotNull String optionName, @NotNull CompareMode compareMode)
   {
      final LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
      switch (compareMode)
      {
         case MATCH_PATTERN :
            for (Entry<String, String> entry : options.entrySet())
            {
               final String key = entry.getKey();
               if (key.endsWith(optionName))
               {
                  final String keyPattern = key.substring(0, key.length() - optionName.length());
                  if (isMatch(bundle, keyPattern))
                  {
                     String value = entry.getValue();
                     result.put(key, value);
                  }
               }
            }
            break;
         case EQUAL_VALUE :
            String key = bundle.getSymbolicName() + "_" + bundle.getVersion() + optionName;
            String value = options.get(key);
            if (value != null)
            {
               result.put(key, value);
            }

            key = bundle.getSymbolicName() + optionName;
            value = options.get(key);
            if (value != null)
            {
               result.put(key, value);
            }

            key = optionName;
            value = options.get(key);
            if (value != null)
            {
               result.put(key, value);
            }
            break;
         default :
            break;
      }
      return result;
   }

   public boolean getBooleanValue(BundleDescription bundle, PropertiesMap options, String optionName,
      boolean defaultValue)
   {
      final LinkedHashMap<String, String> bundleOptions = getBundleOptions(bundle, options, optionName,
         CompareMode.MATCH_PATTERN);
      for (Entry<String, String> bundleOption : bundleOptions.entrySet())
      {
         return Boolean.valueOf(bundleOption.getValue());
      }
      return defaultValue;
   }

   public boolean isMatch(Requirement requirement, PropertiesMap options, String optionName)
   {
      final LinkedHashMap<String, String> bundleOptions = getBundleOptions(requirement.getFrom(), options, optionName,
         CompareMode.MATCH_PATTERN);
      for (Entry<String, String> bundleOption : bundleOptions.entrySet())
      {
         final String value = bundleOption.getValue();
         if (isMatch(requirement.getTo(), value))
         {
            return true;
         }
      }
      return false;
   }

   private boolean isMatch(BundleDescription bundle, String patterns)
   {
      if (patterns != null)
      {
         final PathMatcher macher = PathMatcher.parse(patterns, ".", ",");

         if (macher.isExclude(bundle.getSymbolicName()))
         {
            return false;
         }

         if (macher.isMatch(bundle.getSymbolicName() + "_" + bundle.getVersion()))
         {
            return true;
         }

         if (macher.isMatch(bundle.getSymbolicName()))
         {
            return true;
         }
      }
      return false;
   }

}
