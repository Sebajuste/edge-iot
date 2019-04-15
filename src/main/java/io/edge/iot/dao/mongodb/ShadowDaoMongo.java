package io.edge.iot.dao.mongodb;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.edge.iot.dao.ShadowDao;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;

public class ShadowDaoMongo implements ShadowDao {

	private static final String SHADOW_COLLECTION = "shadows";

	private final MongoClient mongoClient;

	private final CircuitBreaker getShadowCB;
	
	private final CircuitBreaker getReportedCB;
	
	private final CircuitBreaker saveReportedCB;
	
	private final CircuitBreaker saveDesiredCB;

	public ShadowDaoMongo(final Vertx vertx, final MongoClient mongoClient) {
		super();
		this.mongoClient = mongoClient;

		CircuitBreakerOptions cbOptions = new CircuitBreakerOptions()//
				.setMaxFailures(5)//
				.setTimeout(2000)//
				.setFallbackOnFailure(false)//
				.setResetTimeout(60000L);

		this.getShadowCB = CircuitBreaker.create("edge.iot.shadow-dao.get", vertx, cbOptions);
		
		this.getReportedCB = CircuitBreaker.create("edge.iot.shadow-dao.getReported", vertx, cbOptions);
		
		this.saveReportedCB = CircuitBreaker.create("edge.iot.shadow-dao.saveReported", vertx, cbOptions);
		
		this.saveDesiredCB = CircuitBreaker.create("edge.iot.shadow-dao.saveDesired", vertx, cbOptions);

	}

	private static JsonObject sanitizeDate(JsonObject shadow) {
		shadow.remove("_id");

		if (shadow.containsKey("datetime")) {
			shadow.put("datetime", shadow.getJsonObject("datetime").getString("$date"));
		}

		if (shadow.containsKey("metadata")) {

			JsonObject metadata = shadow.getJsonObject("metadata");

			if (metadata.containsKey("reported")) {
				JsonObject reported = metadata.getJsonObject("reported");

				for (String name : reported.fieldNames()) {
					if (reported.getJsonObject(name).containsKey("datetime")) {
						reported.getJsonObject(name).put("datetime", reported.getJsonObject(name).getJsonObject("datetime").getString("$date"));
					}
				}

			}

			if (metadata.containsKey("desired")) {
				JsonObject desired = metadata.getJsonObject("desired");

				for (String name : desired.fieldNames()) {
					if (desired.getJsonObject(name).containsKey("datetime")) {
						desired.getJsonObject(name).put("datetime", desired.getJsonObject(name).getJsonObject("datetime").getString("$date"));
					}
				}

			}

		}
		return shadow;
	}

	@Override
	public void get(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler) {

		JsonObject query = new JsonObject().put("registry", registry).put("thingName", thingName);

		this.getShadowCB.<JsonObject> execute(future -> {

			mongoClient.findOne(ShadowDaoMongo.SHADOW_COLLECTION, query, null, result -> {

				if (result.succeeded()) {
					JsonObject json = result.result();
					future.complete(json != null ? ShadowDaoMongo.sanitizeDate(json) : new JsonObject());
				} else {
					future.fail(result.cause());
				}

			});

		}).setHandler(resultHandler);

	}

	@Override
	public void getReported(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler) {

		JsonObject query = new JsonObject().put("registry", registry).put("thingName", thingName);
		
		JsonObject fields = new JsonObject().put("state.reported", 1);
		
		this.getReportedCB.<JsonObject>execute(future -> {
			
			this.mongoClient.findOne(ShadowDaoMongo.SHADOW_COLLECTION, query, fields, ar -> {

				if (ar.succeeded()) {
					JsonObject json = ar.result();

					if (json != null) {
						ShadowDaoMongo.sanitizeDate(json);

						if (json.containsKey("state")) {
							JsonObject state = json.getJsonObject("state");
							if (state.containsKey("reported")) {
								json = state.getJsonObject("reported");
							}
						}
					}

					future.complete(json);
				} else {
					future.fail(ar.cause());
				}

			});
			
		}).setHandler(resultHandler);
		
		
	}

