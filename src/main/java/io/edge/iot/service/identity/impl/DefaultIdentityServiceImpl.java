package io.edge.iot.service.identity.impl;

import io.edge.iot.service.identity.IdentityService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttAuth;

public class DefaultIdentityServiceImpl implements IdentityService {

	@Override
	public void findIdentity(String clientId, MqttAuth auth, Handler<AsyncResult<JsonObject>> resultHandler) {

		String registry = "default";

		if (auth != null && auth.getUsername() != null) {

			String splits[] = auth.getUsername().split("@");
			if (splits.length > 1) {
				registry = splits[0];
			}
		} else {

			String splits[] = clientId.split("@");

			if (splits.length > 1) {
				registry = splits[0];
			}

		}

		resultHandler.handle(Future.succeededFuture(new JsonObject().put("registry", registry)));

	}

}
