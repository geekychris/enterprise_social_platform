package com.social.datagen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DataGenApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataGenApplication.class);

    private final DataGenOrchestrator orchestrator;

    public DataGenApplication(DataGenOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public static void main(String[] args) {
        SpringApplication.run(DataGenApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        DataGenConfig.Mode mode = DataGenConfig.Mode.HUNDREDS;
        boolean aoeeSync = false;

        for (String arg : args) {
            if (arg.startsWith("--mode=")) {
                String modeStr = arg.substring("--mode=".length()).toUpperCase();
                mode = DataGenConfig.Mode.valueOf(modeStr);
            } else if (arg.startsWith("--aoee-sync=")) {
                aoeeSync = Boolean.parseBoolean(arg.substring("--aoee-sync=".length()));
            }
        }

        log.info("Starting data generation: mode={}, aoee-sync={}", mode, aoeeSync);
        long start = System.currentTimeMillis();

        orchestrator.generate(mode, aoeeSync);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Data generation completed in {} ms ({} seconds)", elapsed, elapsed / 1000);
    }
}
