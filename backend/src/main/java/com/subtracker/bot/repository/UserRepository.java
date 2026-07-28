package com.subtracker.bot.repository;

import com.subtracker.bot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Look up a user by their Telegram chat ID — this is how every incoming bot message resolves "who is this?"
    Optional<User> findByTelegramChatId(Long telegramChatId);

    // Quick existence check without loading the full entity — used to decide whether to auto-create a User on first message
    boolean existsByTelegramChatId(Long telegramChatId);
}