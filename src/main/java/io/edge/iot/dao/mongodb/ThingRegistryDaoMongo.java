package io.edge.iot.dao.mongodb;

import java.util.List;

import io.edge.iot.dao.ThingRegistryDao;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;

public class ThingRegistryDaoMongo implements ThingRegistryDao {

	private static final String REGISTRY_COLLECTION = "registry";

	private final MongoClient mongoClient;

	private final CircuitBreaker getAllCB;

	private final CircuitBreaker findByNameCB;

	private final CircuitBreaker addOrUpdateThingCB;

	public ThingRegistryDaoMongo(Vertx vertx, MongoClient mongoClient) {
		super();
		this.mongoClient = mongoClient;

		this.getAllCB = CircuitBreaker.create("edge.iot.registry-dao.getAll", vertx);
		this.findByNameCB = CircuitBreaker.create("edge.iot.registry-dao.findByName", vertx);
		this.addOrUpdateThingCB = CircuitBreaker.create("edge.iot.registry-dao.addOrUpdateThing", vertx);
	}

	@Override
	public void getAll(String registry, Handler<AsyncResult<List<JsonObject>>> resultHandler) {

		JsonObject query = new JsonObject().put("registry", registry);
		
		System.out.println("get All");

		this.getAllCB.<List<JsonObject>>execute(future -> {
			mongoClient.find(ThingRegistryDaoMongo.REGISTRY_COLLECTION, query, future);
		}).setHandler(ar -> {
			
			if( ar.succeeded()) {
				System.out.println("ok");
				resultHandler.handle( Future.succeededFuture(ar.result()) );
			} else {
				System.err.println("error");
				resultHandler.handle(Future.failedFuture(ar.cause()));
			}
			
		});

	}

	@Override
	public void findByName(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler) {

		JsonObject query = new JsonObject().put("registry", registry).put("thingName", thingName);
		
		JsonObject fields = new JsonObject().put("metadata", true);

		this.findByNameCB.<JsonObject>execute(future -> {
			mongoClient.findOne(ThingRegistryDaoMongo.REGISTRY_COLLECTION, query, fields, future);
		}).setHandler(resultHandler);

	}

	@Override
	public void addOrUpdateThing(String registry, String thingName, JsonObject metadata,
			Handler<AsyncResult<Boolean>> resultHandler) {

		JsonObject query = new JsonObject().put("registry", registry).put("thingName", thingName);

		UpdateOptions options = new UpdateOptions();
		options.setUpsert(true);
		
		JsonObject thingItem = new JsonObject()//
				// .put("registry", registry)//
				// .put("thingName", thingName)//
				.put("metadata", metadata);

		this.addOrUpdateThingCB.<Boolean>execute(future -> {
			mongoClient.updateCollectionWithOptions(ThingRegistryDaoMongo.REGISTRY_COLLECTION, query,
					new JsonObject().put("$set", thingItem), options, ar -> {

						if (ar.succeeded()) {
							future.complete(ar.result().getDocModified() > 0);
						} else {
							future.fail(ar.cause());
						}

					});
		}).setHandler(resultHandler);

	}

}
