package com.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Crpt {

    private final Lock lock = new ReentrantLock();
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private int requestCount = 0;
    private final int requestLimit;
    private final long intervalInSeconds;

    public Crpt(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.intervalInSeconds = timeUnit.toSeconds(1);
        scheduler.scheduleAtFixedRate(this::resetRequestCount, 0, intervalInSeconds, TimeUnit.SECONDS);
    }
    public static void main(String[] args) {
        // в методе мэйна создает класс Crpt с ограничением 1 запрос в секунду
        // после создаем сам документ для отправки и фиктивную подпись
        // и в цикле for 5 раз отправляем post запрос
        Crpt crpt = new Crpt(TimeUnit.SECONDS, 1);

        DocumentRequest documentRequest = new DocumentRequest();

        String signature = "dummy_signature";

        for (int i = 1; i <= 5; i++) {
            crpt.createDocument(documentRequest, signature);
        }
        crpt.shutdown();
    }
    private void resetRequestCount() {
        synchronized (lock) {
            requestCount = 0;
        }
    }

    public void createDocument(DocumentRequest documentRequest, String signature) {
            synchronized (lock) {
                // если количество отправок будет превышено то вызовется метод scheduleRetry
                // для повторной отправки запросов используется класс ScheduledExecutorService, чтобы запросы откладывались,
                // а не выкидывали ошибку
                if (requestCount < requestLimit) {
                    requestCount++;
                    // в методе документ для отправки преобразуется в json
                    String jsonRequestBody = prepareJsonRequest(documentRequest, signature);
                    try {
                        URL url = new URL(apiUrl);

                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        conn.setRequestProperty("Content-Length", Integer.toString(jsonRequestBody.length()));
                        conn.setUseCaches(false);

                        try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                            dos.writeBytes(jsonRequestBody);
                        }

                        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                                conn.getInputStream()))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                System.out.println(line);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println("Document created successfully");

                }
                else {
                    scheduleRetry(documentRequest, signature);
                }
            }
    }
    private void scheduleRetry(DocumentRequest documentRequest, String signature) {
        scheduler.schedule(() -> {
            requestCount = 0;
            createDocument(documentRequest, signature);
        }, intervalInSeconds, TimeUnit.SECONDS);
    }

    private String prepareJsonRequest(DocumentRequest documentRequest, String signature) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("document", documentRequest.toJsonObject());
        requestBody.put("signature", signature);

        return requestBody.toString();
    }
    public void shutdown() {

        scheduler.shutdown();
    }

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class DocumentRequest {
        private Description description = new Description();
        private String doc_id = "";
        private String doc_status = "";
        private String doc_type = "";
        private boolean importRequest = false;
        private String owner_inn = "";
        private String participant_inn = "";
        private String producer_inn = "";
        private String production_date = "";
        private String production_type = "";
        private List<Product> products = new ArrayList<>();
        private String reg_date = "";
        private String reg_number = "";
        public JSONObject toJsonObject() {
            JSONObject documentJson = new JSONObject();

            JSONObject descriptionJson = new JSONObject();
            descriptionJson.put("participantInn", description.getParticipantInn());

            documentJson.put("description", descriptionJson);
            documentJson.put("doc_id", doc_id);
            documentJson.put("doc_status", doc_status);
            documentJson.put("doc_type", doc_type);
            documentJson.put("importRequest", importRequest);
            documentJson.put("owner_inn", owner_inn);
            documentJson.put("participant_inn", participant_inn);
            documentJson.put("producer_inn", producer_inn);
            documentJson.put("production_date", production_date);
            documentJson.put("production_type", production_type);

            JSONArray productsJsonArray = new JSONArray();
            for (Product product : products) {
                JSONObject productJson = new JSONObject();
                productJson.put("certificate_document", product.getCertificate_document());
                productJson.put("certificate_document_date", product.getCertificate_document_date());
                productJson.put("certificate_document_number", product.getCertificate_document_number());
                productJson.put("owner_inn", product.getOwner_inn());
                productJson.put("producer_inn", product.getProducer_inn());
                productJson.put("production_date", product.getProduction_date());
                productJson.put("tnved_code", product.getTnved_code());
                productJson.put("uit_code", product.getUit_code());
                productJson.put("uitu_code", product.getUitu_code());
                productsJsonArray.put(productJson);
            }

            documentJson.put("products", productsJsonArray);
            documentJson.put("reg_date", reg_date);
            documentJson.put("reg_number", reg_number);

            return documentJson;
        }
        @Getter @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        private class Description
        {
            private String participantInn;

        }
        @Getter @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        private static class Product {
            private String certificate_document = "";
            private String certificate_document_date = "";
            private String certificate_document_number = "";
            private String owner_inn = "";
            private String producer_inn = "";
            private String production_date = "";
            private String tnved_code = "";
            private String uit_code = "";
            private String uitu_code = "";
        }

    }


}
