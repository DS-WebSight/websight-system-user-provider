package pl.ds.websight.system.user.provider.service;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

public interface SystemUserProvider {

    /**
     * Provides resource resolver for configured system user. If the system user does not exists it's created and configured ACLs are applied.
     * <p>
     * Service User Mapper is registered for bundle providing SystemUserConfig implementation class used as second parameter.
     * ResourceResolverFactory instance used as param must be provided by same bundle as config param to get ResourceResolver
     * properly - bundle used to find Service User Mapper is taken from ResourceResolverFactory instance.
     *
     * @param resourceResolverFactory ResourceResolverFactory instance referenced from same bundle that provides SystemUserConfig param
     * @param config                  system user config instance
     * @return
     * @throws LoginException
     */
    ResourceResolver getSystemUserResourceResolver(ResourceResolverFactory resourceResolverFactory, SystemUserConfig config) throws LoginException;
}
