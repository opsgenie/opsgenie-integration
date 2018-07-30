package com.example.demo.kube;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class WatchExample {
    public static void main(String[] args) throws IOException, ApiException {
        ApiClient client = Config.defaultClient();
        client.getHttpClient().setReadTimeout(60, TimeUnit.SECONDS);
        Configuration.setDefaultApiClient(client);
        RestTemplate restTemplate = new RestTemplate();
        CoreV1Api api = new CoreV1Api();
        String opsgenieApiKey = "your integration api key";
        String url = "http://localhost:9000/v1/json/integrations/webhooks/kubernetes?apiKey=" + opsgenieApiKey;

        Watch<V1Namespace> watch =
                Watch.createWatch(
                        client,
                        api.listNamespaceCall(
                                null, null, null, null, null, 5, null, null, Boolean.TRUE, null, null),
                        new TypeToken<Watch.Response<V1Namespace>>() {}.getType());

        try {
            for (Watch.Response<V1Namespace> item : watch) {
                System.out.printf("%s : %s%n", item.type, item.object.getMetadata().getName());
                restTemplate.postForObject(url,item, item.getClass());
            }
        } finally {
            watch.close();
        }
    }
}