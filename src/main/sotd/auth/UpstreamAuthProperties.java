package sotd.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sotd.upstream-auth")
public class UpstreamAuthProperties {

    private boolean enabled = true;
    private String sharedSecret;
    private String issuer;
    private String audience;
    private Duration clockSkew = Duration.ofSeconds(30);
    private String queryParameterName = "upstreamAuth";
    private String headerName = "Authorization";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }

    public String getQueryParameterName() {
        return queryParameterName;
    }

    public void setQueryParameterName(String queryParameterName) {
        this.queryParameterName = queryParameterName;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
