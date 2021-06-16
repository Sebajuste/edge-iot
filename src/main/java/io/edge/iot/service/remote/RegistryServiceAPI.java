package io.edge.iot.service.remote;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.api.service.ServiceRequest;
import io.vertx.ext.web.api.service.ServiceResponse;
import io.vertx.ext.web.api.service.WebApiServiceGen;

@WebApiServiceGen
public interface RegistryServiceAPI {
	
	static String ADDRESS = "edge.iot.registry-service-api";

	void getAll(String registry, ServiceRequest context, Handler<AsyncResult<ServiceResponse>> resultHandler);

	void findMetadata(String registry, String thingName, ServiceRequest context, Handler<AsyncResult<ServiceResponse>> resultHandler);

}
