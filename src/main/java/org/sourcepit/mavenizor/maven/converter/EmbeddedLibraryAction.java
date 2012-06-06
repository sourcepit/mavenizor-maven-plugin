/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import org.sourcepit.common.maven.model.MavenArtifact;

public class EmbeddedLibraryAction
{
   public enum Action
   {
      AUTO_DETECT, MAVENIZE, IGNORE, REPLACE;

      private final String literal;

      private Action()
      {
         this.literal = name().toLowerCase();
      }

      public final String literal()
      {
         return literal;
      }

      public static Action valueOfLiteral(String literal)
      {
         for (Action mode : values())
         {
            if (mode.literal().equals(literal))
            {
               return mode;
            }
         }
         return null;
      }
   }

   private final Action action;

   private final MavenArtifact replacement;

   public EmbeddedLibraryAction(Action action, MavenArtifact replacement)
   {
      this.action = action;
      this.replacement = replacement;
   }

   public Action getAction()
   {
      return action;
   }

   public MavenArtifact getReplacement()
   {
      return replacement;
   }

}
