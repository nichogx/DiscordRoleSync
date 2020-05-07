package dev.nicho.rolesync.permissionapis;

import java.util.List;

public abstract class PermissionsAPI {

    public abstract void setPermissions(String uuid, List<String> permissions, List<String> managed);
}
