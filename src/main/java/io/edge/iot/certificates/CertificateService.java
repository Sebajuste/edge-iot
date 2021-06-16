package io.edge.iot.certificates;

import io.edge.iot.certificates.impl.CertificateServiceImpl;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public interface CertificateService {

	static CertificateService create(Vertx vertx) {
		return new CertificateServiceImpl(vertx);
	}

	Future<JsonObject> getServerCertificate(String registry, String certName, boolean loadKeys);

	Future<JsonObject> createCertificate(String registry, String certName, boolean CA);

	Future<JsonObject> createSignedCertificate(String registry, String certName, String caCertName);

}
