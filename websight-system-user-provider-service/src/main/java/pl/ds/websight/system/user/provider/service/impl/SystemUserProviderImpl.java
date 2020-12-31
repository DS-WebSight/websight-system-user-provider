package pl.ds.websight.system.user.provider.service.impl;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ds.websight.system.user.provider.service.SystemUserConfig;
import pl.ds.websight.system.user.provider.service.SystemUserProvider;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import java.io.IOException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.apache.sling.api.resource.ResourceResolverFactory.SUBSERVICE;

@Component(service = SystemUserProvider.class)
public class SystemUserProviderImpl implements SystemUserProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SystemUserProviderImpl.class);

    private static final String SYSTEM_USER_MAPPING_FACTORY_PID = "org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended";

    private static final String PN_USER_MAPPING = "user.mapping";

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private ConfigurationAdmin configAdmin;

    private Set<String> createdUsers = new HashSet<>();

    @Override
    public ResourceResolver getSystemUserResourceResolver(ResourceResolverFactory resourceResolverFactory,
                                                          SystemUserConfig systemUserConfig) throws LoginException {
        String systemUserId = systemUserConfig.getSystemUserId();
        if (!createdUsers.contains(systemUserId)) {
            setupSystemUser(systemUserConfig);
        }
        Map<String, Object> serviceUserAuthInfo = Collections.singletonMap(SUBSERVICE, systemUserId);
        // use ResourceResolverFactory from param that must be referenced from same bundle that deliver systemUserConfig implementation
        return resourceResolverFactory.getServiceResourceResolver(serviceUserAuthInfo);
    }

    private void setupSystemUser(SystemUserConfig systemUserConfig) {
        String systemUserId = systemUserConfig.getSystemUserId();
        if (createServiceUser(systemUserId)) {
            createServiceMapperConfig(systemUserConfig);
            setupAcl(systemUserConfig);
            createdUsers.add(systemUserId);
        }
    }

    private boolean createServiceUser(String userId) {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null)) {
            UserManager userManager = AccessControlUtil.getUserManager(resourceResolver.adaptTo(Session.class));
            Authorizable authorizable = userManager.getAuthorizable(userId);
            if (authorizable != null) {
                LOG.debug("Skipping creating system user. User already exists");
                return false;
            } else {
                userManager.createSystemUser(userId, null);
                LOG.info("System user created with id: " + userId);
                resourceResolver.commit();
                return true;
            }
        } catch (RepositoryException | LoginException | PersistenceException e) {
            LOG.warn("Failed to create system user", e);
            return false;
        }
    }

    private void setupAcl(SystemUserConfig config) {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null)) {
            Session session = resourceResolver.adaptTo(Session.class);
            for (Map.Entry<String, String[]> entry : config.getPrivileges().entrySet()) {
                Privilege[] jcrPrivileges = AccessControlUtils.privilegesFromNames(session, entry.getValue());
                addAcl(session, config.getSystemUserId(), entry.getKey(), jcrPrivileges);
            }
            if (resourceResolver.hasChanges()) {
                resourceResolver.commit();
            }
        } catch (RepositoryException | LoginException | PersistenceException e) {
            LOG.warn("Failed to setup ACL for system user", e);
        }
    }

    private void addAcl(Session session, String userId, String path, Privilege[] privileges) throws RepositoryException {
        AccessControlManager acManager = session.getAccessControlManager();
        JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(session, path);
        acl.addEntry(() -> userId, privileges, true);
        acManager.setPolicy(path, acl);
    }

    private void createServiceMapperConfig(SystemUserConfig systemUserConfig) {
        try {
            Bundle bundle = FrameworkUtil.getBundle(systemUserConfig.getClass());
            Configuration config = configAdmin.createFactoryConfiguration(SYSTEM_USER_MAPPING_FACTORY_PID, null);
            Dictionary properties = new Properties();
            properties.put(PN_USER_MAPPING, bundle.getSymbolicName() + "=" + systemUserConfig.getSystemUserId());
            config.update(properties);
        } catch (IOException e) {
            LOG.warn("Failed to create service user mapper entry", e);
        }
    }
}