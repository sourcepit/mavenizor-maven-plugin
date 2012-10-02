/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;
public enum ConvertionDirective
{
   AUTO_DETECT, MAVENIZE, OMIT, IGNORE, REPLACE;

   private final String literal;

   private ConvertionDirective()
   {
      this.literal = name().toLowerCase();
   }

   public final String literal()
   {
      return literal;
   }

   public static ConvertionDirective valueOfLiteral(String literal)
   {
      for (ConvertionDirective mode : values())
      {
         if (mode.literal().equals(literal))
         {
            return mode;
         }
      }
      return null;
   }
}