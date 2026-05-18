package pl.cramber.assetstore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VpnApiResponse {
    private SecurityData security;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SecurityData {
        @JsonProperty("is_vpn")
        private boolean vpn;

        @JsonProperty("is_proxy")
        private boolean proxy;

        @JsonProperty("is_tor")
        private boolean tor;

        @JsonProperty("is_relay")
        private boolean relay;

        @JsonProperty("is_anonymous")
        private boolean anonymous;

        public boolean isSuspicious() {
            return anonymous || vpn || proxy || tor || relay;
        }
    }
}