package com.netflix.eureka;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

/**
 * Jersey 2 Feature that registers HK2 bindings for Eureka server context.
 * This bridges the EurekaServerContext (set up by EurekaBootStrap via ServletContextListener)
 * to Jersey 2's HK2 dependency injection framework.
 */
public class EurekaServerFeature implements Feature {

    @Override
    public boolean configure(FeatureContext context) {
        context.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bindFactory(EurekaServerContextFactory.class).to(EurekaServerContext.class);
            }
        });
        return true;
    }

    private static class EurekaServerContextFactory implements Factory<EurekaServerContext> {
        @Override
        public EurekaServerContext provide() {
            return EurekaServerContextHolder.getInstance().getServerContext();
        }

        @Override
        public void dispose(EurekaServerContext instance) {
            // no-op
        }
    }
}