	@Override
	public void getDesired(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler) {

		mongoClient.findOne(ShadowDaoMongo.SHADOW_COLLECTION, new JsonObject().put("registry", registry).put("thingName", thingName), new JsonObject().put("state.desired", 1), result -> {

			if (result.succeeded()) {
				JsonObject json = result.result();

				if (json != null) {

					ShadowDaoMongo.sanitizeDate(json);

					if (json.containsKey("state")) {
						JsonObject state = json.getJsonObject("state");
						if (state.containsKey("desired")) {
							json = state.getJsonObject("desired");
						}
					}
				}

				resultHandler.handle(Future.succeededFuture(json));
			} else {
				resultHandler.handle(Future.failedFuture(result.cause()));
			}

		});

	}

	@Override
	public void saveReported(String registry, String thingName, final JsonObject shadow, Handler<AsyncResult<Boolean>> resultHandler) {

		JsonObject reported = shadow.getJsonObject("state").getJsonObject("reported");
		
		JsonObject metadata = shadow.getJsonObject("metadata", new JsonObject().put("reported", new JsonObject())).getJsonObject("reported", new JsonObject());
		
		if (reported.isEmpty()) {
			resultHandler.handle(Future.succeededFuture(true));
			return;
		}

		JsonObject query = new JsonObject().put("registry", registry).put("thingName", thingName);

		JsonObject update = new JsonObject();

		long shadowTimestamp = shadow.containsKey("timestamp") ? shadow.getLong("timestamp") : System.currentTimeMillis();
		
		long maxtimestamp = -1L;

		// for (int i = 0; i < measures.size(); ++i) {
		for (String name : reported.fieldNames()) {

			// JsonObject measure = measures.getJsonObject(i);
			
			// String name = measure.getString("name");
			
			Object value = reported.getValue(name);
			
			if( value != null) {
				
				long timestamp = metadata.containsKey(name) ? ( metadata.getJsonObject(name).containsKey("timestamp") ? metadata.getJsonObject(name).getLong("timestamp") : shadowTimestamp ) : shadowTimestamp;
	
				if (timestamp > maxtimestamp) {
					maxtimestamp = timestamp;
				}
	
				update.put("state.reported." + name, value);
				update.put("metadata.reported." + name + ".timestamp", timestamp);
	
				Instant instant = Instant.ofEpochMilli(timestamp);
				update.put("metadata.reported." + name + ".datetime", new JsonObject().put("$date", ZonedDateTime.ofInstant(instant, ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT)));
			}
		}

		if (maxtimestamp > 0) {
			update.put("timestamp", maxtimestamp);

			Instant instant = Instant.ofEpochMilli(maxtimestamp);
			update.put("datetime", new JsonObject().put("$date", ZonedDateTime.ofInstant(instant, ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT)));
		}

		UpdateOptions options = new UpdateOptions();

		options.setUpsert(true);
		
		
		saveReportedCB.<Boolean>execute(future -> {
			
			mongoClient.updateCollectionWithOptions(ShadowDaoMongo.SHADOW_COLLECTION, query, new JsonObject().put("$set", update), options, result -> {

				if (result.succeeded()) {

					
					future.complete(result.result().getDocModified() > 0);
					
					// resultHandler.handle(Future.succeededFuture(result.result().getDocModified() > 0));

				} else {
					// resultHandler.handle(Future.failedFuture(result.cause()));
					future.fail(result.cause());
				}

			});
			
		}).setHandler(resultHandler);
		
		/*
		mongoClient.updateCollectionWithOptions(MongoShadowDao.SHADOW_COLLECTION, query, new JsonObject().put("$set", update), options, result -> {

			if (result.succeeded()) {

				resultHandler.handle(Future.succeededFuture(result.result().getDocModified() > 0));

			} else {
				resultHandler.handle(Future.failedFuture(result.cause()));
			}

		});
		*/

	}

