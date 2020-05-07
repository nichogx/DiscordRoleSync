package dev.nicho.rolesync.permissionapis;

public class PermPluginNotFoundException extends Exception {

    public PermPluginNotFoundException() {
        super();
    }

    public PermPluginNotFoundException(String err) {
        super(err);
    }
}
