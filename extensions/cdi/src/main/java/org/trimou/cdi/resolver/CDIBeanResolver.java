/*
 * Copyright 2013 Martin Kouba
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
package org.trimou.cdi.resolver;

import static org.trimou.engine.priority.Priorities.rightAfter;

import java.util.Collections;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.trimou.cdi.BeanManagerLocator;
import org.trimou.engine.cache.ComputingCache;
import org.trimou.engine.config.ConfigurationKey;
import org.trimou.engine.config.SimpleConfigurationKey;
import org.trimou.engine.priority.WithPriority;
import org.trimou.engine.resolver.AbstractResolver;
import org.trimou.engine.resolver.Hints;
import org.trimou.engine.resolver.ResolutionContext;
import org.trimou.engine.resource.ReleaseCallback;

import com.google.common.base.Optional;

/**
 * CDI beans resolver. Note that only beans with a name (i.e. annotated with
 * {@link Named}) are resolvable.
 *
 * Similarly to the CDI and Unified EL integration, instance of a dependent bean
 * exists to service just a single tag evaluation.
 *
 * @author Martin Kouba
 */
public class CDIBeanResolver extends AbstractResolver {

    private static final Logger logger = LoggerFactory
            .getLogger(CDIBeanResolver.class);

    public static final String COMPUTING_CACHE_CONSUMER_ID = CDIBeanResolver.class
            .getName();

    public static final int CDI_BEAN_RESOLVER_PRIORITY = rightAfter(WithPriority.EXTENSION_RESOLVERS_DEFAULT_PRIORITY);

    public static final ConfigurationKey BEAN_CACHE_MAX_SIZE_KEY = new SimpleConfigurationKey(
            CDIBeanResolver.class.getName() + ".beanCacheMaxSize", 1000l);

    private BeanManager beanManager;

    private ComputingCache<String, Optional<Bean<?>>> beanCache;

    /**
     *
     */
    public CDIBeanResolver() {
        this(CDI_BEAN_RESOLVER_PRIORITY);
    }

    /**
     *
     * @param priority
     */
    public CDIBeanResolver(int priority) {
        super(priority);
    }

    /**
     *
     * @param beanManager
     * @param priority
     */
    public CDIBeanResolver(BeanManager beanManager, int priority) {
        this(priority);
        this.beanManager = beanManager;
    }

    /**
     *
     * @param beanManager
     */
    public CDIBeanResolver(BeanManager beanManager) {
        this(CDI_BEAN_RESOLVER_PRIORITY);
        this.beanManager = beanManager;
    }

    @Override
    public Object resolve(Object contextObject, String name,
            ResolutionContext context) {

        if (contextObject != null) {
            return null;
        }

        Optional<Bean<?>> bean = beanCache.get(name);

        if (!bean.isPresent()) {
            // Unsuccessful lookup already performed
            return null;
        }
        return getReference(bean.get(), context);
    }

    @Override
    public void init() {

        if (beanManager == null) {
            beanManager = BeanManagerLocator.locate();
        }

        if (beanManager == null) {
            throw new IllegalStateException(
                    "BeanManager not set - invalid resolver configuration");
        }
        // Init cache max size
        long beanCacheMaxSize = configuration
                .getLongPropertyValue(BEAN_CACHE_MAX_SIZE_KEY);

        beanCache = configuration.getComputingCacheFactory().create(
                COMPUTING_CACHE_CONSUMER_ID,
                new ComputingCache.Function<String, Optional<Bean<?>>>() {
                    @Override
                    public Optional<Bean<?>> compute(String key) {

                        Set<Bean<?>> beans = beanManager.getBeans(key);

                        // Check required for CDI 1.0
                        if (beans == null || beans.isEmpty()) {
                            return Optional.absent();
                        }

                        try {
                            return Optional.<Bean<?>> of((Bean<?>) beanManager
                                    .resolve(beans));
                        } catch (AmbiguousResolutionException e) {
                            logger.warn(
                                    "An ambiguous EL name exists [name: {}]",
                                    key);
                            return Optional.absent();
                        }

                    }
                }, null, beanCacheMaxSize, null);
        logger.debug("Initialized [beanCacheMaxSize: {}]", beanCacheMaxSize);
    }

    @Override
    public Set<ConfigurationKey> getConfigurationKeys() {
        return Collections
                .<ConfigurationKey> singleton(BEAN_CACHE_MAX_SIZE_KEY);
    }

    private <T> Object getReference(Bean<T> bean, ResolutionContext context) {

        CreationalContext<T> creationalContext = beanManager
                .createCreationalContext(bean);

        if (Dependent.class.equals(bean.getScope())) {
            T reference = bean.create(creationalContext);
            context.registerReleaseCallback(new DependentDestroyCallback<T>(
                    bean, creationalContext, reference));
            return reference;

        } else {
            return beanManager.getReference(bean, Object.class,
                    creationalContext);
        }
    }

    @Override
    public Hint createHint(Object contextObject, String name,
            ResolutionContext context) {
        if (contextObject == null) {
            Optional<Bean<?>> bean = beanCache.getIfPresent(name);
            if (bean.isPresent()) {
                return new CDIBeanHint(bean.get());
            }
        }
        return Hints.INAPPLICABLE_HINT;
    }

    static class DependentDestroyCallback<T> implements ReleaseCallback {

        private final Bean<T> bean;

        private final CreationalContext<T> creationalContext;

        private final T instance;

        private DependentDestroyCallback(Bean<T> bean,
                CreationalContext<T> creationalContext, T instance) {
            this.bean = bean;
            this.creationalContext = creationalContext;
            this.instance = instance;
        }

        @Override
        public void release() {
            bean.destroy(instance, creationalContext);
        }

    }

    private class CDIBeanHint implements Hint {

        private final Bean<?> bean;

        /**
         *
         * @param bean
         */
        public CDIBeanHint(Bean<?> bean) {
            this.bean = bean;
        }

        @Override
        public Object resolve(Object contextObject, String name,
                ResolutionContext context) {
            if (contextObject != null) {
                return null;
            }
            return CDIBeanResolver.this.getReference(bean, context);
        }

    }

}