	@Override
	public void saveDesired(String registry, String thingName, final JsonObject shadow, Handler<AsyncResult<Boolean>> resultHandler) {

		JsonObject desired = shadow.getJsonObject("state").getJsonObject("desired");
		
		JsonObject metadata = shadow.getJsonObject("metadata", new JsonObject().put("desired", new JsonObject())).getJsonObject("desired", new JsonObject());
		
		if (desired.isEmpty()) {
			resultHandler.handle(Future.succeededFuture(true));
			return;
		}

		long shadowTimestamp = shadow.containsKey("timestamp") ? shadow.getLong("timestamp") : System.currentTimeMillis();

		JsonObject filter = new JsonObject().put("registry", registry).put("thingName", thingName);

		JsonObject update = new JsonObject();

		// for (int i = 0; i < desired.size(); ++i) {
		for (String name : desired.fieldNames()) {

			// JsonObject measure = desired.getJsonObject(i);

			// String name = measure.getString("name");
			
			Object value = desired.getValue(name);
			
			if( value != null) {

				long timestamp = metadata.containsKey(name) ? ( metadata.getJsonObject(name).containsKey("timestamp") ? metadata.getJsonObject(name).getLong("timestamp") : shadowTimestamp ) : shadowTimestamp;
				
				update.put("state.desired." + name,value);
				update.put("metadata.desired." + name + ".timestamp", timestamp);
				Instant instant = Instant.ofEpochMilli(timestamp);
				update.put("metadata.desired." + name + ".datetime", new JsonObject().put("$date", ZonedDateTime.ofInstant(instant, ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT)));
			}
		}

		UpdateOptions options = new UpdateOptions();
		options.setUpsert(true);

		this.saveDesiredCB.<Boolean>execute(future -> {
			
			mongoClient.updateCollectionWithOptions(ShadowDaoMongo.SHADOW_COLLECTION, filter, new JsonObject().put("$set", update), options, ar -> {

				if (ar.succeeded()) {
					future.complete( ar.result().getDocModified() > 0 );
				} else {
					future.fail(ar.cause());
				}

			});
			
		}).setHandler(resultHandler);

	}

	@Override
	public void deleteReported(String registry, String thingName, final Iterable<String> keys, Handler<AsyncResult<Boolean>> resultHandler) {

		JsonObject unset = new JsonObject();

		for (String key : keys) {
			unset.put("state.reported." + key, "");
			unset.put("metadata.reported." + key, "");
		}
		
		if (unset.isEmpty()) {
			resultHandler.handle(Future.succeededFuture(true));
			return;
		}

		JsonObject query = new JsonObject().put("registry", registry).put("thingName", thingName);

		mongoClient.updateCollection(ShadowDaoMongo.SHADOW_COLLECTION, query, new JsonObject().put("$unset", unset), result -> {
			if (result.succeeded()) {
				resultHandler.handle(Future.succeededFuture(result.result().getDocModified() > 0));
			} else {
				resultHandler.handle(Future.failedFuture(result.cause()));
			}
		});

	}

	@Override
	public void deleteDesired(String registry, String thingName, final Iterable<String> desired, Handler<AsyncResult<Boolean>> resultHandler) {

		JsonObject unset = new JsonObject();

		for (String key : desired) {
			unset.put("state.desired." + key, "");
			unset.put("metadata.desired." + key, "");
		}
		
		if (unset.isEmpty()) {
			resultHandler.handle(Future.succeededFuture(true));
			return;
		}

		JsonObject query = new JsonObject().put("registry", registry).put("thingName", thingName);

		mongoClient.updateCollection(ShadowDaoMongo.SHADOW_COLLECTION, query, new JsonObject().put("$unset", unset), result -> {
			if (result.succeeded()) {
				resultHandler.handle(Future.succeededFuture(result.result().getDocModified() > 0));

				// TODO : supprimer le cache si suppression réussie

			} else {
				resultHandler.handle(Future.failedFuture(result.cause()));
			}

		});

	}

	@Override
	public void remove(String registry, String thingName, List<String> valueNameList, Handler<AsyncResult<Boolean>> resultHandler) {

		final JsonObject unset = new JsonObject();

		for (String value : valueNameList) {
			unset.put("state.reported." + value, "");
			unset.put("state.desired." + value, "");
			unset.put("metadata.reported." + value, "");
			unset.put("metadata.desired." + value, "");
		}

		JsonObject query = new JsonObject().put("registry", registry).put("thingName", thingName);

		mongoClient.updateCollection(ShadowDaoMongo.SHADOW_COLLECTION, query, new JsonObject().put("$unset", unset), result -> {

			if (result.succeeded()) {
				resultHandler.handle(Future.succeededFuture(result.result().getDocModified() > 0));
			} else {
				resultHandler.handle(Future.failedFuture(result.cause()));
			}

		});

	}

	@Override
	public void delete(String registry, String thingName, Handler<AsyncResult<Boolean>> resultHandler) {

		JsonObject query = new JsonObject().put("registry", registry).put("thingName", thingName);

		mongoClient.removeDocument(ShadowDaoMongo.SHADOW_COLLECTION, query, result -> {
			if (result.succeeded()) {
				resultHandler.handle(Future.succeededFuture(result.result().getRemovedCount() > 0));
			} else {
				resultHandler.handle(Future.failedFuture(result.cause()));
			}
		});

	}

}
