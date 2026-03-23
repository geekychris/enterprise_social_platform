package com.social.app.config;

import com.social.app.persistence.repository.*;
import com.social.core.id.GlobalId;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GlobalIdConfig {

    private static final Logger log = LoggerFactory.getLogger(GlobalIdConfig.class);

    @Bean
    public GlobalIdGenerator globalIdGenerator() {
        return new GlobalIdGenerator();
    }

    @Bean
    public CommandLineRunner initializeGlobalIdCounters(
            GlobalIdGenerator generator,
            UserRepository userRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            TeamRepository teamRepository,
            GroupRepository groupRepository,
            PageRepository pageRepository,
            ProjectRepository projectRepository,
            AttachmentRepository attachmentRepository,
            ReactionRepository reactionRepository,
            MessageRepository messageRepository) {

        return args -> {
            initCounter(generator, ObjectType.USER, userRepository.findMaxId());
            initCounter(generator, ObjectType.POST, postRepository.findMaxId());
            initCounter(generator, ObjectType.COMMENT, commentRepository.findMaxId());
            initCounter(generator, ObjectType.TEAM, teamRepository.findMaxId());
            initCounter(generator, ObjectType.GROUP, groupRepository.findMaxId());
            initCounter(generator, ObjectType.PAGE, pageRepository.findMaxId());
            initCounter(generator, ObjectType.PROJECT, projectRepository.findMaxId());
            initCounter(generator, ObjectType.ATTACHMENT, attachmentRepository.findMaxId());
            initCounter(generator, ObjectType.REACTION, reactionRepository.findMaxId());
            initCounter(generator, ObjectType.MESSAGE, messageRepository.findMaxId());
            log.info("GlobalId counters initialized from database max values");
        };
    }

    private void initCounter(GlobalIdGenerator generator, ObjectType type, Long maxRawId) {
        if (maxRawId != null) {
            long sequence = new GlobalId(maxRawId).sequence();
            generator.initCounter(type, sequence);
            log.debug("Initialized {} counter to sequence {}", type, sequence);
        }
    }
}
