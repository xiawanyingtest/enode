package com.enodeframework.commanding.impl;

import com.enodeframework.commanding.ICommandProcessor;
import com.enodeframework.commanding.IProcessingCommandHandler;
import com.enodeframework.commanding.ProcessingCommand;
import com.enodeframework.commanding.ProcessingCommandMailbox;
import com.enodeframework.common.scheduling.IScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class DefaultCommandProcessor implements ICommandProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DefaultCommandProcessor.class);

    private final ConcurrentMap<String, ProcessingCommandMailbox> mailboxDict;
    private final int timeoutSeconds;
    private final String taskName;
    private final int commandMailBoxPersistenceMaxBatchSize = 1000;
    private final int scanExpiredAggregateIntervalMilliseconds = 5000;
    private final int eventMailBoxPersistenceMaxBatchSize = 1000;
    private final int aggregateRootMaxInactiveSeconds = 3600 * 24 * 3;
    @Autowired
    private IProcessingCommandHandler handler;
    @Autowired
    private IScheduleService scheduleService;

    public DefaultCommandProcessor() {
        this.mailboxDict = new ConcurrentHashMap<>();
        this.timeoutSeconds = aggregateRootMaxInactiveSeconds;
        this.taskName = "CleanInactiveAggregates" + System.nanoTime() + new Random().nextInt(10000);
    }

    @Override
    public void process(ProcessingCommand processingCommand) {
        String aggregateRootId = processingCommand.getMessage().getAggregateRootId();
        if (aggregateRootId == null || "".equals(aggregateRootId.trim())) {
            throw new IllegalArgumentException("aggregateRootId of command cannot be null or empty, commandId:" + processingCommand.getMessage().id());
        }

        ProcessingCommandMailbox mailbox = mailboxDict.computeIfAbsent(aggregateRootId, x -> new ProcessingCommandMailbox(x, handler));
        mailbox.enqueueMessage(processingCommand);
    }

    @Override
    public void start() {
        scheduleService.startTask(taskName, this::cleanInactiveMailbox,
                scanExpiredAggregateIntervalMilliseconds,
                scanExpiredAggregateIntervalMilliseconds);
    }

    @Override
    public void stop() {
        scheduleService.stopTask(taskName);
    }

    private void cleanInactiveMailbox() {
        List<Map.Entry<String, ProcessingCommandMailbox>> inactiveList = mailboxDict.entrySet().stream().filter(entry ->
                entry.getValue().isInactive(timeoutSeconds) && !entry.getValue().isRunning()
        ).collect(Collectors.toList());

        inactiveList.forEach(entry -> {
            if (mailboxDict.remove(entry.getKey()) != null) {
                logger.info("Removed inactive command mailbox, aggregateRootId: {}", entry.getKey());
            }
        });
    }
}
