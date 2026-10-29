package dev.nicho.rolesync.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class XboxAPITest {

    @Test
    void testSearchNameRejectsEmptyGamertags() throws Exception {
        // Returns before making a request
        XboxAPI xbox = new XboxAPI();

        for (String name : new String[]{".", "..", "._", ".__."}) {
            assertNull(xbox.searchName(name), name);
        }
    }
}
