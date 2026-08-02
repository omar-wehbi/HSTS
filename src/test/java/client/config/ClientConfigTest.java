package client.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClientConfigTest {

    @Test
    void loadReadsHostAndPortFromExternalFile() throws Exception {
        Path tempDir = Files.createTempDirectory("hsts-client-config");
        Path props = tempDir.resolve("client.properties");
        Files.writeString(props, """
                server.host=192.168.1.50
                server.port=5600
                """, StandardCharsets.UTF_8);

        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            ClientConfig.Settings settings = ClientConfig.load();
            assertThat(settings.host()).isEqualTo("192.168.1.50");
            assertThat(settings.port()).isEqualTo(5600);
        } finally {
            if (previous != null) {
                System.setProperty("user.dir", previous);
            } else {
                System.clearProperty("user.dir");
            }
        }
    }

    @Test
    void loadFallsBackToDefaultPortWhenInvalid() throws Exception {
        Path tempDir = Files.createTempDirectory("hsts-client-config-bad-port");
        Path props = tempDir.resolve("client.properties");
        Files.writeString(props, """
                server.host=demo-host
                server.port=not-a-number
                """, StandardCharsets.UTF_8);

        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            ClientConfig.Settings settings = ClientConfig.load();
            assertThat(settings.host()).isEqualTo("demo-host");
            assertThat(settings.port()).isEqualTo(5555);
        } finally {
            if (previous != null) {
                System.setProperty("user.dir", previous);
            } else {
                System.clearProperty("user.dir");
            }
        }
    }

    @Test
    void loadTrimsBlankHostWhitespace() throws Exception {
        Path tempDir = Files.createTempDirectory("hsts-client-config-host-trim");
        Path props = tempDir.resolve("client.properties");
        Files.writeString(props, """
                server.host=  trim-me.example  
                server.port=5601
                """, StandardCharsets.UTF_8);

        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            ClientConfig.Settings settings = ClientConfig.load();
            assertThat(settings.host()).isEqualTo("trim-me.example");
            assertThat(settings.port()).isEqualTo(5601);
        } finally {
            if (previous != null) {
                System.setProperty("user.dir", previous);
            } else {
                System.clearProperty("user.dir");
            }
        }
    }
}
