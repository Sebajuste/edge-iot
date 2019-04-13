package io.edge.iot.service.remote.impl;

import io.edge.iot.dao.ShadowDao;
import io.edge.iot.service.remote.ShadowServiceAPI;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.api.OperationRequest;
import io.vertx.ext.web.api.OperationResponse;

public class ShadowServiceAPIImpl implements ShadowServiceAPI {

	private static final Logger LOGGER = LoggerFactory.getLogger(ShadowServiceAPIImpl.class);

	private final ShadowDao shadowDao;

	public ShadowServiceAPIImpl(ShadowDao shadowDao) {
		this.shadowDao = shadowDao;
	}

	@Override
	public void getShadow(String registry, String thingName, OperationRequest context, Handler<AsyncResult<OperationResponse>> resultHandler) {

		shadowDao.get(registry, thingName, ar -> {

			if (ar.succeeded()) {

				resultHandler.handle(Future.succeededFuture(OperationResponse.completedWithJson(ar.result())));

			} else {
				LOGGER.error("Cannot load shadow", ar.cause());

				OperationResponse response = new OperationResponse() //
						.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());

				resultHandler.handle(Future.succeededFuture(response));

			}

		});

	}

}
