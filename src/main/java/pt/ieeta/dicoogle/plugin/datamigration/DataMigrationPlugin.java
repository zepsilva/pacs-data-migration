package pt.ieeta.dicoogle.plugin.datamigration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataMigrationPlugin {

    private static final Logger logger = LoggerFactory.getLogger(DataMigrationPlugin.class);

    public DataMigrationPlugin() {

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        OperationsTest operationsTest = new OperationsTest();
        executor.schedule(operationsTest, 5, TimeUnit.SECONDS);
//        logger.info("AET: {}", platform.getSettings().getDicomServicesSettings().getAETitle());
        logger.info("Init finished!");

    }

}