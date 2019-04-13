package io.edge.iot.service.remote.impl;

import io.edge.iot.dao.ThingRegistryDao;
import io.edge.iot.service.remote.RegistryServiceAPI;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.api.OperationRequest;
import io.vertx.ext.web.api.OperationResponse;

public class RegistryServiceAPIImpl implements RegistryServiceAPI {

	private final ThingRegistryDao registryDao;

	public RegistryServiceAPIImpl(ThingRegistryDao registryDao) {
		super();
		this.registryDao = registryDao;
	}

	@Override
	public void getAll(String registry, OperationRequest context, Handler<AsyncResult<OperationResponse>> resultHandler) {

		this.registryDao.getAll(registry, ar -> {

			// OperationResponse response = new OperationResponse();

			if (ar.succeeded()) {
				OperationResponse response = OperationResponse.completedWithJson(new JsonArray(ar.result()));
				resultHandler.handle(Future.succeededFuture(response));
			} else {
				OperationResponse response = new OperationResponse().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
				resultHandler.handle(Future.succeededFuture(response));
				// OperationResponse
				// response.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
			}

			//

		});

	}

	@Override
	public void findMetadata(String registry, String thingName, OperationRequest context, Handler<AsyncResult<OperationResponse>> resultHandler) {

		this.registryDao.findByName(registry, thingName, ar -> {

			if (ar.succeeded()) {

				JsonObject metadata = ar.result();

				if (metadata != null) {
					OperationResponse response = OperationResponse.completedWithJson(metadata);
					resultHandler.handle(Future.succeededFuture(response));
				} else {
					OperationResponse response = new OperationResponse().setStatusCode(HttpResponseStatus.NO_CONTENT.code());
					resultHandler.handle(Future.succeededFuture(response));
				}

			} else {
				OperationResponse response = new OperationResponse().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
				resultHandler.handle(Future.succeededFuture(response));
			}

		});

	}

}
