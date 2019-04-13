package io.edge.iot;

import io.edge.iot.verticle.LauncherVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Launcher {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Launcher.class);

	public static void main(String[] args) {

		Vertx vertx = Vertx.vertx();
		
		vertx.deployVerticle(LauncherVerticle.class.getName(), startResult -> {
			
			if( startResult.succeeded()) {
				LOGGER.info("Edge IoT Started");
			} else {
				LOGGER.error("Cannot start Launcher Verticle", startResult.cause());
				System.exit(0);
			}
			
		});
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			
			@Override
			public void run() {
				vertx.close();
				LOGGER.info("Edge IoT Stoped");
			}
			
		});

	}

}
