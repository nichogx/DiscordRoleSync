package dev.nicho.rolesync.permissionapis;

import javax.annotation.Nullable;
import java.util.List;

public abstract class PermissionsAPI {

    protected List<String> managedPerms = null;

    public abstract void setPermissions(String uuid, @Nullable List<String> permissions);
}
