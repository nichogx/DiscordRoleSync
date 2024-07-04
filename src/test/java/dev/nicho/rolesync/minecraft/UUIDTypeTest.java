package dev.nicho.rolesync.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UUIDTypeTest {

    @Test
    void testGetTypeForUUID() {
        assertEquals(UUIDType.AUTHENTICATED, UUIDType.getTypeForUUID("852cd554-dedd-4e11-b539-873c70884c42"));
        assertEquals(UUIDType.NOT_AUTHENTICATED, UUIDType.getTypeForUUID("0b3bf5fe-f027-3b4d-8b92-d8f5f8c56e2b"));
        assertEquals(UUIDType.BEDROCK, UUIDType.getTypeForUUID("00000000-0000-0000-0009-01f7e2b1b6ca"));
    }
}