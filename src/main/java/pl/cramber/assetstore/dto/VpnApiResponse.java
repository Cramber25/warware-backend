package pl.cramber.assetstore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VpnApiResponse {
    private SecurityData security;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SecurityData {
        private boolean vpn;
        private boolean proxy;
        private boolean tor;
        private boolean relay;

        public boolean isSuspicious() {
            return vpn || proxy || tor || relay;
        }
    }
}