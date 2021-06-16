package io.edge.iot.service.remote.impl;

import io.edge.iot.dao.ShadowDao;
import io.edge.iot.service.remote.ShadowServiceAPI;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.api.service.ServiceRequest;
import io.vertx.ext.web.api.service.ServiceResponse;

public class ShadowServiceAPIImpl implements ShadowServiceAPI {

	private static final Logger LOGGER = LoggerFactory.getLogger(ShadowServiceAPIImpl.class);

	private final ShadowDao shadowDao;

	public ShadowServiceAPIImpl(ShadowDao shadowDao) {
		this.shadowDao = shadowDao;
	}

	@Override
	public void getShadow(String registry, String thingName, ServiceRequest context, Handler<AsyncResult<ServiceResponse>> resultHandler) {

		shadowDao.get(registry, thingName).onComplete(ar -> {

			if (ar.succeeded()) {

				resultHandler.handle(Future.succeededFuture(ServiceResponse.completedWithJson(ar.result())));

			} else {
				LOGGER.error("Cannot load shadow", ar.cause());

				ServiceResponse response = new ServiceResponse() //
						.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());

				resultHandler.handle(Future.succeededFuture(response));

			}

		});

	}

}
