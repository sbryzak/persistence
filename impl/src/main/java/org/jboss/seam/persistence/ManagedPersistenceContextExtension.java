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
package org.jboss.seam.persistence;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.jboss.weld.extensions.annotated.AnnotatedTypeBuilder;
import org.jboss.weld.extensions.bean.BeanBuilder;
import org.jboss.weld.extensions.literal.AnyLiteral;
import org.jboss.weld.extensions.literal.ApplicationScopedLiteral;
import org.jboss.weld.extensions.literal.DefaultLiteral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension the wraps producer methods/fields that produce an entity manager to
 * turn them into Seam Managed Persistence Contexts.
 * 
 * 
 * @author Stuart Douglas
 * 
 */
public class ManagedPersistenceContextExtension implements Extension
{

   Set<Bean<?>> beans = new HashSet<Bean<?>>();

   private static final Logger log = LoggerFactory.getLogger(ManagedPersistenceContextExtension.class);

   /**
    * loops through the fields on an AnnotatedType looking for a @PersistnceUnit
    * producer field that is annotated {@link SeamManaged}. Then a corresponding
    * smpc bean is created and registered. Any scope declaration on the producer
    * are removed as this is not supported by the spec
    * 
    */
   public <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> event, BeanManager manager)
   {
      AnnotatedTypeBuilder<T> modifiedType = null;
      for (AnnotatedField<? super T> f : event.getAnnotatedType().getFields())
      {
         // look for a seam managed persistence unit declaration on EE resource
         // producer fields
         if (f.isAnnotationPresent(SeamManaged.class) && f.isAnnotationPresent(PersistenceUnit.class) && f.isAnnotationPresent(Produces.class) && EntityManagerFactory.class.isAssignableFrom(f.getJavaMember().getType()))
         {
            if (modifiedType == null)
            {
               modifiedType = new AnnotatedTypeBuilder().readFromType(event.getAnnotatedType());
            }
            Set<Annotation> qualifiers = new HashSet<Annotation>();
            Class<? extends Annotation> scope = Dependent.class;
            // get the qualifier and scope for the new bean
            for (Annotation a : f.getAnnotations())
            {
               if (manager.isQualifier(a.annotationType()))
               {
                  qualifiers.add(a);
               }
               else if (manager.isScope(a.annotationType()))
               {
                  scope = a.annotationType();
               }
            }
            if (qualifiers.isEmpty())
            {
               qualifiers.add(new DefaultLiteral());
            }
            qualifiers.add(AnyLiteral.INSTANCE);
            // we need to remove the scope, they are not nessesarily supported
            // on producer fields
            if (scope != Dependent.class)
            {
               modifiedType.removeFromField(f.getJavaMember(), scope);
            }
            registerManagedPersistenceContext(qualifiers, scope, manager, event.getAnnotatedType().getJavaClass().getClassLoader());
         }
         // now look for producer methods that produce an EntityManagerFactory.
         // This allows the user to manually configure an EntityManagerFactory
         // and return it from a producer method
      }
      // not look for SMPC's that are configured programatically via a producer
      // method the producer method has its scope changes to application scoped
      // this allows for programati config of the SMPC
      for (AnnotatedMethod<? super T> m : event.getAnnotatedType().getMethods())
      {
         // look for a seam managed persistence unit declaration on EE resource
         // producer fields
         if (m.isAnnotationPresent(SeamManaged.class) && m.isAnnotationPresent(Produces.class) && EntityManagerFactory.class.isAssignableFrom(m.getJavaMember().getReturnType()))
         {
            if (modifiedType == null)
            {
               modifiedType = new AnnotatedTypeBuilder().readFromType(event.getAnnotatedType());
            }
            Set<Annotation> qualifiers = new HashSet<Annotation>();
            Class<? extends Annotation> scope = Dependent.class;
            // get the qualifier and scope for the new bean
            for (Annotation a : m.getAnnotations())
            {
               if (manager.isQualifier(a.annotationType()))
               {
                  qualifiers.add(a);
               }
               else if (manager.isScope(a.annotationType()))
               {
                  scope = a.annotationType();
               }
            }
            if (qualifiers.isEmpty())
            {
               qualifiers.add(new DefaultLiteral());
            }
            qualifiers.add(AnyLiteral.INSTANCE);
            // we need to remove the scope, they are not nessesarily supported
            // on producer fields
            modifiedType.removeFromMethod(m.getJavaMember(), scope);
            modifiedType.addToMethod(m.getJavaMember(), ApplicationScopedLiteral.INSTANCE);
            registerManagedPersistenceContext(qualifiers, scope, manager, event.getAnnotatedType().getJavaClass().getClassLoader());
         }
         // now look for producer methods that produce an EntityManagerFactory.
         // This allows the user to manually configure an EntityManagerFactory
         // and return it from a producer method
      }

      if (modifiedType != null)
      {
         event.setAnnotatedType(modifiedType.create());
      }
      // prevent the install of HibernatePersistenceProvider is hibernate is not
      // present
      if (event.getAnnotatedType().getJavaClass() == HibernatePersistenceProvider.class)
      {
         try
         {
            if (Thread.currentThread().getContextClassLoader() != null)
            {
               Thread.currentThread().getContextClassLoader().loadClass("org.hibernate.Session");
            }
            else
            {
               Class.forName("org.hibernate.Session");
            }
         }
         catch (ClassNotFoundException e)
         {
            event.veto();
            log.debug("Hibernate is not availbile", e);
         }
      }
   }

   public void registerManagedPersistenceContext(Set<Annotation> qualifiers, Class<? extends Annotation> scope, BeanManager manager, ClassLoader loader)
   {
      // TODO: this is a massive hack. We need a much better way of doing this
      HibernatePersistenceProvider prov = new HibernatePersistenceProvider();
      Set<Class<?>> additionalInterfaces = prov.getAdditionalEntityManagerInterfaces();
      // create the new bean to be registered later
      ManagedPersistenceContextBeanLifecycle lifecycle = new ManagedPersistenceContextBeanLifecycle(qualifiers, loader, manager, additionalInterfaces);
      AnnotatedTypeBuilder<EntityManager> typeBuilder = new AnnotatedTypeBuilder().setJavaClass(EntityManager.class);
      BeanBuilder<EntityManager> builder = new BeanBuilder<EntityManager>(manager).defineBeanFromAnnotatedType(typeBuilder.create());
      builder.setQualifiers(qualifiers);
      builder.setScope(scope);
      builder.getTypes().add(ManagedPersistenceContext.class);
      builder.getTypes().addAll(additionalInterfaces);
      builder.getTypes().add(Object.class);
      builder.setBeanLifecycle(lifecycle);
      beans.add(builder.create());
   }

   public void afterBeanDiscovery(@Observes AfterBeanDiscovery event)
   {
      for (Bean<?> i : beans)
      {
         event.addBean(i);
      }
   }

}
