package pl.ds.websight.system.user.provider.service;

import java.util.Map;

public interface SystemUserConfig {

    /**
     * Id od the system user
     * @return
     */
    String getSystemUserId();

    /**
     * Privileges of a system user where key is a path to add acl entry on. The value is an array of privileges names
     * to be added on given path (e.g jcr:read, jcr:write)
     * @return
     */
    Map<String, String[]> getPrivileges();
}
