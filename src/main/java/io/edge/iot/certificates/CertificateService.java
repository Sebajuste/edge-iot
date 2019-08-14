package io.edge.iot.certificates;

import io.edge.iot.certificates.impl.CertificateServiceImpl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public interface CertificateService {

	static CertificateService create(Vertx vertx) {
		return new CertificateServiceImpl(vertx);
	}

	void getServerCertificate(String registry, String certName, boolean loadKeys, Handler<AsyncResult<JsonObject>> resultHandler);

	void createCertificate(String registry, String certName, boolean cA, Handler<AsyncResult<JsonObject>> resultHandler);

	void createSignedCertificate(String registry, String certName, String caCertName, Handler<AsyncResult<JsonObject>> resultHandler);

}
