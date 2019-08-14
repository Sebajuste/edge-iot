package io.edge.iot.certificates.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import io.edge.iot.certificates.CertificateService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class CertificateServiceImpl implements CertificateService {

	private static final String CERTIFICATE_SERVICE_ADDRESS = "edge.certificate.service";

	private final EventBus eventBus;

	public CertificateServiceImpl(Vertx vertx) {
		super();
		this.eventBus = vertx.eventBus();
	}

	@Override
	public void getServerCertificate(String registry, String certName, boolean loadKeys, Handler<AsyncResult<JsonObject>> resultHandler) {

		DeliveryOptions options = new DeliveryOptions()//
				.addHeader("action", "findCertificate");

		JsonObject message = new JsonObject()//
				.put("account", registry)//
				.put("name", certName)//
				.put("loadKeys", loadKeys);

		this.eventBus.<JsonObject> send(CERTIFICATE_SERVICE_ADDRESS, message, options, ar -> {

			if (ar.succeeded()) {

				Message<JsonObject> m = ar.result();

				resultHandler.handle(Future.succeededFuture(m.body()));

			} else {
				resultHandler.handle(Future.failedFuture(ar.cause()));
			}

		});

	}

	@Override
	public void createCertificate(String registry, String certName, boolean cA, Handler<AsyncResult<JsonObject>> resultHandler) {

		Instant notAfter = LocalDateTime.of(2020, 06, 01, 0, 0).toInstant(ZoneOffset.UTC);
		
		DeliveryOptions options = new DeliveryOptions()//
				.addHeader("action", "createCertificate");

		JsonObject claims = new JsonObject()//
				.put("commonName", "ca-mqtt-server")//
				.put("organization", "Edge")//
				.put("organizationalUnit", "Edge-IoT")//
		;
		
		JsonObject certOptions = new JsonObject()//
				.put("ca", cA)//
				.put("notAfter", notAfter)//
				.put("mutualAuthentication", !cA)//
				;

		JsonObject message = new JsonObject()//
				.put("account", registry)//
				.put("name", certName)//
				.put("algorithm", "ECDSA_SHA1")// // RSA_SHA128 ECDSA_SHA1
												// ECDSA_SHA256
				.put("claims", claims)//
				.put("options", certOptions);

		this.eventBus.<JsonObject> send(CERTIFICATE_SERVICE_ADDRESS, message, options, ar -> {

			if (ar.succeeded()) {

				Message<JsonObject> m = ar.result();

				resultHandler.handle(Future.succeededFuture(m.body()));

			} else {
				resultHandler.handle(Future.failedFuture(ar.cause()));
			}

		});

	}

	@Override
	public void createSignedCertificate(String registry, String certName, String caCertName, Handler<AsyncResult<JsonObject>> resultHandler) {

		Future<JsonObject> future = Future.future();

		future.setHandler(resultHandler);

		Instant notAfter = LocalDateTime.of(2020, 06, 01, 0, 0).toInstant(ZoneOffset.UTC);
		
		DeliveryOptions options = new DeliveryOptions()//
				.addHeader("action", "createSignedCertificate");

		JsonObject claims = new JsonObject()//
				.put("commonName", "mqtt-server")//
				.put("organization", "Edge")//
				.put("organizationalUnit", "Edge-IoT")//
		;
		
		JsonObject certOptions = new JsonObject()//
				.put("notAfter", notAfter)//
				.put("mutualAuthentication", true)//
				;

		

		JsonObject message = new JsonObject()//
				.put("account", registry)//
				.put("name", certName)//
				.put("caCertName", caCertName)//
				.put("algorithm", "ECDSA_SHA1")// RSA_SHA128 ECDSA_SHA1 - ECDSA_SHA256
				.put("claims", claims)//
				.put("options", certOptions)//
				;

		this.eventBus.<JsonObject> send(CERTIFICATE_SERVICE_ADDRESS, message, options, ar -> {

			if (ar.succeeded()) {

				Message<JsonObject> m = ar.result();

				future.complete(m.body());

			} else {
				future.fail(ar.cause());
			}

		});

	}

}
