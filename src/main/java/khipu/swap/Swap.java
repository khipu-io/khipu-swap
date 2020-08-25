package khipu.swap;

import static org.apache.logging.log4j.LogManager.getLogger;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import java.util.function.BiFunction;
import khipu.ProtocolContextFactory;
import khipu.RunnerBuilder;
import khipu.swap.chain.SwapBlockchain;
import khipu.chainimport.JsonBlockImporter;
import khipu.chainimport.RlpBlockImporter;
import khipu.cli.BesuCommand;
import khipu.controller.BesuController;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.chainexport.RlpBlockExporter;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import picocli.CommandLine.RunLast;

public final class Swap {
  private static final int SUCCESS_EXIT_CODE = 0;
  private static final int ERROR_EXIT_CODE = 1;

  public static void main(final String... args) {
    final Logger logger = setupLogging();

    final ProtocolContextFactory protocolContextFactory =
        (StorageProvider storageProvider,
            GenesisState genesisState,
            ProtocolSchedule protocolSchedule,
            MetricsSystem metricsSystem,
            BiFunction<Blockchain, WorldStateArchive, Object> consensusContextFactory) -> {
          final BlockchainStorage blockchainStorage =
              storageProvider.createBlockchainStorage(protocolSchedule);
          final WorldStateStorage worldStateStorage = storageProvider.createWorldStateStorage();
          final WorldStatePreimageStorage preimageStorage =
              storageProvider.createWorldStatePreimageStorage();

          final MutableBlockchain blockchain =
              SwapBlockchain.createMutable(
                  genesisState.getBlock(), blockchainStorage, metricsSystem);

          final WorldStateArchive worldStateArchive =
              new WorldStateArchive(worldStateStorage, preimageStorage);
          genesisState.writeStateTo(worldStateArchive.getMutable());

          return new ProtocolContext(
              blockchain,
              worldStateArchive,
              consensusContextFactory.apply(blockchain, worldStateArchive));
        };

    final BesuCommand besuCommand =
        new BesuCommand(
            logger,
            protocolContextFactory,
            RlpBlockImporter::new,
            JsonBlockImporter::new,
            RlpBlockExporter::new,
            new RunnerBuilder(),
            new BesuController.Builder(),
            new BesuPluginContextImpl(),
            System.getenv());

    besuCommand.parse(
        new RunLast().andExit(SUCCESS_EXIT_CODE),
        besuCommand.exceptionHandler().andExit(ERROR_EXIT_CODE),
        System.in,
        args);
  }

  private static Logger setupLogging() {
    InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
    try {
      System.setProperty(
          "vertx.logger-delegate-factory-class-name",
          "io.vertx.core.logging.Log4j2LogDelegateFactory");
    } catch (SecurityException e) {
      System.out.println(
          "Could not set logging system property as the security manager prevented it:"
              + e.getMessage());
    }

    final Logger logger = getLogger();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) ->
            logger.error("Uncaught exception in thread \"" + thread.getName() + "\"", error));
    Thread.currentThread()
        .setUncaughtExceptionHandler(
            (thread, error) ->
                logger.error("Uncaught exception in thread \"" + thread.getName() + "\"", error));
    return logger;
  }
}
