package io.edge.iot.service.remote.impl;

import java.util.List;

import io.edge.iot.dao.ThingRegistryDao;
import io.edge.iot.service.remote.RegistryService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class RegistryServiceImpl implements RegistryService {

	private final ThingRegistryDao registryDao;

	public RegistryServiceImpl(ThingRegistryDao registryDao) {
		super();
		this.registryDao = registryDao;
	}

	@Override
	public void createThing(String registry, String thingName, JsonObject metadata, Handler<AsyncResult<Boolean>> resultHandler) {

		this.registryDao.addOrUpdateThing(registry, thingName, metadata, resultHandler);

	}

	@Override
	public void getAll(String registry, Handler<AsyncResult<List<JsonObject>>> resultHandler) {
		
		this.registryDao.getAll(registry, resultHandler);
		
	}

	@Override
	public void findMetadata(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler) {

		this.registryDao.findByName(registry, thingName, resultHandler);

	}

}
