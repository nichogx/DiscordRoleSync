package dev.nicho.rolesync.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserSearchTest {

    @Test
    void testUUIDAddDashes() {
        String uuid = "00000000-0000-0000-0000-000000000000";
        assertEquals(uuid, UserSearch.uuidAddDashes(uuid.replace("-", "")));
    }
}