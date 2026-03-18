package sotd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls whether strict startup validation should fail the app on missing production-critical config.
 */
@ConfigurationProperties(prefix = "sotd.startup-validation")
public class StartupValidationProperties {

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
