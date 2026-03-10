package com.smartjkpdf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "app.storage.dir=./test-files")
class SmartJKPDFApplicationTests {
    @Test
    void contextLoads() {}
}
