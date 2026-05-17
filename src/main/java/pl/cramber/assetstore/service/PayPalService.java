package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayPalService {

    private final Environment env;
    private final RestTemplate restTemplate = new RestTemplate();

    public String getClientIdForEnv(String envKey) {
        if (envKey == null || envKey.isEmpty()) return null;
        return env.getProperty(envKey + "_PAYPAL_CLIENT_ID");
    }

    private String getAccessToken(String envKey) {
        String clientId = env.getProperty(envKey + "_PAYPAL_CLIENT_ID");
        String secret = env.getProperty(envKey + "_PAYPAL_CLIENT_SECRET");
        String mode = env.getProperty("PAYPAL_MODE", "sandbox");
        String baseUrl = "live".equals(mode) ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, secret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/v1/oauth2/token", new HttpEntity<>(body, headers), Map.class);
        return (String) response.getBody().get("access_token");
    }

    public Map<String, String> createOrder(String envKey, Double amount, String currency) {
        String token = getAccessToken(envKey);
        String mode = env.getProperty("PAYPAL_MODE", "sandbox");
        String baseUrl = "live".equals(mode) ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String amountStr = String.format(Locale.US, "%.2f", amount);
        String payload = "{\"intent\":\"CAPTURE\",\"purchase_units\":[{\"amount\":{\"currency_code\":\"" + currency + "\",\"value\":\"" + amountStr + "\"}}]}";

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/v2/checkout/orders", new HttpEntity<>(payload, headers), Map.class);
        Map<String, Object> body = response.getBody();
        String orderId = (String) body.get("id");
        String approveLink = "";

        List<Map<String, String>> links = (List<Map<String, String>>) body.get("links");
        for (Map<String, String> link : links) {
            if ("approve".equals(link.get("rel"))) {
                approveLink = link.get("href");
            }
        }
        return Map.of("orderId", orderId, "approveLink", approveLink);
    }

    public Map<String, String> captureOrder(String envKey, String orderId) {
        String token = getAccessToken(envKey);
        String mode = env.getProperty("PAYPAL_MODE", "sandbox");
        String baseUrl = "live".equals(mode) ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/v2/checkout/orders/" + orderId + "/capture", new HttpEntity<>("{}", headers), Map.class);
            Map<String, Object> body = response.getBody();
            String status = (String) body.get("status");

            if ("COMPLETED".equals(status)) {
                List<Map<String, Object>> purchaseUnits = (List<Map<String, Object>>) body.get("purchase_units");
                Map<String, Object> payments = (Map<String, Object>) purchaseUnits.get(0).get("payments");
                List<Map<String, Object>> captures = (List<Map<String, Object>>) payments.get("captures");
                String captureId = (String) captures.get(0).get("id");
                return Map.of("status", "COMPLETED", "captureId", captureId);
            }
        } catch (Exception e) {
            return Map.of("status", "FAILED");
        }
        return Map.of("status", "FAILED");
    }

    public boolean refundCapture(String envKey, String captureId) {
        String token = getAccessToken(envKey);
        String mode = env.getProperty("PAYPAL_MODE", "sandbox");
        String baseUrl = "live".equals(mode) ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/v2/payments/captures/" + captureId + "/refund", new HttpEntity<>("{}", headers), Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}