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
package org.jboss.seam.persistence.transaction;

import javax.transaction.Status;

import org.jboss.seam.persistence.util.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs work in a JTA transaction.
 * 
 * @author Gavin King
 */
public abstract class Work<T>
{
   private static final Logger log = LoggerFactory.getLogger(Work.class);

   protected abstract T work() throws Exception;

   protected boolean isNewTransactionRequired(boolean transactionActive)
   {
      return !transactionActive;
   }

   public final T workInTransaction(org.jboss.seam.persistence.transaction.SeamTransaction transaction) throws Exception
   {
      boolean transactionActive = transaction.isActiveOrMarkedRollback() || transaction.isRolledBack();
      // TODO: temp workaround, what should we really do in this case??
      boolean newTransactionRequired = isNewTransactionRequired(transactionActive);

      try
      {
         if (newTransactionRequired)
         {
            log.debug("beginning transaction");
            transaction.begin();
         }

         T result = work();
         if (newTransactionRequired)
         {
            if (transaction.isMarkedRollback())
            {
               log.debug("rolling back transaction");
               transaction.rollback();
            }
            else
            {
               log.debug("committing transaction");
               transaction.commit();
            }
         }
         return result;
      }
      catch (Exception e)
      {
         if (newTransactionRequired && transaction.getStatus() != Status.STATUS_NO_TRANSACTION)
         {
            if (ExceptionUtil.exceptionCausesRollback(e))
            {
               log.debug("rolling back transaction");
               transaction.rollback();
            }
            else
            {
               log.debug("committing transaction after ApplicationException(rollback=false):" + e.getMessage());
               transaction.commit();
            }
         }
         else if (transaction.getStatus() != Status.STATUS_NO_TRANSACTION && ExceptionUtil.exceptionCausesRollback(e))
         {
            transaction.setRollbackOnly();
         }
         throw e;
      }
   }

}
