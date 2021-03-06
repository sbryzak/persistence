/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.seam.persistence.util;

import java.util.Hashtable;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this has been ported to seam and hacked to make it work
 * 
 * we need to figure out what we are doing with JNDI in seam 3 and make this go
 * away
 * 
 * @author stuart
 * 
 */
public final class NamingUtils
{
   private static final Logger log = LoggerFactory.getLogger(NamingUtils.class);
   private static Hashtable initialContextProperties;

   private static InitialContext initialContext;

   public static InitialContext getInitialContext(Hashtable<String, String> props) throws NamingException
   {
      if (props == null)
      {
         // throw new
         // IllegalStateException("JNDI properties not initialized, Seam was not started correctly");
      }
      props = new Hashtable<String, String>();

      if (log.isDebugEnabled())
      {
         log.debug("JNDI InitialContext properties:" + props);
      }

      try
      {
         return props.size() == 0 ? new InitialContext() : new InitialContext(props);
      }
      catch (NamingException e)
      {
         log.debug("Could not obtain initial context");
         throw e;
      }

   }

   public static InitialContext getInitialContext() throws NamingException
   {
      if (initialContext == null)
         initInitialContext();

      return initialContext;
   }

   private static synchronized void initInitialContext() throws NamingException
   {
      if (initialContext == null)
      {
         initialContext = getInitialContext(initialContextProperties);
      }
   }

   private NamingUtils()
   {
   }

   public static void setInitialContextProperties(Hashtable initialContextProperties)
   {
      NamingUtils.initialContextProperties = initialContextProperties;
      initialContext = null;
   }

   public static Hashtable getInitialContextProperties()
   {
      return initialContextProperties;
   }
}
